package com.project.api_rate_limiter.exception;

import lombok.Getter;

import java.io.Serial;

@Getter
public class RateLimitExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;
    
    private final long waitTimeSeconds;

    public RateLimitExceededException(String message, long waitTimeSeconds) {
        super(message);
        this.waitTimeSeconds = waitTimeSeconds;
    }
} 