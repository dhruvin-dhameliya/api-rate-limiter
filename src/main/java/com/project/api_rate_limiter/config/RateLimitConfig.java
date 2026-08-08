package com.project.api_rate_limiter.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate-limiter configuration. Precedence, highest first:
 * environment variables, application properties, {@code @RateLimit} annotation,
 * defaults defined here.
 */
@Data
@Slf4j
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimitConfig implements EnvironmentAware {

    private Environment environment;

    private int defaultLimit = 100;
    private int defaultTimeWindowSeconds = 60;

    private boolean enabled = true;
    private boolean enableRedis = false;
    private boolean enableIpFiltering = true;

    private List<String> whitelistedIps = new ArrayList<>();
    private List<String> blacklistedIps = new ArrayList<>();

    /**
     * When non-empty, proxy headers ({@code X-Forwarded-For} et al.) are only
     * honoured if the immediate connection came from one of these IPs. When
     * empty, proxy headers are trusted unconditionally — safe only for local
     * development; set this in production behind a load balancer.
     */
    private List<String> trustedProxies = new ArrayList<>();

    private boolean ddosProtectionEnabled = false;
    private int ddosThreshold = 1000;
    private int ddosBanDurationSeconds = 3600;
    private int ddosCountResetIntervalSeconds = 60;

    private Map<String, Integer> methodLimits = new HashMap<>();

    private boolean userBasedLimitingEnabled = false;
    private int defaultUserLimit = 50;
    private int defaultUserTimeWindowSeconds = 60;

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

    private Map<String, EndpointLimit> endpoints = new HashMap<>();

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    private String getEffectiveValue(String propertyName, String defaultValue) {
        String envVarName = propertyName.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = environment.getProperty(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
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
        
        String envPrefix = "RATE_LIMITER_ENDPOINTS_" + endpoint.toUpperCase().replace('-', '_').replace('/', '_') + "_";

        Integer envLimit = parseIntProperty(envPrefix + "LIMIT");
        if (envLimit != null) effectiveLimit.setLimit(envLimit);

        Integer envTimeWindow = parseIntProperty(envPrefix + "TIME_WINDOW_SECONDS");
        if (envTimeWindow != null) effectiveLimit.setTimeWindowSeconds(envTimeWindow);

        Integer envUserLimit = parseIntProperty(envPrefix + "USER_LIMIT");
        if (envUserLimit != null) effectiveLimit.setUserLimit(envUserLimit);

        Integer envApiKeyLimit = parseIntProperty(envPrefix + "API_KEY_LIMIT");
        if (envApiKeyLimit != null) effectiveLimit.setApiKeyLimit(envApiKeyLimit);

        String envEnabledValue = environment.getProperty(envPrefix + "ENABLED");
        if (envEnabledValue != null && !envEnabledValue.isEmpty()) {
            effectiveLimit.setEnabled(Boolean.parseBoolean(envEnabledValue));
        }

        return effectiveLimit;
    }

    public int getEffectiveDefaultLimit() {
        return readIntProperty("RATE_LIMITER_DEFAULT_LIMIT", "rate-limiter.default-limit", defaultLimit);
    }

    public int getEffectiveDefaultTimeWindowSeconds() {
        return readIntProperty("RATE_LIMITER_DEFAULT_TIME_WINDOW_SECONDS",
                "rate-limiter.default-time-window-seconds", defaultTimeWindowSeconds);
    }

    public boolean getEffectiveDdosProtectionEnabled() {
        String envValue = environment.getProperty("RATE_LIMITER_DDOS_PROTECTION_ENABLED");
        if (envValue != null && !envValue.isEmpty()) {
            return Boolean.parseBoolean(envValue);
        }
        String propValue = environment.getProperty("rate-limiter.ddos-protection-enabled");
        if (propValue != null && !propValue.isEmpty()) {
            return Boolean.parseBoolean(propValue);
        }
        return ddosProtectionEnabled;
    }

    public int getEffectiveDdosThreshold() {
        return readIntProperty("RATE_LIMITER_DDOS_THRESHOLD", "rate-limiter.ddos-threshold", ddosThreshold);
    }

    public int getEffectiveDdosBanDurationSeconds() {
        return readIntProperty("RATE_LIMITER_DDOS_BAN_DURATION_SECONDS",
                "rate-limiter.ddos-ban-duration-seconds", ddosBanDurationSeconds);
    }

    public int getEffectiveDdosCountResetIntervalSeconds() {
        return readIntProperty("RATE_LIMITER_DDOS_COUNT_RESET_INTERVAL_SECONDS",
                "rate-limiter.ddos-count-reset-interval-seconds", ddosCountResetIntervalSeconds);
    }

    public int getEffectiveDefaultApiKeyLimit() {
        return readIntProperty("RATE_LIMITER_DEFAULT_API_KEY_LIMIT",
                "rate-limiter.default-api-key-limit", defaultApiKeyLimit);
    }

    public int getEffectiveDefaultApiKeyTimeWindowSeconds() {
        return readIntProperty("RATE_LIMITER_DEFAULT_API_KEY_TIME_WINDOW_SECONDS",
                "rate-limiter.default-api-key-time-window-seconds", defaultApiKeyTimeWindowSeconds);
    }

    private int readIntProperty(String envKey, String propKey, int fallback) {
        Integer envValue = parseIntProperty(envKey);
        if (envValue != null) return envValue;
        Integer propValue = parseIntProperty(propKey);
        if (propValue != null) return propValue;
        return fallback;
    }

    private Integer parseIntProperty(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Ignoring non-numeric value for rate-limiter property {}: '{}'", key, value);
            return null;
        }
    }

    @Data
    public static class EndpointLimit {
        private int limit;
        private int timeWindowSeconds;
        private boolean enabled = true;

        private int userLimit;
        private int userTimeWindowSeconds;

        private int apiKeyLimit;
        private int apiKeyTimeWindowSeconds;

        private Map<String, Integer> methodLimits = new HashMap<>();

        private List<String> whitelistedIps = new ArrayList<>();
        private List<String> blacklistedIps = new ArrayList<>();
    }
}