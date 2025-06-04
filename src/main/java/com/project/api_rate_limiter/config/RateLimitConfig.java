package com.project.api_rate_limiter.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for rate limiting.
 * 1. Environment variables (highest priority)
 *    - Format: RATE_LIMITER_DEFAULT_LIMIT, RATE_LIMITER_ENDPOINTS_LOGIN_LIMIT, etc.
 * 2. application.properties values
 *    - Format: rate-limiter.default-limit, rate-limiter.endpoints.login.limit, etc.
 * 3. Annotation values (from @RateLimit)
 * 4. Default hardcoded values (lowest priority)
 */
@Data
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimitConfig implements EnvironmentAware {
    
    private Environment environment;
    
    // Default rate limit for all endpoints (if not specified)
    private int defaultLimit = 100;

    private int defaultTimeWindowSeconds = 60;

    private boolean enableRedis = false;
    
    // Whether to enable rate limiting globally
    private boolean enabled = true;
    
    // Endpoint-specific rate limits
    private Map<String, EndpointLimit> endpoints = new HashMap<>();
    
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    private String getEffectiveValue(String propertyName, String defaultValue) {
        // Check environment variable first (highest priority)
        String envVarName = propertyName.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = environment.getProperty(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        
        // Then check application properties
        String propValue = environment.getProperty(propertyName);
        if (propValue != null && !propValue.isEmpty()) {
            return propValue;
        }

        return defaultValue;
    }

    public EndpointLimit getEffectiveEndpointLimit(String endpoint) {
        EndpointLimit endpointLimit = endpoints.get(endpoint);
        if (endpointLimit == null) {
            endpointLimit = new EndpointLimit();
        }

        EndpointLimit effectiveLimit = new EndpointLimit();
        effectiveLimit.setLimit(endpointLimit.getLimit());
        effectiveLimit.setTimeWindowSeconds(endpointLimit.getTimeWindowSeconds());
        effectiveLimit.setEnabled(endpointLimit.isEnabled());
        
        // Check environment variables for endpoint-specific values (highest priority)
        String envPrefix = "RATE_LIMITER_ENDPOINTS_" + endpoint.toUpperCase() + "_";
        
        // Get limit from environment variable
        String envLimitKey = envPrefix + "LIMIT";
        String envLimitValue = environment.getProperty(envLimitKey);
        if (envLimitValue != null && !envLimitValue.isEmpty()) {
            try {
                effectiveLimit.setLimit(Integer.parseInt(envLimitValue));
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        
        // Get time window from environment variable
        String envTimeWindowKey = envPrefix + "TIME_WINDOW_SECONDS";
        String envTimeWindowValue = environment.getProperty(envTimeWindowKey);
        if (envTimeWindowValue != null && !envTimeWindowValue.isEmpty()) {
            try {
                effectiveLimit.setTimeWindowSeconds(Integer.parseInt(envTimeWindowValue));
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        
        // Get enabled flag from environment variable
        String envEnabledKey = envPrefix + "ENABLED";
        String envEnabledValue = environment.getProperty(envEnabledKey);
        if (envEnabledValue != null && !envEnabledValue.isEmpty()) {
            effectiveLimit.setEnabled(Boolean.parseBoolean(envEnabledValue));
        }
        return effectiveLimit;
    }

    public int getEffectiveDefaultLimit() {
        // Check environment variable first (highest priority)
        String envKey = "RATE_LIMITER_DEFAULT_LIMIT";
        String envValue = environment.getProperty(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        
        // Then check application.properties
        String propKey = "rate-limiter.default-limit";
        String propValue = environment.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        return defaultLimit;
    }

    public int getEffectiveDefaultTimeWindowSeconds() {
        String envKey = "RATE_LIMITER_DEFAULT_TIME_WINDOW_SECONDS";
        String envValue = environment.getProperty(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }

        String propKey = "rate-limiter.default-time-window-seconds";
        String propValue = environment.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        return defaultTimeWindowSeconds;
    }

    @Data
    public static class EndpointLimit {
        private int limit;
        private int timeWindowSeconds;
        private boolean enabled = true;
    }
} 