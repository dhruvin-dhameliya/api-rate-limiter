package com.project.api_rate_limiter.redis;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.project.api_rate_limiter.config.RateLimitConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisRateLimiter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Boolean> rateLimitScript;
    
    @Qualifier("rateLimitConfig")
    private final RateLimitConfig config;
    
    private static final String KEY_PREFIX = "rate_limit:";

    public boolean allowRequest(String key, int limit, int timeWindowSeconds) {
        if (!config.isEnableRedis()) {
            return true; // Redis rate limiting is disabled
        }
        
        String redisKey = KEY_PREFIX + key;
        long currentTimestamp = Instant.now().getEpochSecond();
        
        // Execute the Lua script atomically in Redis
        return Boolean.TRUE.equals(redisTemplate.execute(
                rateLimitScript,
                List.of(redisKey),
                String.valueOf(limit),
                String.valueOf(timeWindowSeconds),
                String.valueOf(currentTimestamp)
        ));
    }

    public long getWaitTimeSeconds(String key) {
        if (!config.isEnableRedis()) {
            return 0;
        }
        
        String redisKey = KEY_PREFIX + key;
        String value = redisTemplate.opsForValue().get(redisKey);
        
        if (value == null) {
            return 0;
        }
        
        try {
            long expiryTime = redisTemplate.getExpire(redisKey).longValue();
            return Math.max(0, expiryTime);
        } catch (Exception e) {
            return 0;
        }
    }
} 