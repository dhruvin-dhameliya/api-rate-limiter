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
 * The annotation says limit=1000, but the properties override with limit=2.
 * If the limit trips at request 3, properties won. If it took 1001 requests,
 * properties would have been ignored.
 */
@SpringBootTest(classes = { IntegrationBootstrap.class, EndpointPrecedenceTest.LimitedController.class })
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limiter.endpoints[api-precedence-test].limit=2",
        "rate-limiter.endpoints[api-precedence-test].time-window-seconds=60"
})
class EndpointPrecedenceTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void propertiesOverrideAnnotation() throws Exception {
        String ip = "10.0.0.50";
        mvc.perform(get("/api/precedence-test").with(remoteAddr(ip))).andExpect(status().isOk());
        mvc.perform(get("/api/precedence-test").with(remoteAddr(ip))).andExpect(status().isOk());
        mvc.perform(get("/api/precedence-test").with(remoteAddr(ip))).andExpect(status().isTooManyRequests());
    }

    @RestController
    public static class LimitedController {
        @GetMapping("/api/precedence-test")
        @RateLimit(limit = 1000, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
        public String hit() {
            return "ok";
        }
    }
}
