# Spring Boot API Rate Limiter Library

A comprehensive Spring Boot rate limiting library with security features.

## Features
- **Default rate limiting** (global configuration)
- **Endpoint-specific rate limiting**
- **IP-based rate limiting**
- **User-based rate limiting**
- **API key-based rate limiting**
- **Support for different HTTP methods** (GET, POST, PUT, DELETE)
- **DDoS protection**

## Installation

Add the dependency to your Maven project:

```xml
<dependency>
    <groupId>com.project</groupId>
    <artifactId>api-rate-limiter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Configuration

Configure the rate limiter in your `application.properties` (or `application.yml`) file:

```properties
# Enable/disable rate limiting globally
rate-limiter.enabled=true

# Default rate limit for all endpoints
rate-limiter.default-limit=100
rate-limiter.default-time-window-seconds=60

# Enable Redis for distributed rate limiting (optional)
rate-limiter.enable-redis=false

# DDoS protection settings
rate-limiter.ddos-protection-enabled=true
rate-limiter.ddos-threshold=1000
rate-limiter.ddos-ban-duration-seconds=3600
rate-limiter.ddos-count-reset-interval-seconds=60

# User-based rate limiting settings
rate-limiter.user-based-limiting-enabled=true
rate-limiter.default-user-limit=50
rate-limiter.default-user-time-window-seconds=60

# API key-based rate limiting settings
rate-limiter.api-key-based-limiting-enabled=true
rate-limiter.default-api-key-limit=200
rate-limiter.default-api-key-time-window-seconds=60

# Endpoint-specific rate limits
rate-limiter.endpoints.login.limit=10
rate-limiter.endpoints.login.time-window-seconds=120
rate-limiter.endpoints.login.enabled=true

# Method-specific rate limits for an endpoint
rate-limiter.endpoints.api-data.method-limits.POST=20
rate-limiter.endpoints.api-data.method-limits.PUT=30
rate-limiter.endpoints.api-data.method-limits.DELETE=10

# User-specific rate limits for an endpoint
rate-limiter.endpoints.private-data.user-limit=20
rate-limiter.endpoints.private-data.user-time-window-seconds=60

# API key specific rate limits for an endpoint
rate-limiter.endpoints.api-service.api-key-limit=100
rate-limiter.endpoints.api-service.api-key-time-window-seconds=60
```

## Usage

### Basic Rate Limiting

The library applies rate limiting automatically to all endpoints once added to your project. You can customize the behavior using the `@RateLimit` annotation.

### Using the @RateLimit Annotation

Apply rate limiting to a specific controller or method:

```java
import com.project.api_rate_limiter.annotation.RateLimit;
import com.project.api_rate_limiter.annotation.RateLimitType;

@RestController
@RequestMapping("/api")
public class ApiController {

