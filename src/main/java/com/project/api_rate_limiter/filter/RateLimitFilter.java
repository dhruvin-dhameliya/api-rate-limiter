package com.project.api_rate_limiter.filter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.api_rate_limiter.exception.RateLimitExceededException;
import com.project.api_rate_limiter.model.ErrorResponse;
import com.project.api_rate_limiter.service.RateLimiterService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "rate-limiter.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String clientIp = getClientIp(request);
        String endpoint = request.getRequestURI();
        
        try {
            rateLimiterService.allowRequest(clientIp, endpoint);
            filterChain.doFilter(request, response);
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for client {} on endpoint {}: {}", 
                    clientIp, endpoint, e.getMessage());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(e.getWaitTimeSeconds()));

            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    e.getMessage(),
                    System.currentTimeMillis()
            );

            objectMapper.writeValue(response.getOutputStream(), errorResponse);
        }
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
} 