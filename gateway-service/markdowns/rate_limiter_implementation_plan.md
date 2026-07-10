# Implementation Plan: Gateway Edge Rate Limiting

This plan details the design and implementation of edge rate limiting in the API Gateway using Spring Cloud Gateway's Redis-backed rate limiter.

---

## Section 0: Architectural Rationale & Technical Decisions

### Why Redis-Backed Rate Limiting?
In a distributed microservice architecture, rate limiting at the API Gateway prevents denial-of-service (DoS) attacks, brute-forcing, and resource exhaustion.
* **Token Bucket Algorithm:** Spring Cloud Gateway's `RedisRateLimiter` uses the Token Bucket algorithm (implemented via optimized Redis Lua scripts). It determines whether to allow or drop a request in a highly performant, non-blocking manner.
* **Shared State:** Since both instances of the Gateway or other gateway nodes would share the same Redis server, rate limits are enforced consistently across the entire edge layer rather than per-instance.

### Why Client IP Resolution (`ipKeyResolver`)?
* **Unauthenticated Requests:** Public endpoints (like `/api/v1/auth/login` or `/api/v1/auth/register`) do not contain JWTs, so we cannot limit by User ID.
* **Remote IP Tracking:** Resolving the remote address IP ensures that unauthenticated clients are limited based on their physical network origin, mitigating brute-force password login attempts.

---

## 1. Proposed Implementation Steps

### A. Implement in `GatewayConfig.java`
Instead of creating a new configuration class, we will keep and uncomment the existing [GatewayConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/java/com/chauhan/gatewayservice/config/GatewayConfig.java) class so the developer can study it. We will:
* Activate the class with `@Configuration`.
* Add the active `@Bean public KeyResolver ipKeyResolver()` to resolve IP addresses.
* Keep the programmatic `routeLocator` method as a fully uncommented reference, but **omit** the `@Bean` annotation so it does not register and conflict with the active YAML routes.
* Add inline comments showing how to configure the rate limiting filter programmatically.

### B. Bind Filter in `application.yaml`
Add the `RequestRateLimiter` filter to both `auth-service` and `user-service` routes under the `spring.cloud.gateway.server.webflux.routes` section:
```yaml
          routes:
            - id: auth-service
              uri: lb://auth-service
              predicates:
                - Path=/api/v1/auth/**,/api/v1/admin/**,/api/v1/sessions/**
              filters:
                - name: RequestRateLimiter
                  args:
                    redis-rate-limiter.replenishRate: 10
                    redis-rate-limiter.burstCapacity: 20
                    key-resolver: "#{@ipKeyResolver}"

            - id: user-service
              uri: lb://user-service
              predicates:
                - Path=/api/v1/users/**
              filters:
                - JwtValidationFilter
                - name: RequestRateLimiter
                  args:
                    redis-rate-limiter.replenishRate: 10
                    redis-rate-limiter.burstCapacity: 20
                    key-resolver: "#{@ipKeyResolver}"
```

---

## 2. Step-by-Step Execution Plan

* [ ] **Step 1: Refactor GatewayConfig Class**
  * Update [GatewayConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/java/com/chauhan/gatewayservice/config/GatewayConfig.java) with active config, IP resolver, and programmatic routing references.
* [ ] **Step 2: Bind Rate Limiter in application.yaml**
  * Update routes in `application.yaml` to include `RequestRateLimiter` filters.
* [ ] **Step 3: Verify & Compile**
  * Run Maven compilation to check for configuration or syntax errors.
* [ ] **Step 4: Restart & Test**
  * Start the Gateway and send concurrent requests to verify the rate limiting headers (`X-RateLimit-Remaining`, `X-RateLimit-Burst-Capacity`).
