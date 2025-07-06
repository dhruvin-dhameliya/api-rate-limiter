package com.project.api_rate_limiter.exception;

import com.project.api_rate_limiter.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.UUID;

    @ControllerAdvice
    public class GlobalExceptionHandler {

        @ExceptionHandler(RateLimitExceededException.class)
        public ResponseEntity<ErrorResponse> handleRateLimitExceededException(RateLimitExceededException ex, HttpServletRequest request) {
            String traceId = UUID.randomUUID().toString();
            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too Many Requests",
                    ex.getMessage(),
                    request.getRequestURI()
            );

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(ex.getWaitTimeSeconds()))
                    .header("X-Trace-ID", traceId)
                    .body(errorResponse);
        }

        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex, HttpServletRequest request) {
            String traceId = UUID.randomUUID().toString();
            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    ex.getMessage(),
                    request.getRequestURI()
            );

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("X-Trace-ID", traceId)
                    .body(errorResponse);
        }

        @ExceptionHandler(NoHandlerFoundException.class)
        public ResponseEntity<ErrorResponse> handleNotFoundException(NoHandlerFoundException ex, HttpServletRequest request) {
            String traceId = UUID.randomUUID().toString();
            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.NOT_FOUND.value(),
                    "Not Found",
                    ex.getMessage(),
                    request.getRequestURI()
            );

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Trace-ID", traceId)
                    .body(errorResponse);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
            String traceId = UUID.randomUUID().toString();
            ErrorResponse errorResponse = new ErrorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Internal Server Error",
                    ex.getMessage(),
                    request.getRequestURI()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Trace-ID", traceId)
                    .body(errorResponse);
        }
    }