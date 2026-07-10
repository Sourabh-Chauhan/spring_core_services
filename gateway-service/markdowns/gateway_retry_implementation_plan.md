# Implementation Plan: Gateway GET Retry Mechanism

This plan outlines the selective configuration of a Retry mechanism at the API Gateway to handle transient errors on safe (idempotent) endpoints.

---

## Section 0: Architectural Rationale & Technical Decisions

### Why Selective Retry?
* **Safety First (Idempotency):** Retrying state-changing operations (such as `POST /api/v1/auth/register` or `PATCH /api/v1/users/profile`) is risky if a timeout occurs. The request might have already executed downstream, leading to duplicate side effects (e.g. double registration or duplicate DB writes). 
* **Safe Retries:** By contrast, HTTP `GET` requests are idempotent (repeating them has no side effects). If a transient timeout or server error occurs while fetching data, retrying the request immediately improves the application's perceived stability without introducing side effects.

### Filter Chain Ordering
The filters on the route are executed sequentially:
1. `JwtValidationFilter` (validates the user)
2. `RequestRateLimiter` (checks client request limits)
3. `Retry` (triggers a retry on `GET` requests for 5xx errors or timeouts)
4. `CircuitBreaker` (trips open if failures persist, bypassing the retry filter)

---

## 1. Proposed YAML Configuration Changes

We will edit [application.yaml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/resources/application.yaml) to add the `Retry` filter to the `user-service` route:

```yaml
            # Route to User Service (Hosted within auth-service)
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

## 2. Step-by-Step Execution Plan

* [ ] **Step 1: Update application.yaml**
  * Insert the `Retry` filter configuration block into the `user-service` route filters list.
* [ ] **Step 2: Verify & Compile**
  * Run `mvn clean compile` to verify configuration syntax.
* [ ] **Step 3: Test Route Execution**
  * Boot Eureka, Auth Service, and Gateway, then send requests to verify both routing and retry behaviors.
