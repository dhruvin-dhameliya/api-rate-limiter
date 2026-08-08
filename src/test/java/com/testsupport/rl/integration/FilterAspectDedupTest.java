package com.testsupport.rl.integration;

import com.project.api_rate_limiter.annotation.RateLimit;
import com.project.api_rate_limiter.annotation.RateLimitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.testsupport.rl.integration.MockMvcSupport.remoteAddr;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The filter and aspect both target {@code @RateLimit}. If they both counted
 * the same request, a limit of 2 would trip at request 2, not 3. This test
 * pins down the dedup: exactly the configured number of requests pass.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, FilterAspectDedupTest.LimitedController.class })
@AutoConfigureMockMvc
class FilterAspectDedupTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void controllerRequestsCountedOnceNotTwice() throws Exception {
        String ip = "10.0.0.30";

        mvc.perform(get("/api/dedup-test").with(remoteAddr(ip))).andExpect(status().isOk());
        mvc.perform(get("/api/dedup-test").with(remoteAddr(ip))).andExpect(status().isOk());
        // If dedup were broken, this would have been the 4th "count" and would 429.
        // We expect the 3rd HTTP request to be the one that trips the limit.
        mvc.perform(get("/api/dedup-test").with(remoteAddr(ip))).andExpect(status().isTooManyRequests());
    }

    @RestController
    public static class LimitedController {
        @GetMapping("/api/dedup-test")
        @RateLimit(limit = 2, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
        public String hit() {
            return "ok";
        }
    }
}
