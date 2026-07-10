# Troubleshooting & Route Fix Plan: User Service Route Resolution

This plan details the resolution of the `503 Service Unavailable` error returned when requesting `/api/v1/users`.

---

## 1. Audit & Root Cause Analysis

* **Symptom:**
  ```json
  {
      "timestamp": "2026-07-10T08:49:32.438Z",
      "path": "/api/v1/users",
      "status": 503,
      "error": "Service Unavailable",
      "requestId": "b7d97ae8-3"
  }
  ```
* **Audit findings:**
  * The `/api/v1/users` path mappings are declared and implemented in `UserController.java` within the `auth-service` module.
  * In the API Gateway's [application.yaml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/resources/application.yaml), the route with ID `user-service` was pointing to `uri: lb://user-service`.
  * Since there is no standalone microservice named `USER-SERVICE` registered on Eureka, the Spring Cloud LoadBalancer fails to resolve the instance, throwing a `503 Service Unavailable`.
  * We need to change the route destination to `lb://auth-service` to correctly delegate requests matching `/api/v1/users/**` to the `auth-service` instances.
  * We also need to make sure the `RequestRateLimiter` and `CircuitBreaker` filters are uncommented and fully active on the `user-service` route.

---

## 2. Proposed Configuration Refactoring

We will update [application.yaml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/resources/application.yaml)'s `user-service` route:

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
                - name: CircuitBreaker
                  args:
                    name: userServiceCircuitBreaker
                    fallbackUri: forward:/fallback/user
```

---

## 3. Step-by-Step Execution Plan

* [ ] **Step 1: Edit application.yaml**
  * Set `uri: lb://auth-service` for route `user-service`.
  * Uncomment the `RequestRateLimiter` and `CircuitBreaker` filters on `user-service`.
* [ ] **Step 2: Verify & Compile**
  * Run Maven compile to verify configuration syntax.
* [ ] **Step 3: Test Route Execution**
  * Boot Eureka, Auth Service, and Gateway, then send a request to `/api/v1/users` to verify successful routing.