    // Apply rate limiting to a specific endpoint
    @GetMapping("/data")
    @RateLimit(limit = 50, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
    public ResponseEntity<Object> getData() {
        // Your code here
        return ResponseEntity.ok().build();
    }
    
    // Apply rate limiting to a specific HTTP method
    @PostMapping("/data")
    @RateLimit(limit = 20, timeWindowSeconds = 60, methods = {"POST"}, 
              type = RateLimitType.METHOD_BASED)
    public ResponseEntity<Object> postData() {
        // Your code here
        return ResponseEntity.ok().build();
    }
    
    // Apply user-based rate limiting
    @GetMapping("/user-data")
    @RateLimit(limit = 30, timeWindowSeconds = 60, type = RateLimitType.USER_BASED)
    public ResponseEntity<Object> getUserData() {
        // Your code here
        return ResponseEntity.ok().build();
    }
    
    // Apply API key-based rate limiting
    @GetMapping("/service")
    @RateLimit(limit = 100, timeWindowSeconds = 60, type = RateLimitType.API_KEY_BASED)
    public ResponseEntity<Object> getService() {
        // Your code here
        return ResponseEntity.ok().build();
    }
    
    // Apply DDoS protection
    @PostMapping("/login")
    @RateLimit(limit = 5, timeWindowSeconds = 60, type = RateLimitType.IP_BASED, 
              ddosProtection = true, ddosThreshold = 20, ddosBanDurationSeconds = 1800)
    public ResponseEntity<Object> login() {
        // Your code here
        return ResponseEntity.ok().build();
    }
}
```

You can also apply rate limiting to an entire controller:

```java
@RestController
@RequestMapping("/api")
@RateLimit(limit = 100, timeWindowSeconds = 60, type = RateLimitType.IP_BASED)
public class ApiController {
    // All methods in this controller will be rate-limited
}
```

### Rate Limit Types

The library supports various types of rate limiting:

- `RateLimitType.GLOBAL` - Global rate limiting for all requests
- `RateLimitType.IP_BASED` - Rate limiting based on client IP address
- `RateLimitType.USER_BASED` - Rate limiting based on authenticated user
- `RateLimitType.API_KEY_BASED` - Rate limiting based on API key
- `RateLimitType.METHOD_BASED` - Rate limiting specific to HTTP method
- `RateLimitType.ENDPOINT_BASED` - Endpoint-specific rate limiting

### API Key Management

To use API key-based rate limiting:

```java
@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;
    
    @PostMapping("/generate")
    public ResponseEntity<ApiKey> generateApiKey(@RequestParam String owner, 
                                               @RequestParam int rateLimit,
                                               @RequestParam int timeWindowSeconds,
                                               @RequestParam int expiryDays) {
        ApiKey apiKey = apiKeyService.generateApiKey(owner, rateLimit, timeWindowSeconds, expiryDays);
        return ResponseEntity.ok(apiKey);
    }
    
    @PostMapping("/revoke")
    public ResponseEntity<Boolean> revokeApiKey(@RequestParam String key) {
        boolean revoked = apiKeyService.revokeApiKey(key);
        return ResponseEntity.ok(revoked);
    }
}
```

When using API keys, clients should include the API key in the `X-API-Key` header.

### DDoS Protection

The library includes DDoS protection that can be enabled globally or for specific endpoints. When a client exceeds the DDoS threshold, they will be temporarily banned for the specified duration.

## Environment Variables

All configuration properties can also be set via environment variables:

- `RATE_LIMITER_ENABLED` - Enable/disable rate limiting globally
- `RATE_LIMITER_DEFAULT_LIMIT` - Default rate limit for all endpoints
- `RATE_LIMITER_DEFAULT_TIME_WINDOW_SECONDS` - Default time window in seconds
- `RATE_LIMITER_ENABLE_REDIS` - Enable Redis for distributed rate limiting
- `RATE_LIMITER_DDOS_PROTECTION_ENABLED` - Enable DDoS protection
- `RATE_LIMITER_DDOS_THRESHOLD` - DDoS threshold
- `RATE_LIMITER_DDOS_BAN_DURATION_SECONDS` - DDoS ban duration
- `RATE_LIMITER_ENDPOINTS_<ENDPOINT>_LIMIT` - Endpoint-specific rate limit
- `RATE_LIMITER_ENDPOINTS_<ENDPOINT>_TIME_WINDOW_SECONDS` - Endpoint-specific time window
- `RATE_LIMITER_ENDPOINTS_<ENDPOINT>_ENABLED` - Enable/disable rate limiting for specific endpoint

## Distributed Rate Limiting with Redis

To enable distributed rate limiting with Redis, set `rate-limiter.enable-redis=true` and ensure Redis is properly configured in your Spring Boot application.

## Exception Handling

The library throws a `RateLimitExceededException` when a client exceeds the rate limit. This exception is automatically handled by the filter, which returns a 429 (Too Many Requests) response with a "Retry-After" header.

## License

[MIT License](LICENSE) 