package com.project.api_rate_limiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String value() default "";
    int limit() default 0;
    int timeWindowSeconds() default 0;
    String key() default "";
} 