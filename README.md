# API Rate Limiter for Microservices

A dynamic and scalable rate limiting library for Spring Boot applications, designed to be easily integrated into microservices architectures. This library provides flexible configuration options and can be customized per API endpoint or for global API limits.

## Features

- **Dynamic Rate Limiting per API Endpoint**: Define different rate limits for various endpoints with easy configuration.
- **Multiple Algorithms**: Choose between Token Bucket and Sliding Window algorithms.
- **Granular Time Intervals**: Control API usage with configurable time windows.
- **Distributed Rate Limiting**: Optional Redis support for distributed environments.
- **Intelligent Failure Handling**: Custom retry mechanisms with helpful error messages.
- **Annotation-based Limiting**: Simple `@RateLimit` annotation for method-level control.
- **Global Filter Option**: Enable rate limiting for all endpoints with a servlet filter.

## Getting Started

### Prerequisites

- Java 17 or higher
- Spring Boot 3.x
- Maven or Gradle
- Redis (optional, for distributed rate limiting)

### Installation

Add the dependency to your project:

```xml
<dependency>
    <groupId>com.project</groupId>
    <artifactId>api-rate-limiter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Basic Configuration

Add the following to your `application.properties` or `application.yml`:

```properties
# Enable/Disable Rate Limiting
rate-limiter.enabled=true

# Default Algorithm (TOKEN_BUCKET or SLIDING_WINDOW)
rate-limiter.default-algorithm=TOKEN_BUCKET

# Default Rate Limit (requests per time window)
rate-limiter.default-limit=100

# Default Time Window (in seconds)
rate-limiter.default-time-window-seconds=60

# Enable Redis for distributed rate limiting
rate-limiter.enable-redis=false
```

### Endpoint-Specific Configuration

```properties
# Configuration for /login endpoint
rate-limiter.endpoints.login.limit=5
rate-limiter.endpoints.login.time-window-seconds=60
rate-limiter.endpoints.login.algorithm=SLIDING_WINDOW

# Configuration for /search endpoint
rate-limiter.endpoints.search.limit=200
rate-limiter.endpoints.search.time-window-seconds=60
```

## Usage

### Using the Annotation

```java
@RestController
public class UserController {
    
    // Basic usage - uses default configuration
    @RateLimit
    @GetMapping("/users")
    public List<User> getUsers() {
        // ...
    }
    
    // Custom rate limit for this endpoint
    @RateLimit(value = "login", limit = 5, timeWindowSeconds = 60)
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        // ...
    }
    
    // Rate limit based on user ID
    @RateLimit(key = "#userId")
    @GetMapping("/users/{userId}")
    public User getUser(@PathVariable String userId) {
        // ...
    }
}
```

### Using Redis for Distributed Rate Limiting

To enable Redis-based rate limiting, configure Redis and set `rate-limiter.enable-redis=true` in your properties.

```properties
rate-limiter.enable-redis=true
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## Algorithms

### Token Bucket

The Token Bucket algorithm uses a bucket that continuously fills with tokens at a constant rate. Each request consumes one token, and if there are no tokens available, the request is rejected. This algorithm allows for bursts of traffic up to the bucket size.

### Sliding Window

The Sliding Window algorithm tracks requests in a sliding time window. It provides more accurate rate limiting by considering the distribution of requests over time.

## Exception Handling

When a rate limit is exceeded, a `RateLimitExceededException` is thrown with details about the wait time. The exception is handled to return a 429 Too Many Requests status with a Retry-After header.

## License

This project is licensed under the MIT License - see the LICENSE file for details. 