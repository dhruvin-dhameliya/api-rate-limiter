package com.project.api_rate_limiter.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.api_rate_limiter.algorithm.SlidingWindowAlgorithm;
import com.project.api_rate_limiter.annotation.RateLimitType;
import com.project.api_rate_limiter.config.RateLimitConfig;
import com.project.api_rate_limiter.config.RateLimitConfig.EndpointLimit;
import com.project.api_rate_limiter.exception.RateLimitExceededException;
import com.project.api_rate_limiter.exception.UnauthorizedException;
import com.project.api_rate_limiter.redis.RedisRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that handles rate limiting logic using Sliding Window algorithm.
 * Configuration priority order:
 * 1. Environment variables (highest priority)
 * 2. application.properties configuration
 * 3. Annotation values (@RateLimit)
 * 4. Default configuration (lowest priority)
 */
@Service
@Slf4j
public class RateLimiterService {

    private final RateLimitConfig config;
    private final SlidingWindowAlgorithm slidingWindowAlgorithm;
    
    @Autowired(required = false)
    private RedisRateLimiter redisRateLimiter;
    
    @Autowired
    private ApiKeyService apiKeyService;
    
    @Autowired
    private DdosProtectionService ddosProtectionService;

    @Autowired
    public RateLimiterService(RateLimitConfig config,
                             SlidingWindowAlgorithm slidingWindowAlgorithm) {
        this.config = config;
        this.slidingWindowAlgorithm = slidingWindowAlgorithm;
    }

    public void allowRequest(String clientId, String endpoint) throws RateLimitExceededException {
        allowRequestWithType(clientId, endpoint, 0, 0, RateLimitType.IP_BASED, null);
    }

    public void allowRequest(String clientId, String endpoint,
                             int annotationLimit, int annotationTimeWindow)
            throws RateLimitExceededException {
        allowRequestWithType(clientId, endpoint, annotationLimit, annotationTimeWindow, RateLimitType.GLOBAL, null);
    }

    public void allowRequestWithType(String clientId, String endpoint,
                                     int annotationLimit, int annotationTimeWindow,
                                     RateLimitType type, HttpServletRequest request)
            throws RateLimitExceededException {
        if (!config.isEnabled()) {
            return;
        }

        // Check DDoS protection if enabled
        if (request != null && type == RateLimitType.IP_BASED) {
            if (!ddosProtectionService.trackRequest(clientId)) {
                throw new RateLimitExceededException(
                        "Request blocked due to DDoS protection. Your IP has been temporarily banned.", 
                        ddosProtectionService.getBanDurationSeconds());
            }
        }

        String key = determineKeyByType(clientId, endpoint, type, request);
        if (key == null) {
            if (type == RateLimitType.API_KEY_BASED) {
                throw new UnauthorizedException("API key is missing or invalid.");
            }
            return;
        }
        
        EndpointLimit endpointLimit = config.getEffectiveEndpointLimit(endpoint);

        if (!endpointLimit.isEnabled()) {
            return;
        }
        int limit;
        int timeWindow;

        if (type == RateLimitType.API_KEY_BASED && config.isApiKeyBasedLimitingEnabled()) {
            String apiKeyValue = getApiKeyFromRequest(request);
            // API key is valid, use API key-specific limits with priority
            if (endpointLimit.getApiKeyLimit() > 0) {
                limit = endpointLimit.getApiKeyLimit();
                timeWindow = endpointLimit.getApiKeyTimeWindowSeconds() > 0
                        ? endpointLimit.getApiKeyTimeWindowSeconds()
                        : config.getEffectiveDefaultApiKeyTimeWindowSeconds();
            } else if (annotationLimit > 0) {
                limit = annotationLimit;
                timeWindow = annotationTimeWindow > 0
                        ? annotationTimeWindow
                        : config.getEffectiveDefaultTimeWindowSeconds();
            } else {
                limit = config.getEffectiveDefaultApiKeyLimit();
                timeWindow = config.getEffectiveDefaultApiKeyTimeWindowSeconds();
            }
        } else {
            // Determine the effective rate limit parameters for other types
            if (endpointLimit.getLimit() > 0) {
                limit = endpointLimit.getLimit();
                timeWindow = endpointLimit.getTimeWindowSeconds() > 0
                        ? endpointLimit.getTimeWindowSeconds()
                        : config.getEffectiveDefaultTimeWindowSeconds();
            } else if (annotationLimit > 0) {
                limit = annotationLimit;
                timeWindow = annotationTimeWindow > 0
                        ? annotationTimeWindow
                        : config.getEffectiveDefaultTimeWindowSeconds();
            } else {
                limit = config.getEffectiveDefaultLimit();
                timeWindow = config.getEffectiveDefaultTimeWindowSeconds();
            }
        }
        
        log.debug("Using rate limit: {} requests per {} seconds for endpoint {} with type {}", 
                limit, timeWindow, endpoint, type);

        checkRateLimit(key, endpoint, limit, timeWindow, type, clientId);
    }

