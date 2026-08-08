package com.project.api_rate_limiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class as rate-limited. Controller handlers are enforced
 * by {@code RateLimitFilter}; other Spring beans by {@code RateLimitAspect}.
 * The two coordinate via a request attribute so no request is counted twice.
 * <p>
 * Precedence (highest first): endpoint config in properties → this annotation → library defaults.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Bucket label for the aspect path (ignored by the filter). Empty = use the method name. */
    String value() default "";

    /** Max requests per window. {@code 0} = fall back to endpoint config or global default. */
    int limit() default 0;

    /** Window size in seconds. {@code 0} = fall back to endpoint config or global default. */
    int timeWindowSeconds() default 0;

    /** SpEL over method args to build a custom bucket key (e.g. {@code "#userId"}). Aspect path only; empty = use client IP. */
    String key() default "";

    /** Identifier used to build the bucket — IP, user, API key, method, endpoint, or one global counter. */
    RateLimitType type() default RateLimitType.GLOBAL;

    /** Restrict enforcement to these HTTP methods (e.g. {@code {"POST", "PUT"}}). Empty = all methods. */
    String[] methods() default {};
}
