package com.project.api_rate_limiter.exception;

import lombok.Getter;

import java.io.Serial;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long waitTimeSeconds;
    private final int limit;
    private final int remaining;

    public RateLimitExceededException(String message, long waitTimeSeconds, int limit, int remaining) {
        super(message);
        this.waitTimeSeconds = waitTimeSeconds;
        this.limit = limit;
        this.remaining = remaining;
    }

    public RateLimitExceededException(String message, long waitTimeSeconds) {
        this(message, waitTimeSeconds, 0, 0);
    }
}