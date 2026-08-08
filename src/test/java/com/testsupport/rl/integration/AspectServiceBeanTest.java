package com.testsupport.rl.integration;

import com.project.api_rate_limiter.annotation.RateLimit;
import com.project.api_rate_limiter.annotation.RateLimitType;
import com.project.api_rate_limiter.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When a rate-limited bean is called outside an HTTP request (no filter has
 * run), the AOP aspect is the only enforcer. This test calls the service
 * directly to exercise that path.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, AspectServiceBeanTest.LimitedService.class })
class AspectServiceBeanTest {

    @Autowired
    private LimitedService service;

    @Test
    void aspectEnforcesOnDirectServiceCalls() {
        assertEquals("done", service.doWork());
        assertEquals("done", service.doWork());
        assertThrows(RateLimitExceededException.class, () -> service.doWork());
    }

    @Component
    public static class LimitedService {
        @RateLimit(value = "aspect-svc-work", limit = 2, timeWindowSeconds = 60, type = RateLimitType.GLOBAL)
        public String doWork() {
            return "done";
        }
    }
}
