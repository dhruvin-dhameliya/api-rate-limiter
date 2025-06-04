package com.project.api_rate_limiter.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

import com.project.api_rate_limiter.config.RateLimitConfig;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
    
    // Configure the Lua script for rate limiting
    @Bean
    public RedisScript<Boolean> rateLimitScript() {
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/rate-limit.lua")));
        redisScript.setResultType(Boolean.class);
        return redisScript;
    }

    @Bean
    public boolean createRedisRateLimiter(RateLimitConfig config) {
        return config.isEnableRedis();
    }
} 