package com.project.api_rate_limiter.exception;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.project.api_rate_limiter.filter.RateLimitFilter;
import com.project.api_rate_limiter.model.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback advice for exceptions that escape {@link RateLimitFilter} — e.g. a
 * rate-limit thrown from the aspect on a service bean, or an uncaught error
 * from a controller.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", String.valueOf(ex.getWaitTimeSeconds()));
        headers.set("X-RateLimit-Limit", String.valueOf(ex.getLimit()));
        headers.set("X-RateLimit-Remaining", String.valueOf(ex.getRemaining()));
        return build(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage(), request, headers);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        // Log at error so operators see stack traces for otherwise-silent 500s.
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage(), request, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message,
                                                HttpServletRequest request, HttpHeaders extraHeaders) {
        String traceId = resolveTraceId(request);
        ErrorResponse body = new ErrorResponse(status.value(), error, message, request.getRequestURI());
        body.setTraceId(traceId);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Trace-ID", traceId);
        if (extraHeaders != null) {
            extraHeaders.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        }
        return builder.body(body);
    }

    /** Reuse the filter's trace id when available so logs and headers agree across layers. */
    private String resolveTraceId(HttpServletRequest request) {
        Object attr = request.getAttribute(RateLimitFilter.TRACE_ID_ATTRIBUTE);
        return attr instanceof String s ? s : UUID.randomUUID().toString();
    }
}
