package com.project.api_rate_limiter.algorithm;

public interface RateLimitAlgorithm {
    boolean allowRequest(String key, int maxRequests, int timeWindowSeconds);

    /** Seconds until the oldest tracked request falls out of the window. */
    long getWaitTimeSeconds(String key, int timeWindowSeconds);
}