package com.project.api_rate_limiter.filter;

import java.io.IOException;
import java.lang.reflect.Method;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.api_rate_limiter.annotation.RateLimit;
import com.project.api_rate_limiter.annotation.RateLimitType;
import com.project.api_rate_limiter.config.RateLimitConfig;
import com.project.api_rate_limiter.exception.RateLimitExceededException;
import com.project.api_rate_limiter.exception.UnauthorizedException;
import com.project.api_rate_limiter.model.ErrorResponse;
import com.project.api_rate_limiter.service.IpFilterService;
import com.project.api_rate_limiter.service.RateLimiterService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100) // Run after Spring Security filters
@ConditionalOnProperty(name = "rate-limiter.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;
    private final RequestMappingHandlerMapping handlerMapping;
    private final IpFilterService ipFilterService;
    private final RateLimitConfig config;

    public RateLimitFilter(RateLimiterService rateLimiterService, 
                          ObjectMapper objectMapper, 
                          RequestMappingHandlerMapping handlerMapping,
                          IpFilterService ipFilterService,
                          RateLimitConfig config) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
        this.handlerMapping = handlerMapping;
        this.ipFilterService = ipFilterService;
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String clientIp = getClientIp(request);
        String endpoint = normalizeEndpoint(request.getRequestURI());
        
        // Check if IP filtering is enabled
        if (config.isEnableIpFiltering()) {
            // Check if the IP is blacklisted
            if (ipFilterService.isBlacklisted(clientIp)) {
                log.warn("Request from blacklisted IP {} blocked for endpoint {}", clientIp, endpoint);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json");
                
                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        "Forbidden",
                        "Access denied: Your IP address is blacklisted",
                        request.getRequestURI()
                );
                
                objectMapper.writeValue(response.getOutputStream(), errorResponse);
                return;
            }
            
            // Check if the IP is whitelisted (bypass rate limiting)
            if (ipFilterService.isWhitelisted(clientIp)) {
                log.debug("Request from whitelisted IP {} bypassing rate limiting for endpoint {}", clientIp, endpoint);
                filterChain.doFilter(request, response);
                return;
            }
        }
        
        try {
            // Try to find the handler method to check for annotations
            HandlerMethod handlerMethod = getHandlerMethod(request);
            if (handlerMethod != null) {
                processWithAnnotation(request, response, filterChain, clientIp, endpoint, handlerMethod);
            } else {
                // No handler method found, use default IP-based rate limiting
                rateLimiterService.allowRequest(clientIp, endpoint);
                filterChain.doFilter(request, response);
            }
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for client {} on endpoint {}: {}", 
                    clientIp, endpoint, e.getMessage());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("X-RateLimit-Limit", String.valueOf(e.getLimit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(e.getRemaining()));
            response.setHeader("Retry-After", String.valueOf(e.getWaitTimeSeconds()));

            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too Many Requests",
                    e.getMessage(),
                    request.getRequestURI()
            );

            objectMapper.writeValue(response.getOutputStream(), errorResponse);
        } catch (UnauthorizedException e) {
            log.warn("Unauthorized request for client {} on endpoint {}: {}", 
                    clientIp, endpoint, e.getMessage());

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");

            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    e.getMessage(),
                    request.getRequestURI()
            );

            objectMapper.writeValue(response.getOutputStream(), errorResponse);
        } catch (Exception e) {
            log.error("Error in rate limit filter", e);
            filterChain.doFilter(request, response);
        }
    }
    
    private void processWithAnnotation(HttpServletRequest request, HttpServletResponse response, 
                                      FilterChain filterChain, String clientIp, String endpoint, 
                                      HandlerMethod handlerMethod) 
            throws ServletException, IOException, RateLimitExceededException {
        
        Method method = handlerMethod.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        
        // If no method annotation, check class annotation
        if (rateLimit == null) {
            rateLimit = method.getDeclaringClass().getAnnotation(RateLimit.class);
        }
        
        if (rateLimit != null) {
            // Check if the HTTP method is allowed for rate limiting
            if (!rateLimiterService.isMethodAllowed(request, rateLimit.methods())) {
                // Method not specified in the annotation, skip rate limiting
                filterChain.doFilter(request, response);
                return;
            }
            
            // Apply rate limiting based on the annotation type
            RateLimitType type = rateLimit.type();
            int limit = rateLimit.limit();
            int timeWindow = rateLimit.timeWindowSeconds();
            
            // If using DDoS protection
            if (rateLimit.ddosProtection() && type == RateLimitType.IP_BASED) {
                // Set DDoS protection parameters from annotation
                // Implementation detail: these settings would be applied to the DdosProtectionService
            }
            
            rateLimiterService.allowRequestWithType(clientIp, endpoint, limit, timeWindow, type, request);
        } else {
            // No annotation found, use default IP-based rate limiting
            rateLimiterService.allowRequest(clientIp, endpoint);
        }
        
        filterChain.doFilter(request, response);
    }
    
    // Get the HandlerMethod for the current request to check for annotations
    private HandlerMethod getHandlerMethod(HttpServletRequest request) {
        try {
            HandlerExecutionChain handlerChain = handlerMapping.getHandler(request);
            if (handlerChain != null && handlerChain.getHandler() instanceof HandlerMethod) {
                return (HandlerMethod) handlerChain.getHandler();
            }
        } catch (Exception e) {
            log.debug("Could not get handler method for request", e);
        }
        return null;
    }
    
    // Get the client IP address from the request
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String normalizeEndpoint(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "";
        }
        // Remove leading slash and replace subsequent slashes with hyphens
        return uri.startsWith("/") ? uri.substring(1).replace('/', '-') : uri.replace('/', '-');
    }
}