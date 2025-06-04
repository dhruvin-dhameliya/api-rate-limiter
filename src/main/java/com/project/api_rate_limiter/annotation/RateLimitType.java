package com.project.api_rate_limiter.annotation;

/**
 * Enum defining the different types of rate limiting strategies.
 */
public enum RateLimitType {
    /**
     * Global rate limiting applied to all requests regardless of source
     */
    GLOBAL,
    
    /**
     * Rate limiting based on client IP address
     */
    IP_BASED,
    
    /**
     * Rate limiting based on authenticated user ID
     */
    USER_BASED,
    
    /**
     * Rate limiting based on API key
     */
    API_KEY_BASED,
    
    /**
     * Rate limiting specific to HTTP method (GET, POST, etc.)
     */
    METHOD_BASED,
    
    /**
     * Endpoint-specific rate limiting
     */
    ENDPOINT_BASED
} 