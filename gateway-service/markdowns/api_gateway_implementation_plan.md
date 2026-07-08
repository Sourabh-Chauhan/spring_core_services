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
* **Security:** Spring Cloud Starter Security (Reactive WebFlux Security).

---

## 3. Configuration: Routing and CORS

The gateway's `application.yml` will configure routes and apply global CORS parameters:

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  
  # Redis configuration for Rate Limiting & Blacklist Check
  data:
    redis:
      host: localhost
      port: 6379

  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "http://localhost:3000"
            allowedMethods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
      
      routes:
        # 1. Route to Auth Service (Public and Session endpoints)
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/v1/auth/**, /api/v1/admin/**, /api/v1/sessions/**
          filters:
            # Apply Rate Limiting on authentication endpoints
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@ipKeyResolver}"
            
        # 2. Route to User Service (Requires JWT Validation and Blacklist filter)
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
          filters:
            - JwtValidationFilter
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
                key-resolver: "#{@ipKeyResolver}"

# Eureka client configuration for service discovery
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    fetch-registry: true
    register-with-eureka: true
```

---

## 4. Centralized Security Logic (JwtValidationFilter)

Protected downstream services (like `user-service`) will rely on the gateway to authenticate requests. We will implement a custom reactive Gateway Filter `JwtValidationFilter` that runs before routing protected requests downstream:

1. **Extract Token:** Retrieves the `Authorization: Bearer <token>` header.
2. **Signature Validation:** Checks if the JWT is signature-valid and has not expired.
3. **Blacklist Check:** Queries the shared Redis instance to ensure the token's JTI (`blacklist:<jti>`) is not present in the blacklist. 
   *(Note: This matches the corrected `auth-service` token blacklist strategy which stores `blacklist:<jti>` rather than the entire JWT token string).*
4. **Header Injection:** Extracts claims (e.g. `userId`, `email`, `roles`) and injects them as downstream headers (`X-User-Id`, `X-User-Email`, `X-User-Roles`).
5. **Deny/Pass:** Rejects request with `401 Unauthorized` on check failure; forwards matching headers on success.

```java
@Component
public class JwtValidationFilter extends AbstractGatewayFilterFactory<JwtValidationFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil; // Shared JWT utility
    
    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    public JwtValidationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // 1. Extract Header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }
            
            String token = authHeader.substring(7);
            
            // 2. Validate JWT & Redis Blacklist
            try {
                if (!jwtUtil.validateToken(token)) {
                    return onError(exchange, "Invalid access token", HttpStatus.UNAUTHORIZED);
                }
                
                String jti = jwtUtil.getJti(token);
                
                // Check if JTI is in Redis blacklist (blacklist:<jti>)
                return redisTemplate.hasKey("blacklist:" + jti)
                    .flatMap(isBlacklisted -> {
                        if (isBlacklisted) {
                            return onError(exchange, "Token is blacklisted (logged out)", HttpStatus.UNAUTHORIZED);
                        }
                        
                        // 3. Inject Downstream Headers
                        ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-User-Id", jwtUtil.getUserId(token).toString())
                            .header("X-User-Email", jwtUtil.getEmail(token))
                            .header("X-User-Roles", String.join(",", jwtUtil.getRoles(token)))
                            .build();
                            
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });
            } catch (Exception e) {
                return onError(exchange, "JWT validation failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String body = String.format("{\"error\": \"%s\", \"status\": %d}", err, status.value());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {}
}
```

---

## 5. Rate Limiting at the Edge

To prevent abuse on endpoints (e.g., brute-force attacks on login or spamming user resources), the Gateway uses Redis to implement a **Token Bucket** algorithm.

### Key Resolver config:
```java
@Configuration
public class GatewayConfig {
    
    // Limits rate per client IP Address
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("127.0.0.1")
        );
    }
}
```
* **Replenish Rate:** 10 tokens/second (how many requests are allowed per second continuously for auth endpoints).
* **Burst Capacity:** 20 tokens (maximum requests allowed in a single second for auth endpoints).

---

## 6. Downstream Consumption Guidelines

Downstream microservices (e.g. `user-service`) must **NOT** re-verify the JWT signature, as this is already verified at the gateway. Instead:
1. They should parse the custom headers:
   * `X-User-Id` - The authenticated user ID.
   * `X-User-Email` - The user's email address.
   * `X-User-Roles` - A comma-separated list of roles (e.g., `ROLE_USER,ROLE_ADMIN`).
2. They should implement a lightweight Security Filter that extracts these headers and places them into the `SecurityContextHolder` as `GrantedAuthority` permissions/roles, allowing standard Spring Security method-level annotations (`@PreAuthorize`) to function normally.

---

## 7. Action Plan / Roadmap

### Phase 1: Core Setup
* [ ] Add Eureka client and Redis dependencies to `gateway-service/pom.xml`.
* [ ] Set up `application.yml` with Eureka server registries and Redis connections.
* [ ] Enable `eureka` configuration on `auth-service` (uncomment the properties in `application-dev.yml` and add the client dependency).

### Phase 2: Security implementation
* [ ] Implement `JwtUtil` class in the `gateway-service` package structure.
* [ ] Implement `JwtValidationFilter` component.
* [ ] Configure Reactive Spring Security to permit all public traffic (e.g. registration, login, email verification) and let the Gateway Filter intercept protected paths.

### Phase 3: Testing & Hardening
* [ ] Test routing to `/api/v1/auth/**` and verify header injection on protected routes.
* [ ] Perform logout in `auth-service` and verify that the Gateway immediately rejects the blacklisted token on subsequent requests.
* [ ] Validate that rate limiting triggers a `429 Too Many Requests` when limits are exceeded.
