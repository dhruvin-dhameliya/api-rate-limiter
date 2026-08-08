package com.testsupport.rl.integration;

import com.project.api_rate_limiter.annotation.RateLimit;
import com.project.api_rate_limiter.annotation.RateLimitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mirror of {@link TrustedProxiesUntrustedTest} but with the request's
 * remoteAddr (127.0.0.1) in the trusted list — the proxy header IS honoured
 * here, so each unique {@code X-Forwarded-For} value becomes its own bucket.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, TrustedProxiesTrustedTest.LimitedController.class })
@AutoConfigureMockMvc
@TestPropertySource(properties = "rate-limiter.trusted-proxies=127.0.0.1")
class TrustedProxiesTrustedTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void distinctXForwardedForValuesGetIndependentBuckets() throws Exception {
        // Limit is 1. Under a naive "always bucket by remoteAddr" implementation,
        // the second request here would 429. It should pass because the header
        // is trusted and the two requests advertise different upstream clients.
        mvc.perform(get("/api/proxy-trusted").header("X-Forwarded-For", "4.4.4.4"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proxy-trusted").header("X-Forwarded-For", "5.5.5.5"))
                .andExpect(status().isOk());
        // Same header value again — this one shares a bucket with the first request and trips.
        mvc.perform(get("/api/proxy-trusted").header("X-Forwarded-For", "4.4.4.4"))
                .andExpect(status().isTooManyRequests());
    }

    @RestController
    public static class LimitedController {
        @GetMapping("/api/proxy-trusted")
        @RateLimit(limit = 1, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
        public String hit() {
            return "ok";
        }
    }
}
