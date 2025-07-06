package com.project.api_rate_limiter.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

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
    
    // IP whitelist and blacklist settings
    private List<String> whitelistedIps = new ArrayList<>();
    private List<String> blacklistedIps = new ArrayList<>();
    private boolean enableIpFiltering = true;
    
    // DDoS protection settings
    private boolean ddosProtectionEnabled = false;
    private int ddosThreshold = 1000;
    private int ddosBanDurationSeconds = 3600; // 1 hour
    private int ddosCountResetIntervalSeconds = 60; // 1 minute
    
    // HTTP method-specific rate limits
    private Map<String, Integer> methodLimits = new HashMap<>();
    
    // User-based rate limiting settings
    private boolean userBasedLimitingEnabled = false;
    private int defaultUserLimit = 50;
    private int defaultUserTimeWindowSeconds = 60;
    
    // API key rate limiting settings
    private boolean apiKeyBasedLimitingEnabled = false;
    private int defaultApiKeyLimit = 200;
    private int defaultApiKeyTimeWindowSeconds = 60;
    private List<String> apiKeyHeaders = new ArrayList<>(List.of(
        "X-API-Key",
        "api-key",
        "apikey",
        "api_key",
        "key"
    ));
    
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
        effectiveLimit.setUserLimit(endpointLimit.getUserLimit());
        effectiveLimit.setUserTimeWindowSeconds(endpointLimit.getUserTimeWindowSeconds());
        effectiveLimit.setApiKeyLimit(endpointLimit.getApiKeyLimit());
        effectiveLimit.setApiKeyTimeWindowSeconds(endpointLimit.getApiKeyTimeWindowSeconds());
        effectiveLimit.setMethodLimits(new HashMap<>(endpointLimit.getMethodLimits()));
        effectiveLimit.setWhitelistedIps(new ArrayList<>(endpointLimit.getWhitelistedIps()));
        effectiveLimit.setBlacklistedIps(new ArrayList<>(endpointLimit.getBlacklistedIps()));
        
        // Check environment variables for endpoint-specific values (highest priority)
        String envPrefix = "RATE_LIMITER_ENDPOINTS_" + endpoint.toUpperCase().replace('-', '_').replace('/', '_') + "_";
        
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
        
        // Get user limit settings
        String envUserLimitKey = envPrefix + "USER_LIMIT";
        String envUserLimitValue = environment.getProperty(envUserLimitKey);
        if (envUserLimitValue != null && !envUserLimitValue.isEmpty()) {
            try {
                effectiveLimit.setUserLimit(Integer.parseInt(envUserLimitValue));
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        
        // Get API key limit settings
        String envApiKeyLimitKey = envPrefix + "API_KEY_LIMIT";
        String envApiKeyLimitValue = environment.getProperty(envApiKeyLimitKey);
        if (envApiKeyLimitValue != null && !envApiKeyLimitValue.isEmpty()) {
            try {
                effectiveLimit.setApiKeyLimit(Integer.parseInt(envApiKeyLimitValue));
            } catch (NumberFormatException e) {
                // Log error
            }
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
    
    /**
     * Get effective DDoS protection settings from environment variables or properties.
     */
    public boolean getEffectiveDdosProtectionEnabled() {
        String envKey = "RATE_LIMITER_DDOS_PROTECTION_ENABLED";
        String envValue = environment.getProperty(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return Boolean.parseBoolean(envValue);
        }
        
        String propKey = "rate-limiter.ddos-protection-enabled";
        String propValue = environment.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            return Boolean.parseBoolean(propValue);
        }
        
        return ddosProtectionEnabled;
    }
    
    /**
     * Get effective DDoS threshold from environment variables or properties.
     */
    public int getEffectiveDdosThreshold() {
        String envKey = "RATE_LIMITER_DDOS_THRESHOLD";
        String envValue = environment.getProperty(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        
        String propKey = "rate-limiter.ddos-threshold";
        String propValue = environment.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        
        return ddosThreshold;
    }

    public int getEffectiveDefaultApiKeyLimit() {
        String envKey = "RATE_LIMITER_DEFAULT_API_KEY_LIMIT";
        String envValue = environment.getProperty(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }

        String propKey = "rate-limiter.default-api-key-limit";
        String propValue = environment.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        return defaultApiKeyLimit;
    }

    public int getEffectiveDefaultApiKeyTimeWindowSeconds() {
        String envKey = "RATE_LIMITER_DEFAULT_API_KEY_TIME_WINDOW_SECONDS";
        String envValue = environment.getProperty(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }

        String propKey = "rate-limiter.default-api-key-time-window-seconds";
        String propValue = environment.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                // Log error
            }
        }
        return defaultApiKeyTimeWindowSeconds;
    }

    @Data
    public static class EndpointLimit {
        private int limit;
        private int timeWindowSeconds;
        private boolean enabled = true;

        // User-based rate limiting settings
        private int userLimit;
        private int userTimeWindowSeconds;
        
        // API key-based rate limiting settings
        private int apiKeyLimit;
        private int apiKeyTimeWindowSeconds;
        
        // HTTP method-specific rate limits
        private Map<String, Integer> methodLimits = new HashMap<>();
        
        // Endpoint-specific whitelist and blacklist
        private List<String> whitelistedIps = new ArrayList<>();
        private List<String> blacklistedIps = new ArrayList<>();
    }
}