    public boolean isMethodAllowed(HttpServletRequest request, String[] methods) {
        if (methods == null || methods.length == 0) return true;
        String requestMethod = request.getMethod();
        for (String method : methods) {
            if (method.equalsIgnoreCase(requestMethod)) return true;
        }
        return false;
    }

    private void checkRateLimit(String key, String endpoint, int limit, int timeWindow, RateLimitType type, String clientId)
            throws RateLimitExceededException {
        boolean allowed;
        long waitTime;
        int remainingRequests;

        if (config.isEnableRedis() && redisRateLimiter != null) {
            allowed = redisRateLimiter.allowRequest(key, limit, timeWindow);
            waitTime = allowed ? 0 : redisRateLimiter.getWaitTimeSeconds(key);
            remainingRequests = allowed ? redisRateLimiter.getRemainingRequests(key, limit) : 0;
        } else {
            allowed = slidingWindowAlgorithm.allowRequest(key, limit, timeWindow);
            waitTime = allowed ? 0 : slidingWindowAlgorithm.getWaitTimeSeconds(key);
            remainingRequests = allowed ? slidingWindowAlgorithm.getRemainingRequests(key, limit) : 0;
        }
        if (!allowed) {
            throw new RateLimitExceededException(
                    formatRateLimitMessage(type, waitTime), 
                    waitTime,
                    limit,
                    remainingRequests
            );
        }
    }

    private String determineKeyByType(String clientId, String endpoint, RateLimitType type, HttpServletRequest request) {
        return switch (type) {
            case IP_BASED -> "ip:" + clientId + ":" + endpoint;
            case USER_BASED -> {
                if (request != null && request.getUserPrincipal() != null) {
                    yield "user:" + request.getUserPrincipal().getName() + ":" + endpoint;
                }
                yield null;
            }
            case API_KEY_BASED -> {
                if (request != null) {
                    String apiKey = getApiKeyFromRequest(request);
                    if (apiKey != null && !apiKey.isEmpty() && apiKeyService.validateApiKey(apiKey)) {
                        yield "api-key:" + apiKey + ":" + endpoint;
                    }
                }
                yield null;
            }
            case METHOD_BASED -> {
                if (request != null) {
                    yield "method:" + request.getMethod() + ":" + endpoint;
                }
                yield "endpoint:" + endpoint;
            }
            case ENDPOINT_BASED -> "endpoint:" + endpoint;
            default -> "global:" + endpoint;
        };
    }

    private String getApiKeyFromRequest(HttpServletRequest request) {
        if (request == null) return null;
        for (String headerName : config.getApiKeyHeaders()) {
            String apiKeyValue = request.getHeader(headerName);
            if (apiKeyValue != null && !apiKeyValue.isEmpty()) {
                return apiKeyValue;
            }
        }
        return null;
    }

    private String formatRateLimitMessage(RateLimitType type, long waitTimeSeconds) {
        return switch (type) {
            case IP_BASED -> "IP-based rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
            case USER_BASED -> "User-based rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
            case API_KEY_BASED -> "API key rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
            case METHOD_BASED -> "HTTP method rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
            case ENDPOINT_BASED -> "Endpoint rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
            default -> "Rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
        };
    }
}