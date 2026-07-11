# Implementation Plan: API Gateway Microservice (Spring Cloud Gateway)

This plan outlines the architecture, routing rules, security configuration, and cross-cutting responsibilities for a centralized **API Gateway** microservice. It is designed to work in tandem with the `auth-service` using Spring Cloud Gateway (Reactive WebFlux) and Netflix Eureka for service discovery.

---

## 1. Gateway Responsibilities vs. Auth Service

To maintain a clean microservice architecture, we divide operations between the Gateway and the Auth Service:

| Capability                    | Handled By   | Implementation Details                                                                       |
|:------------------------------|:-------------|:---------------------------------------------------------------------------------------------|
| **API Routing**               | Gateway      | Dynamically routes external requests to downstream services (via Eureka/Service Discovery).  |
| **CORS Configuration**        | Gateway      | Centrally configured at the gateway perimeter to avoid downstream duplicate CORS filters.    |
| **Rate Limiting**             | Gateway      | Redis-backed Token Bucket filter blocks DoS/Brute Force attacks at the edge.                 |
| **JWT Signature Validation**  | Gateway      | Validates access token signatures and expiration before routing.                             |
| **Token Blacklist Check**     | Gateway      | Checks shared Redis instance for blacklisted `blacklist:<jti>` keys (invalidated by logout). |
| **Credential Authentication** | Auth Service | Verifies BCrypt credentials (passwords, social accounts) and issues JWTs.                    |
| **OAuth2 Provisioning**       | Auth Service | Connects to providers, provisions users, and links accounts in the database.                 |
| **Session & Token Database**  | Auth Service | Manages stateful refresh token rotation and database storage.                                |

---

## 2. API Gateway Tech Stack

* **Framework:** Spring Cloud Gateway (built on Spring WebFlux for non-blocking reactive routing).
* **Service Discovery:** Netflix Eureka Client (for routing via service names instead of static IPs, e.g., `lb://auth-service`).
* **Caching & Rate Limiting:** Spring Data Reactive Redis.
* **Resiliency & Fault Tolerance:** Spring Cloud CircuitBreaker (Resilience4j).
* **Security:** Spring Cloud Starter Security (Reactive WebFlux Security).

---

## 3. Configuration: Routing and CORS

