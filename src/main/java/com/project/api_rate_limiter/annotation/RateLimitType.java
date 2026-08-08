package com.project.api_rate_limiter.annotation;

/** Identifier the rate limiter uses to build a bucket — i.e. who shares a counter with whom. */
public enum RateLimitType {

    /** Single shared counter across all callers of the endpoint. */
    GLOBAL,

    /** Bucket per client IP (respects the trusted-proxies config). */
    IP_BASED,

    /** Bucket per authenticated principal; unauthenticated calls are not counted. */
    USER_BASED,

    /** Bucket per API key from a configured header; missing or invalid keys → 401. */
    API_KEY_BASED,

    /** Bucket per HTTP method on the endpoint (GET and POST count separately). */
    METHOD_BASED,

    /** Single counter per endpoint, independent of caller identity. */
    ENDPOINT_BASED
}
