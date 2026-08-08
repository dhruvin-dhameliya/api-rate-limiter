package com.project.api_rate_limiter.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.project.api_rate_limiter.annotation.RateLimit;
import com.project.api_rate_limiter.exception.RateLimitExceededException;
import com.project.api_rate_limiter.filter.RateLimitFilter;
import com.project.api_rate_limiter.service.RateLimiterService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.project.api_rate_limiter.annotation.RateLimit) || @within(com.project.api_rate_limiter.annotation.RateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        RateLimit rateLimitAnnotation = method.getAnnotation(RateLimit.class);
        if (rateLimitAnnotation == null) {
            rateLimitAnnotation = method.getDeclaringClass().getAnnotation(RateLimit.class);
        }

        if (rateLimitAnnotation == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = currentRequest();

        // Mirror the filter's methods() check so HTTP methods excluded by
        // @RateLimit(methods = ...) are not enforced here either.
        if (request != null
                && rateLimitAnnotation.methods().length > 0
                && !rateLimiterService.isMethodAllowed(request, rateLimitAnnotation.methods())) {
            return joinPoint.proceed();
        }

        // Filter is the single source of truth for controller methods;
        // the aspect still fires for service-bean methods called outside a request.
        if (request != null && Boolean.TRUE.equals(request.getAttribute(RateLimitFilter.ENFORCED_ATTRIBUTE))) {
            return joinPoint.proceed();
        }

        String endpoint = rateLimitAnnotation.value().isEmpty()
                ? method.getName()
                : rateLimitAnnotation.value();
        String clientId = resolveClientId(joinPoint, rateLimitAnnotation, signature, request);

        try {
            rateLimiterService.allowRequestWithType(
                    clientId,
                    endpoint,
                    rateLimitAnnotation.limit(),
                    rateLimitAnnotation.timeWindowSeconds(),
                    rateLimitAnnotation.type(),
                    request);
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for client {} on endpoint {}: {}",
                    clientId, endpoint, e.getMessage());
            throw e;
        }

        return joinPoint.proceed();
    }

    /** SpEL key when the annotation supplies one; otherwise the client IP. */
    private String resolveClientId(ProceedingJoinPoint joinPoint,
                                   RateLimit annotation,
                                   MethodSignature signature,
                                   HttpServletRequest request) {
        if (!annotation.key().isEmpty()) {
            try {
                Expression expression = parser.parseExpression(annotation.key());
                StandardEvaluationContext context = new StandardEvaluationContext();
                String[] paramNames = signature.getParameterNames();
                Object[] args = joinPoint.getArgs();
                if (paramNames != null) {
                    for (int i = 0; i < paramNames.length; i++) {
                        context.setVariable(paramNames[i], args[i]);
                    }
                }
                return expression.getValue(context, String.class);
            } catch (Exception e) {
                log.error("Error evaluating rate limit key: {}", e.getMessage());
            }
        }
        return request != null ? RateLimitFilter.extractClientIp(request) : "unknown";
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
