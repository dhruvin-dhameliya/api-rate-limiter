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
        
        if (rateLimitAnnotation != null) {
            String endpoint = rateLimitAnnotation.value().isEmpty() 
                    ? method.getName() 
                    : rateLimitAnnotation.value();
            String clientId = getClientId(joinPoint, rateLimitAnnotation, signature);
            
            try {
                rateLimiterService.allowRequest(clientId, endpoint, 
                    rateLimitAnnotation.limit(), 
                    rateLimitAnnotation.timeWindowSeconds());
            } catch (RateLimitExceededException e) {
                log.warn("Rate limit exceeded for client {} on endpoint {}: {}", 
                        clientId, endpoint, e.getMessage());
                throw e;
            }
        }

        return joinPoint.proceed();
    }

    private String getClientId(ProceedingJoinPoint joinPoint, RateLimit annotation, MethodSignature signature) {
        if (!annotation.key().isEmpty()) {
            try {
                String key = annotation.key();
                Expression expression = parser.parseExpression(key);

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
        return getClientIp();
    }
    
    // Get the client IP address from the request
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // get the real IP if behind a proxy
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("HTTP_CLIENT_IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("HTTP_X_FORWARDED_FOR");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.error("Error getting client IP: {}", e.getMessage());
        }
        return "unknown";
    }
} 