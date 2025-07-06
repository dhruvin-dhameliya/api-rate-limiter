package com.project.api_rate_limiter.annotation;

public enum RateLimitType {
    GLOBAL,
    IP_BASED,
    USER_BASED,
    API_KEY_BASED,
    METHOD_BASED,
    ENDPOINT_BASED
}