package com.testsupport.rl.integration;

import com.project.api_rate_limiter.annotation.RateLimit;
import com.project.api_rate_limiter.annotation.RateLimitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static com.testsupport.rl.integration.MockMvcSupport.remoteAddr;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With {@code @RateLimit(methods = {"POST"})}, only POSTs are enforced. GETs
 * hit the same handler but bypass the counter entirely.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, MethodsGateTest.PostOnlyLimitedController.class })
@AutoConfigureMockMvc
class MethodsGateTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void getsUnlimited_postsCounted() throws Exception {
        String ip = "10.0.0.40";

        // Many GETs — none of these should touch the counter.
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/methods-test").with(remoteAddr(ip))).andExpect(status().isOk());
        }

        // Limit is 1 POST; first passes, second trips.
        mvc.perform(post("/api/methods-test").with(remoteAddr(ip))).andExpect(status().isOk());
        mvc.perform(post("/api/methods-test").with(remoteAddr(ip))).andExpect(status().isTooManyRequests());

        // GETs still work even after POSTs are limited.
        mvc.perform(get("/api/methods-test").with(remoteAddr(ip))).andExpect(status().isOk());
    }

    @RestController
    public static class PostOnlyLimitedController {
        @RequestMapping(value = "/api/methods-test", method = { RequestMethod.GET, RequestMethod.POST })
        @RateLimit(limit = 1, timeWindowSeconds = 60, methods = { "POST" }, type = RateLimitType.IP_BASED)
        public String hit() {
            return "ok";
        }
    }
}
