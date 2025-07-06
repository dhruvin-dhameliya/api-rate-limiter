package com.project.api_rate_limiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Model representing an API key for rate limiting purposes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {
    private String key;
    private String owner;
    private int rateLimit;
    private int timeWindowSeconds;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
} 