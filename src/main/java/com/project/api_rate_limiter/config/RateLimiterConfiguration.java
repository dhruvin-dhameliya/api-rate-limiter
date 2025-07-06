package com.project.api_rate_limiter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitConfig.class)
public class RateLimiterConfiguration {

}