package com.project.api_rate_limiter.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(RateLimitConfig.class)
public class RateLimiterConfiguration {
    @Bean
    @Primary
    @Qualifier("rateLimitConfig")
    public RateLimitConfig rateLimitConfig() {
        return new RateLimitConfig();
    }
} 