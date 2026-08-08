package com.project.api_rate_limiter.filter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

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

/**
 * Servlet filter that enforces rate limits and IP allow/deny before the request
 * reaches any Spring MVC handler. Runs just after the Spring Security chain so
 * authentication has already populated the request principal.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(name = "rate-limiter.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /** Marks a request as already counted by this filter, so downstream enforcers can skip it. */
    public static final String ENFORCED_ATTRIBUTE = "com.project.api_rate_limiter.enforced";

    /** Trace id generated per request; echoed back in {@code X-Trace-ID}. */
    public static final String TRACE_ID_ATTRIBUTE = "com.project.api_rate_limiter.traceId";

    private static final String[] PROXY_HEADERS = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

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

        String traceId = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader("X-Trace-ID", traceId);

        String clientIp = resolveClientIp(request, config.getTrustedProxies());
        String endpoint = normalizeEndpoint(request.getRequestURI());

        if (config.isEnableIpFiltering()) {
            if (ipFilterService.isBlacklistedFor(clientIp, endpoint)) {
                log.warn("Request from blacklisted IP {} blocked for endpoint {}", clientIp, endpoint);
                writeError(response, HttpStatus.FORBIDDEN, "Forbidden",
                        "Access denied: Your IP address is blacklisted",
                        request.getRequestURI(), traceId);
                return;
            }
            if (ipFilterService.isWhitelistedFor(clientIp, endpoint)) {
                log.debug("Request from whitelisted IP {} bypassing rate limiting for endpoint {}", clientIp, endpoint);
                // Mark so the aspect on the controller method also skips — otherwise
                // a whitelisted IP would still get counted by @RateLimit on the handler.
                request.setAttribute(ENFORCED_ATTRIBUTE, Boolean.TRUE);
                filterChain.doFilter(request, response);
                return;
            }
        }

        try {
            HandlerMethod handlerMethod = getHandlerMethod(request);
            if (handlerMethod != null) {
                Method method = handlerMethod.getMethod();
                RateLimit rateLimit = method.getAnnotation(RateLimit.class);
                if (rateLimit == null) {
                    rateLimit = method.getDeclaringClass().getAnnotation(RateLimit.class);
                }
                if (rateLimit != null) {
                    if (!rateLimiterService.isMethodAllowed(request, rateLimit.methods())) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    rateLimiterService.allowRequestWithType(clientIp, endpoint,
                            rateLimit.limit(), rateLimit.timeWindowSeconds(),
                            rateLimit.type(), request);
                } else {
                    rateLimiterService.allowRequestWithType(clientIp, endpoint, 0, 0,
                            RateLimitType.IP_BASED, request);
                }
            } else {
                rateLimiterService.allowRequestWithType(clientIp, endpoint, 0, 0,
                        RateLimitType.IP_BASED, request);
            }
            request.setAttribute(ENFORCED_ATTRIBUTE, Boolean.TRUE);
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for client {} on endpoint {}: {}",
                    clientIp, endpoint, e.getMessage());
            response.setHeader("X-RateLimit-Limit", String.valueOf(e.getLimit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(e.getRemaining()));
            response.setHeader("Retry-After", String.valueOf(e.getWaitTimeSeconds()));
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    e.getMessage(), request.getRequestURI(), traceId);
            return;
        } catch (UnauthorizedException e) {
            log.warn("Unauthorized request for client {} on endpoint {}: {}",
                    clientIp, endpoint, e.getMessage());
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    e.getMessage(), request.getRequestURI(), traceId);
            return;
        } catch (Exception e) {
            // Fail-open: log and pass the request through untouched. The chain is
            // invoked exactly once below, so downstream exceptions cannot loop back here.
            log.error("Error in rate limit check — failing open", e);
        }

        filterChain.doFilter(request, response);
    }

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

    private void writeError(HttpServletResponse response, HttpStatus status, String error,
                            String message, String path, String traceId) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        ErrorResponse body = new ErrorResponse(status.value(), error, message, path);
        body.setTraceId(traceId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /** Normalises the request URI into a bucket-key fragment ({@code /api/v1/x} → {@code api-v1-x}). */
    private String normalizeEndpoint(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "";
        }
        return uri.startsWith("/") ? uri.substring(1).replace('/', '-') : uri.replace('/', '-');
    }

    /**
     * Extract the client IP, respecting an optional trusted-proxies allowlist.
     * When {@code trustedProxies} is non-empty and the immediate connection did
     * not come from a trusted proxy, proxy headers are ignored.
     */
    public static String resolveClientIp(HttpServletRequest request, List<String> trustedProxies) {
        String remote = request.getRemoteAddr();
        boolean trustHeaders = trustedProxies == null || trustedProxies.isEmpty()
                || trustedProxies.contains(remote);
        if (!trustHeaders) {
            return remote;
        }

        for (String header : PROXY_HEADERS) {
            String value = request.getHeader(header);
            if (value == null || value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            // X-Forwarded-For may be "client, proxy1, proxy2" — first entry is the originating client.
            int comma = value.indexOf(',');
            String ip = (comma > 0 ? value.substring(0, comma) : value).trim();
            if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return remote;
    }

    /**
     * Convenience overload that reads proxy headers unconditionally. Prefer
     * {@link #resolveClientIp(HttpServletRequest, List)} with a trusted-proxies
     * list — this one is safe only when the app cannot be reached directly by
     * untrusted clients.
     */
    public static String extractClientIp(HttpServletRequest request) {
        return resolveClientIp(request, List.of());
    }
}
