package com.project.api_rate_limiter.service;

import com.project.api_rate_limiter.model.ApiKey;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApiKeyService {

    private final Map<String, ApiKey> apiKeys = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKey generateApiKey(String owner, int rateLimit, int timeWindowSeconds, int expiryDays) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be null or blank");
        }
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        LocalDateTime expiresAt = expiryDays > 0
                ? LocalDateTime.now().plusDays(expiryDays)
                : null;

        ApiKey apiKey = new ApiKey(
                key,
                owner,
                rateLimit,
                timeWindowSeconds,
                true,
                LocalDateTime.now(),
                expiresAt
        );

        apiKeys.put(key, apiKey);
        return apiKey;
    }

    public boolean validateApiKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        ApiKey apiKey = apiKeys.get(key);
        if (apiKey == null || !apiKey.isEnabled()) {
            return false;
        }
        return apiKey.getExpiresAt() == null || !apiKey.getExpiresAt().isBefore(LocalDateTime.now());
    }

    public ApiKey getApiKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return apiKeys.get(key);
    }

    public boolean revokeApiKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        ApiKey apiKey = apiKeys.get(key);
        if (apiKey != null) {
            apiKey.setEnabled(false);
            return true;
        }
        return false;
    }
}