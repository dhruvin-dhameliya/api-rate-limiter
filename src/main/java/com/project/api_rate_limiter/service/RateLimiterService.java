package com.project.api_rate_limiter.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.project.api_rate_limiter.algorithm.SlidingWindowAlgorithm;
import com.project.api_rate_limiter.annotation.RateLimitType;
import com.project.api_rate_limiter.config.RateLimitConfig;
import com.project.api_rate_limiter.config.RateLimitConfig.EndpointLimit;
import com.project.api_rate_limiter.exception.RateLimitExceededException;
import com.project.api_rate_limiter.model.ApiKey;
import com.project.api_rate_limiter.redis.RedisRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that handles rate limiting logic using Sliding Window algorithm.
 * 
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
    public RateLimiterService(@Qualifier("rateLimitConfig") RateLimitConfig config, 
                             SlidingWindowAlgorithm slidingWindowAlgorithm) {
        this.config = config;
        this.slidingWindowAlgorithm = slidingWindowAlgorithm;
    }

    /**
     * Basic allow request method - for backward compatibility.
     */
    public boolean allowRequest(String clientId, String endpoint) throws RateLimitExceededException {
        if (!config.isEnabled()) {
            return true; // Rate limiting is disabled globally
        }

        String key = clientId + ":" + endpoint;
        EndpointLimit endpointLimit = config.getEndpoints().get(endpoint);

        if (endpointLimit != null && !endpointLimit.isEnabled()) {
            return true;
        }

        int limit = (endpointLimit != null && endpointLimit.getLimit() > 0) 
                ? endpointLimit.getLimit() 
                : config.getDefaultLimit();
        
        int timeWindow = (endpointLimit != null && endpointLimit.getTimeWindowSeconds() > 0)
                ? endpointLimit.getTimeWindowSeconds()
                : config.getDefaultTimeWindowSeconds();
        
        return checkRateLimit(clientId, endpoint, limit, timeWindow, RateLimitType.IP_BASED);
    }

    /**
     * Method handling annotated rate limits.
     */
    public boolean allowRequest(String clientId, String endpoint, 
            int annotationLimit, int annotationTimeWindow) 
            throws RateLimitExceededException {
        return allowRequestWithType(clientId, endpoint, annotationLimit, annotationTimeWindow, RateLimitType.GLOBAL, null);
    }
    
    /**
     * Method handling all types of rate limiting with a request context
     */
    public boolean allowRequestWithType(String clientId, String endpoint, 
            int annotationLimit, int annotationTimeWindow, 
            RateLimitType type, HttpServletRequest request) 
            throws RateLimitExceededException {
        if (!config.isEnabled()) {
            return true;
        }

        // Check DDoS protection if enabled
        if (request != null && type == RateLimitType.IP_BASED) {
            if (!ddosProtectionService.trackRequest(clientId)) {
                throw new RateLimitExceededException(
                        "Request blocked due to DDoS protection. Your IP has been temporarily banned.", 
                        ddosProtectionService.getBanDurationSeconds());
            }
        }

        // Determine the key based on the rate limit type
        String key = determineKeyByType(clientId, endpoint, type, request);
        
        EndpointLimit endpointLimit = config.getEffectiveEndpointLimit(endpoint);

        if (!endpointLimit.isEnabled()) {
            return true;
        }
        
        // Check for API key rate limits
        if (type == RateLimitType.API_KEY_BASED && request != null) {
            String apiKeyValue = request.getHeader("X-API-Key");
            if (apiKeyValue != null && !apiKeyValue.isEmpty()) {
                ApiKey apiKey = apiKeyService.getApiKey(apiKeyValue);
                if (apiKey != null && apiKeyService.validateApiKey(apiKeyValue)) {
                    return checkRateLimit(apiKeyValue, endpoint, 
                            apiKey.getRateLimit(), 
                            apiKey.getTimeWindowSeconds(), 
                            RateLimitType.API_KEY_BASED);
                }
            }
        }
        
        // Determine the effective rate limit parameters
        // Priority: 
        // 1. Environment variables (handled in getEffectiveEndpointLimit)
        // 2. application.properties configuration
        // 3. Annotation values
        // 4. Default configuration
        int limit;
        int timeWindow;

        if (endpointLimit.getLimit() > 0) {
            // Use endpoint-specific configuration from application.properties or environment variables
            limit = endpointLimit.getLimit();
            timeWindow = endpointLimit.getTimeWindowSeconds() > 0 ? 
                    endpointLimit.getTimeWindowSeconds() : 
                    config.getEffectiveDefaultTimeWindowSeconds();
        } else if (annotationLimit > 0) {
            // If no endpoint-specific config, use annotation values
            limit = annotationLimit;
            timeWindow = annotationTimeWindow > 0 ? 
                    annotationTimeWindow : 
                    config.getEffectiveDefaultTimeWindowSeconds();
        } else {
            // Fall back to default configuration
            limit = config.getEffectiveDefaultLimit();
            timeWindow = config.getEffectiveDefaultTimeWindowSeconds();
        }
        
        log.debug("Using rate limit: {} requests per {} seconds for endpoint {} with type {}", 
                limit, timeWindow, endpoint, type);
        
        return checkRateLimit(key, endpoint, limit, timeWindow, type);
    }
    
    /**
     * Check if HTTP method is allowed for rate limiting
     * 
     * @param request The HTTP request
     * @param methods Allowed methods array from annotation
     * @return true if the method is allowed for rate limiting
     */
    public boolean isMethodAllowed(HttpServletRequest request, String[] methods) {
        if (methods == null || methods.length == 0) {
            return true; // All methods allowed if none specified
        }
        
        String requestMethod = request.getMethod();
        for (String method : methods) {
            if (method.equalsIgnoreCase(requestMethod)) {
                return true;
            }
        }
        
        return false; // Method not in the allowed list
    }
    
    /**
     * Core rate limit checking logic.
     */
    private boolean checkRateLimit(String key, String endpoint, int limit, int timeWindow, RateLimitType type) 
            throws RateLimitExceededException {
        // Check if Redis is enabled and use it for distributed rate limiting
        if (config.isEnableRedis() && redisRateLimiter != null) {
            boolean allowed = redisRateLimiter.allowRequest(key, limit, timeWindow);
            if (!allowed) {
                long waitTime = redisRateLimiter.getWaitTimeSeconds(key);
                throw new RateLimitExceededException(
                        formatRateLimitMessage(type, waitTime), 
                        waitTime);
            }
            return true;
        }

        boolean allowed = slidingWindowAlgorithm.allowRequest(key, limit, timeWindow);
        
        if (!allowed) {
            long waitTime = slidingWindowAlgorithm.getWaitTimeSeconds(key);
            throw new RateLimitExceededException(
                    formatRateLimitMessage(type, waitTime), 
                    waitTime);
        }
        return true;
    }
    
    /**
     * Determine the key to use for rate limiting based on the type.
     */
    private String determineKeyByType(String clientId, String endpoint, RateLimitType type, HttpServletRequest request) {
        switch (type) {
            case IP_BASED:
                return "ip:" + clientId + ":" + endpoint;
                
            case USER_BASED:
                if (request != null && request.getUserPrincipal() != null) {
                    return "user:" + request.getUserPrincipal().getName() + ":" + endpoint;
                }
                // Fall back to IP-based if user is not authenticated
                return "ip:" + clientId + ":" + endpoint;
                
            case API_KEY_BASED:
                if (request != null) {
                    String apiKey = request.getHeader("X-API-Key");
                    if (apiKey != null && !apiKey.isEmpty()) {
                        return "api-key:" + apiKey + ":" + endpoint;
                    }
                }
                // Fall back to IP-based if API key is not present
                return "ip:" + clientId + ":" + endpoint;
                
            case METHOD_BASED:
                if (request != null) {
                    return "method:" + request.getMethod() + ":" + endpoint;
                }
                return "endpoint:" + endpoint;
                
            case ENDPOINT_BASED:
                return "endpoint:" + endpoint;
                
            case GLOBAL:
            default:
                return "global:" + endpoint;
        }
    }
    
    /**
     * Format a user-friendly message based on the rate limit type.
     */
    private String formatRateLimitMessage(RateLimitType type, long waitTimeSeconds) {
        switch (type) {
            case IP_BASED:
                return "IP-based rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
                
            case USER_BASED:
                return "User-based rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
                
            case API_KEY_BASED:
                return "API key rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
                
            case METHOD_BASED:
                return "HTTP method rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
                
            case ENDPOINT_BASED:
                return "Endpoint rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
                
            case GLOBAL:
            default:
                return "Rate limit exceeded. Please try again in " + waitTimeSeconds + " seconds.";
        }
    }
} 