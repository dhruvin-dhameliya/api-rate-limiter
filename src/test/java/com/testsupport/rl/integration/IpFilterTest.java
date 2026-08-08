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

import static com.testsupport.rl.integration.MockMvcSupport.remoteAddr;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IP allow/deny happens before the rate limiter runs, so a whitelisted IP
 * should never see a 429 no matter how many requests it makes, and a
 * blacklisted IP gets a 403 immediately.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, IpFilterTest.LimitedController.class })
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiter.whitelisted-ips=9.9.9.9",
        "rate-limiter.blacklisted-ips=1.2.3.4"
})
class IpFilterTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void blacklistedIpIsRejectedWith403() throws Exception {
        mvc.perform(get("/api/ip-test").with(remoteAddr("1.2.3.4")))
                .andExpect(status().isForbidden());
    }

    @Test
    void whitelistedIpBypassesRateLimit() throws Exception {
        // Limit is 2; a whitelisted IP should be able to blow past it repeatedly.
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/ip-test").with(remoteAddr("9.9.9.9")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void neutralIpStillHitsLimit() throws Exception {
        String ip = "5.5.5.5";
        mvc.perform(get("/api/ip-test").with(remoteAddr(ip))).andExpect(status().isOk());
        mvc.perform(get("/api/ip-test").with(remoteAddr(ip))).andExpect(status().isOk());
        mvc.perform(get("/api/ip-test").with(remoteAddr(ip))).andExpect(status().isTooManyRequests());
    }

    @RestController
    public static class LimitedController {
        @GetMapping("/api/ip-test")
        @RateLimit(limit = 2, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
        public String hit() {
            return "ok";
        }
    }
}
