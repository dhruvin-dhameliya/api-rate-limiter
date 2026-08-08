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
 * Trusted-proxies is configured to something other than the request's
 * remoteAddr (127.0.0.1 in MockMvc). Proxy headers must be ignored — otherwise
 * any client could spoof {@code X-Forwarded-For} to dodge per-IP limits.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, TrustedProxiesUntrustedTest.LimitedController.class })
@AutoConfigureMockMvc
@TestPropertySource(properties = "rate-limiter.trusted-proxies=8.8.8.8")
class TrustedProxiesUntrustedTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void spoofedXForwardedForIgnoredWhenProxyIsNotTrusted() throws Exception {
        // Each request advertises a different X-Forwarded-For. If we honoured
        // them, each would land in its own bucket and none would 429. Because
        // the immediate connection (127.0.0.1) isn't in the trusted list, the
        // headers must be ignored — so all three requests bucket to 127.0.0.1
        // and the third trips the limit.
        mvc.perform(get("/api/proxy-untrusted").header("X-Forwarded-For", "1.1.1.1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proxy-untrusted").header("X-Forwarded-For", "2.2.2.2"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proxy-untrusted").header("X-Forwarded-For", "3.3.3.3"))
                .andExpect(status().isTooManyRequests());
    }

    @RestController
    public static class LimitedController {
        @GetMapping("/api/proxy-untrusted")
        @RateLimit(limit = 2, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
        public String hit() {
            return "ok";
        }
    }
}
