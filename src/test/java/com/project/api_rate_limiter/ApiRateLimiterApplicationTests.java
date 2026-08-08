package com.project.api_rate_limiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

/**
 * Boots the full Spring Boot auto-configuration chain — the same path a
 * consumer app uses — so our library's beans wire up against real Spring MVC
 * and Boot infrastructure. The nested {@link TestBootstrap} exists only to
 * give {@code @SpringBootTest} an {@code @EnableAutoConfiguration} anchor;
 * it keeps test scaffolding out of the published jar.
 */
@SpringBootTest(classes = ApiRateLimiterApplicationTests.TestBootstrap.class)
class ApiRateLimiterApplicationTests {

    @Test
    void contextLoads() {
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestBootstrap {
    }
}
