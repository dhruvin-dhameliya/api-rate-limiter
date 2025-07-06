package com.project.api_rate_limiter.service;

import com.project.api_rate_limiter.model.ApiKey;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing API keys.
 */
@Service
public class ApiKeyService {
    
    private final Map<String, ApiKey> apiKeys = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Generates a new API key.
     * 
     * @param owner The owner of the API key
     * @param rateLimit The rate limit for this API key
     * @param timeWindowSeconds The time window in seconds for the rate limit
     * @param expiryDays Number of days until the key expires, 0 for no expiration
     * @return The generated API key
     */
    public ApiKey generateApiKey(String owner, int rateLimit, int timeWindowSeconds, int expiryDays) {
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
    
    /**
     * Validates an API key.
     * 
     * @param key The API key to validate
     * @return true if the key is valid, false otherwise
     */
    public boolean validateApiKey(String key) {
        ApiKey apiKey = apiKeys.get(key);
        if (apiKey == null || !apiKey.isEnabled()) {
            return false;
        }

        return apiKey.getExpiresAt() == null || !apiKey.getExpiresAt().isBefore(LocalDateTime.now()); // Key has expired
    }
    
    /**
     * Gets an API key by its key string.
     * 
     * @param key The API key string
     * @return The ApiKey object, or null if not found
     */
    public ApiKey getApiKey(String key) {
        return apiKeys.get(key);
    }
    
    /**
     * Revokes an API key.
     * 
     * @param key The API key to revoke
     * @return true if successfully revoked, false if the key doesn't exist
     */
    public boolean revokeApiKey(String key) {
        ApiKey apiKey = apiKeys.get(key);
        if (apiKey != null) {
            apiKey.setEnabled(false);
            return true;
        }
        return false;
    }
} 