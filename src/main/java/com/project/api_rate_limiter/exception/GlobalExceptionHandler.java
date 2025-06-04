package com.project.api_rate_limiter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.project.api_rate_limiter.model.ErrorResponse;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceededException(RateLimitExceededException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ex.getMessage(),
                System.currentTimeMillis()
        );
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getWaitTimeSeconds()))
                .body(errorResponse);
    }
} 