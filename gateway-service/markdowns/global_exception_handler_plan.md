# Implementation Plan: Gateway Global Exception Handler

This plan details the design and implementation of a centralized exception handler in the API Gateway to return standardized JSON responses on unauthorized, forbidden, and rate-limited errors.

---

## Section 0: Architectural Rationale & Technical Decisions

### Why a Centralized Reactive `ErrorWebExceptionHandler`?
In standard Servlet MVC applications, `@ControllerAdvice` handles exceptions. However, Spring Cloud Gateway is built on Spring WebFlux (reactive), where:
1. Filter exceptions occur outside the standard dispatcher controller execution context.
2. Standard WebFlux uses `DefaultErrorWebExceptionHandler` to write responses.
3. Centralizing error mapping in a high-priority `@Order(-2)` bean implementing `ErrorWebExceptionHandler` allows us to intercept all system and filter exceptions (like JWT failures, Spring Security access denials, and rate limits) before they reach the default handler.

### Rate Limiter Exception Conversion (`throw-on-limit`)
By default, the `RequestRateLimiter` filter commits the response immediately with a 429 status code and an empty body.
* **Technical Fix:** By setting `throw-on-limit: true` in the rate limiter properties, the filter throws a `ResponseStatusException(429)` instead.
* **Result:** This exception propagates down the reactive chain and is cleanly captured by our `GlobalExceptionHandler`, which formats it into our standardized JSON structure.

### Centralizing JWT Filter Errors
We will refactor `JwtValidationFilter.java` to return `Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "..."))` instead of manually serializing JSON inside the filter. This ensures all 401 exceptions share the same JSON format and timestamp generation code.

---

## 1. Proposed Implementation Steps

### A. Create `GlobalExceptionHandler.java`
Create `com.chauhan.gatewayservice.exception.GlobalExceptionHandler` implementing `ErrorWebExceptionHandler` (ordered at `-2`):
* Map `ResponseStatusException` (including 401, 403, and 429) to their respective HTTP status codes and standardized JSON.
* Map Spring Security `AccessDeniedException` to `403 Forbidden`.
* Map Spring Security `AuthenticationException` to `401 Unauthorized`.
* Output standard JSON payload:
  ```json
  {
      "status": 429,
      "error": "Too Many Requests",
      "message": "Rate limit exceeded. Please try again later.",
      "path": "/api/v1/users",
      "timestamp": "2026-07-10T14:50:11.123Z"
  }
  ```

### B. Refactor `JwtValidationFilter.java`
Update `JwtValidationFilter` to return `Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "..."))` on verification failure, removing the manual `onError` method.

### C. Enable `throw-on-limit` in `application.yaml`
Add `throw-on-limit: true` to the `RequestRateLimiter` arguments in `application.yaml` for both the `auth-service` and `user-service` routes.

---

## 2. Step-by-Step Execution Plan

* [ ] **Step 1: Create GlobalExceptionHandler Class**
  * Implement `GlobalExceptionHandler.java` under package `com.chauhan.gatewayservice.exception`.
* [ ] **Step 2: Refactor JwtValidationFilter**
  * Edit [JwtValidationFilter.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/java/com/chauhan/gatewayservice/security/JwtValidationFilter.java) to delegate errors to `ResponseStatusException`.
* [ ] **Step 3: Update application.yaml**
  * Add `throw-on-limit: true` to route filters.
* [ ] **Step 4: Verify & Compile**
  * Run Maven compilation to check for compilation or import errors.
* [ ] **Step 5: Restart & Test**
  * Verify 401, 403, and 429 JSON responses are fully standardized.
