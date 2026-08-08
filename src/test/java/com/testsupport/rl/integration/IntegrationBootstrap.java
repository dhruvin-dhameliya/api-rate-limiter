package com.testsupport.rl.integration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal test bootstrap: boots Spring Boot auto-configuration only. Tests add
 * their own controllers/services via {@code @SpringBootTest(classes = { ... })}
 * to keep bean sets scoped to each scenario.
 * <p>
 * Spring Security is on the test classpath (optional dep in the library) but
 * excluded here so its default {@code permitAll = false} chain does not
 * intercept every test request with a 401.
 * <p>
 * Lives outside {@code com.project.api_rate_limiter} on purpose — the library's
 * auto-config scans that package, so test-only components stay elsewhere to
 * avoid bleeding into unrelated contexts.
 */
@Configuration
@EnableAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
public class IntegrationBootstrap {
}
