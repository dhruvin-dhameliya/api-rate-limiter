package com.project.api_rate_limiter.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.project.api_rate_limiter.algorithm.SlidingWindowAlgorithm;
import com.project.api_rate_limiter.config.RateLimitConfig;
import com.project.api_rate_limiter.config.RateLimitConfig.EndpointLimit;
import com.project.api_rate_limiter.exception.RateLimitExceededException;
import com.project.api_rate_limiter.redis.RedisRateLimiter;

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
    public RateLimiterService(@Qualifier("rateLimitConfig") RateLimitConfig config, 
                             SlidingWindowAlgorithm slidingWindowAlgorithm) {
        this.config = config;
        this.slidingWindowAlgorithm = slidingWindowAlgorithm;
    }

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
        
        return allowRequest(clientId, endpoint, limit, timeWindow);
    }

    public boolean allowRequest(String clientId, String endpoint, 
            int annotationLimit, int annotationTimeWindow) 
            throws RateLimitExceededException {
        if (!config.isEnabled()) {
            return true;
        }

        String key = clientId + ":" + endpoint;
        EndpointLimit endpointLimit = config.getEffectiveEndpointLimit(endpoint);

        if (!endpointLimit.isEnabled()) {
            return true;
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
        
        log.debug("Using rate limit: {} requests per {} seconds for endpoint {}", limit, timeWindow, endpoint);
        
        // Check if Redis is enabled and use it for distributed rate limiting
        if (config.isEnableRedis() && redisRateLimiter != null) {
            boolean allowed = redisRateLimiter.allowRequest(key, limit, timeWindow);
            if (!allowed) {
                long waitTime = redisRateLimiter.getWaitTimeSeconds(key);
                throw new RateLimitExceededException(
                        "Rate limit exceeded. Please try again in " + waitTime + " seconds.", 
                        waitTime);
            }
            return true;
        }

        boolean allowed = slidingWindowAlgorithm.allowRequest(key, limit, timeWindow);
        
        if (!allowed) {
            long waitTime = slidingWindowAlgorithm.getWaitTimeSeconds(key);
            throw new RateLimitExceededException(
                    "Rate limit exceeded. Please try again in " + waitTime + " seconds.", 
                    waitTime);
        }
        return true;
    }
} 