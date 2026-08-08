package com.project.api_rate_limiter.redis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.project.api_rate_limiter.config.RateLimitConfig;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(name = "rate-limiter.enable-redis", havingValue = "true")
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final String KEY_PREFIX = "rate_limit:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Boolean> rateLimitScript;
    private final RateLimitConfig config;

    public int getRemainingRequests(String key, int limit) {
        if (!config.isEnableRedis()) {
            return limit;
        }
        String redisKey = KEY_PREFIX + key;
        Long count = redisTemplate.opsForZSet().zCard(redisKey);
        return limit - (count != null ? count.intValue() : 0);
    }

    public boolean allowRequest(String key, int limit, int timeWindowSeconds) {
        if (!config.isEnableRedis()) {
            return true;
        }

        String redisKey = KEY_PREFIX + key;
        long currentTimestamp = Instant.now().toEpochMilli();
        // Unique member per call — two requests arriving in the same millisecond
        // would otherwise collide on ZADD and only be counted once.
        String member = currentTimestamp + ":" + UUID.randomUUID();

        // Lua script runs atomically inside Redis — trim, count, admit in one round-trip.
        return Boolean.TRUE.equals(redisTemplate.execute(
                rateLimitScript,
                List.of(redisKey),
                String.valueOf(limit),
                String.valueOf(timeWindowSeconds),
                String.valueOf(currentTimestamp),
                member
        ));
    }

    public long getWaitTimeSeconds(String key, int timeWindowSeconds) {
        if (!config.isEnableRedis()) {
            return 0;
        }

        String redisKey = KEY_PREFIX + key;
        var oldestEntries = redisTemplate.opsForZSet().rangeWithScores(redisKey, 0, 0);
        if (oldestEntries == null || oldestEntries.isEmpty()) {
            return 0;
        }

        long oldestTimestamp = Objects.requireNonNull(oldestEntries.iterator().next().getScore()).longValue();
        long timeWindowMillis = timeWindowSeconds * 1000L;
        long currentTimeMillis = System.currentTimeMillis();
        long waitTimeMillis = Math.max(0, oldestTimestamp - (currentTimeMillis - timeWindowMillis));

        // Ceiling — see SlidingWindowAlgorithm#getWaitTimeSeconds.
        return (waitTimeMillis + 999) / 1000;
    }
}