The gateway's `application.yaml` configures routes and applies global CORS parameters. In Spring Cloud Gateway (WebFlux Server), properties must be bound under the `spring.cloud.gateway.server.webflux` namespace:

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway-service
  
  # Redis configuration for Rate Limiting & Blacklist Check
  data:
    redis:
      host: localhost
      port: 6379

  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
      server:
        webflux:
          default-filters:
            - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
          globalcors:
            cors-configurations:
              '[/**]':
                allowedOriginPatterns: "*"
                allowedMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
                allowedHeaders: "*"
                allowCredentials: true
          
          routes:
            # 1. Route to Auth Service (Public, Admin and Session endpoints)
            - id: auth-service
              uri: lb://auth-service
              predicates:
                - Path=/api/v1/auth/**,/api/v1/admin/**,/api/v1/sessions/**
              filters:
                # Edge Rate Limiting (throw exception on limit to capture in handler)
                - name: RequestRateLimiter
                  args:
                    redis-rate-limiter.replenishRate: 10
                    redis-rate-limiter.burstCapacity: 20
                    key-resolver: "#{@ipKeyResolver}"
                    throw-on-limit: true
                # Downstream protection via Circuit Breaker
                - name: CircuitBreaker
                  args:
                    name: authServiceCircuitBreaker
                    fallbackUri: forward:/fallback/auth
                
            # 2. Route to User Service (Hosted under auth-service, requires validation, retry and rate limiting)
            - id: user-service
              uri: lb://auth-service
              predicates:
                - Path=/api/v1/users/**
              filters:
                - JwtValidationFilter
                - name: RequestRateLimiter
                  args:
                    redis-rate-limiter.replenishRate: 10
                    redis-rate-limiter.burstCapacity: 20
                    key-resolver: "#{@ipKeyResolver}"
                    throw-on-limit: true
                # Safe GET Request Retry
                - name: Retry
                  args:
                    retries: 3
                    methods: GET
                    series: SERVER_ERROR
                    exceptions: java.io.IOException, java.util.concurrent.TimeoutException
                - name: CircuitBreaker
                  args:
                    name: userServiceCircuitBreaker
                    fallbackUri: forward:/fallback/user
```

---

## 4. Centralized Security Logic (JwtValidationFilter)

Protected downstream routes (like `user-service`) rely on the gateway to authenticate requests. We implement a custom reactive Gateway Filter `JwtValidationFilter` extending `AbstractGatewayFilterFactory`. To keep error handling clean, it throws `ResponseStatusException` rather than manually formatting JSON response bodies:

```java
@Component
public class JwtValidationFilter extends AbstractGatewayFilterFactory<JwtValidationFilter.Config> {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    public JwtValidationFilter(JwtUtil jwtUtil, ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // 1. Extract Header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header"));
            }
            
            String token = authHeader.substring(7);
            
            // 2. Validate JWT & Redis Blacklist
            try {
                if (!jwtUtil.validateToken(token)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired JWT access token"));
                }
                
                String jti = jwtUtil.getJti(token);
                
                // Check if JTI is in Redis blacklist (blacklist:<jti>)
                return redisTemplate.hasKey("blacklist:" + jti)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is blacklisted (logged out)"));
                        }
                        
                        // 3. Inject Downstream Headers (Token Relay)
                        ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-User-Id", jwtUtil.getUserId(token).toString())
                            .header("X-User-Email", jwtUtil.getEmail(token))
                            .header("X-User-Roles", String.join(",", jwtUtil.getRoles(token)))
                            .build();
                            
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });
            } catch (Exception e) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT validation failed: " + e.getMessage()));
            }
        };
    }

    public static class Config {}
}
```

---

## 5. Rate Limiting at the Edge

To prevent brute force and resource exhaustion, the Gateway uses Redis to implement a **Token Bucket** algorithm. The remote client IP is resolved using `ipKeyResolver` defined in `RateLimiterConfig`:

```java
@Configuration
public class RateLimiterConfig {
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .defaultIfEmpty("127.0.0.1");
    }
}
```

---

## 6. Centralized WebExceptionHandler

All exceptions thrown by gateway filters (such as authorization, rate limit block, or security access denial exceptions) are captured by a centralized reactive `WebExceptionHandler` to return a standardized JSON structure:

```java
@Component
@Order(-2)
public class GlobalExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorName = "Internal Server Error";
        String message = ex.getMessage();

        if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            status = rse.getStatusCode();
            message = rse.getReason();
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                errorName = "Too Many Requests";
                message = "Rate limit exceeded. Please try again later.";
            } else if (status == HttpStatus.UNAUTHORIZED) {
                errorName = "Unauthorized";
            } else if (status == HttpStatus.FORBIDDEN) {
                errorName = "Forbidden";
            }
        }
        else if (ex instanceof AuthenticationException) {
            status = HttpStatus.UNAUTHORIZED;
            errorName = "Unauthorized";
            message = ex.getMessage();
        }
        else if (ex instanceof AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
            errorName = "Forbidden";
            message = "Access Denied: You do not have permissions to access this resource.";
        }
        else if (ex instanceof org.springframework.web.client.HttpStatusCodeException) {
            org.springframework.web.client.HttpStatusCodeException hsce = (org.springframework.web.client.HttpStatusCodeException) ex;
            status = hsce.getStatusCode();
            message = hsce.getStatusText();
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                errorName = "Too Many Requests";
                message = "Rate limit exceeded. Please try again later.";
            }
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String cleanMessage = message != null ? message.replace("\"", "\\\"") : "";

        String json = String.format("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\",\"timestamp\":\"%s\"}",
                status.value(),
                errorName,
                cleanMessage,
                exchange.getRequest().getPath().value(),
                Instant.now().toString());

        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
```

---

## 7. Action Plan / Roadmap

### Phase 1: Core Setup
* [x] Add Eureka client and Redis dependencies to `gateway-service/pom.xml`.
* [x] Set up `application.yml` with Eureka server registries and Redis connections.
* [x] Enable `eureka` configuration on `auth-service` (uncomment properties and add dependencies).

### Phase 2: Security & Resiliency Implementation
* [x] Implement `JwtUtil` class in the `gateway-service` package structure.
* [x] Implement `JwtValidationFilter` component to validate signature, verify JTI blacklist, and inject downstream headers.
* [x] Configure Reactive Spring Security to permit all public traffic and let route filters manage protected paths.
* [x] Declare `ipKeyResolver` bean in `RateLimiterConfig` and configure `RequestRateLimiter` filter with `throw-on-limit: true`.
* [x] Integrate Resilience4j `CircuitBreaker` and safe `GET` requests `Retry` filters on YAML routes.
* [x] Implement `FallbackController` and `GlobalExceptionHandler` to centralize and standardize HTTP error payloads.

### Phase 3: Production Hardening (Pending)
* [ ] Verify container configurations and metrics collections.
* [ ] Spin up Docker Compose instances locally for staging verification.
