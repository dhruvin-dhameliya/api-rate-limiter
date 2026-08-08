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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full HTTP-path verification: the filter admits N requests and rejects the
 * next one with the expected status code, headers, and JSON error body.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, FilterEndToEndTest.LimitedController.class })
@AutoConfigureMockMvc
class FilterEndToEndTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void filterAdmitsThenRejectsWithHeadersAndJsonBody() throws Exception {
        String ip = "10.0.0.10";

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/filter-test").with(remoteAddr(ip)))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/api/filter-test").with(remoteAddr(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists("X-Trace-ID"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/filter-test"))
                .andExpect(jsonPath("$.traceId", notNullValue()));
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        // Fill one bucket, then hit from a different IP and confirm it is unaffected.
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/filter-test").with(remoteAddr("10.0.0.20")))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/filter-test").with(remoteAddr("10.0.0.21")))
                .andExpect(status().isOk());
    }

    @RestController
    public static class LimitedController {
        @GetMapping("/api/filter-test")
        @RateLimit(limit = 3, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
        public String hit() {
            return "ok";
        }
    }
}
