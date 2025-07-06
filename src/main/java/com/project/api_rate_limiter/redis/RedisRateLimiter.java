package com.project.api_rate_limiter.redis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.project.api_rate_limiter.config.RateLimitConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    public int getRemainingRequests(String key, int limit) {
        if (!config.isEnableRedis()) {
            return limit;
        }
        String redisKey = KEY_PREFIX + key;
        Long count = redisTemplate.opsForZSet().zCard(redisKey);
        return limit - (count != null ? count.intValue() : 0);
    }
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Boolean> rateLimitScript;

    private final RateLimitConfig config;
    
    private static final String KEY_PREFIX = "rate_limit:";

    public boolean allowRequest(String key, int limit, int timeWindowSeconds) {
        if (!config.isEnableRedis()) {
            return true; // Redis rate limiting is disabled
        }
        
        String redisKey = KEY_PREFIX + key;
        long currentTimestamp = Instant.now().toEpochMilli();

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

        // Get the oldest timestamp from the sorted set
        var oldestEntries = redisTemplate.opsForZSet().rangeWithScores(redisKey, 0, 0);

        if (oldestEntries == null || oldestEntries.isEmpty()) {
            return 0;
        }

        // Extract the timestamp (score)
        long oldestTimestamp = Objects.requireNonNull(oldestEntries.iterator().next().getScore()).longValue();
        long timeWindowMillis = config.getEffectiveDefaultTimeWindowSeconds() * 1000L;
        long currentTimeMillis = System.currentTimeMillis();

        long waitTimeMillis = Math.max(0, oldestTimestamp - (currentTimeMillis - timeWindowMillis));

        return waitTimeMillis / 1000;
    }
}