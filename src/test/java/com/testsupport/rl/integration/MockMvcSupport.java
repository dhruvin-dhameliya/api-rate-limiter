package com.testsupport.rl.integration;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Small helpers to avoid repeating boilerplate in MockMvc test bodies.
 */
final class MockMvcSupport {

    private MockMvcSupport() {}

    /** Overrides the request's remote address — lets tests use unique IPs to isolate rate-limit buckets. */
    static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
