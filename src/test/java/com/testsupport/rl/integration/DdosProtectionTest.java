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
 * DDoS protection is a separate, IP-scoped counter that runs before the
 * per-endpoint limiter. Once an IP crosses the threshold it is banned across
 * every endpoint — so hitting a second endpoint after the ban should also 429.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, DdosProtectionTest.TwoEndpointsController.class })
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiter.ddos-protection-enabled=true",
        "rate-limiter.ddos-threshold=3",
        "rate-limiter.ddos-ban-duration-seconds=60"
})
class DdosProtectionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void banTripsAtThresholdAndAppliesAcrossEndpoints() throws Exception {
        String ip = "10.0.0.60";

        // 3 requests within threshold — all admitted.
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/ddos-a").with(remoteAddr(ip))).andExpect(status().isOk());
        }
        // 4th trips the DDoS counter.
        mvc.perform(get("/api/ddos-a").with(remoteAddr(ip))).andExpect(status().isTooManyRequests());
        // Different endpoint, same IP — the ban should still apply.
        mvc.perform(get("/api/ddos-b").with(remoteAddr(ip))).andExpect(status().isTooManyRequests());
    }

    @Test
    void differentIpsAreNotBannedBySomeoneElsesTraffic() throws Exception {
        // Attacker fills their bucket right up to (but not past) threshold=3.
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/ddos-a").with(remoteAddr("10.0.0.61"))).andExpect(status().isOk());
        }
        // A fresh IP is unaffected by the neighbour's traffic.
        mvc.perform(get("/api/ddos-a").with(remoteAddr("10.0.0.62"))).andExpect(status().isOk());
    }

    @RestController
    public static class TwoEndpointsController {
        @GetMapping("/api/ddos-a")
        @RateLimit(limit = 1000, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
        public String a() {
            return "a";
        }

        @GetMapping("/api/ddos-b")
        @RateLimit(limit = 1000, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
        public String b() {
            return "b";
        }
    }
}
