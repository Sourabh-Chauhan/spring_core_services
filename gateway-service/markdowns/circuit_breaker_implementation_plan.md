# Implementation Plan: Gateway Circuit Breaker Configuration

This plan details the implementation of a Circuit Breaker (Resilience4j) in the API Gateway to handle downstream service failures gracefully.

---

## Section 0: Architectural Rationale & Technical Decisions

### Why a Circuit Breaker?
API Gateways route traffic to multiple downstream services. If a service experiences high latency, database locks, or crashes, requests to it will block gateway connections, which can exhaust resource capacity and cascade failures.
* **Resilience4j Integration:** Spring Cloud Gateway uses Resilience4j to wrap route requests in circuit breakers and time limiters.
* **State Management:** The circuit breaker monitors request health using a sliding window:
  * **Closed:** Downstream is healthy. Requests pass through normally.
  * **Open:** Failure rate exceeds the threshold. All requests are short-circuited immediately and routed to a local fallback endpoint.
  * **Half-Open:** Allows a limited number of test requests to check if the downstream service has recovered.

### Fallback Mechanism:
* A central, reactive `FallbackController` will expose endpoints `/fallback/auth` and `/fallback/user`.
* When a timeout or circuit trip occurs, Spring Cloud Gateway forwards the request locally to these endpoints, returning a standardized JSON error response rather than a connection failure page.

---

## 1. Proposed Implementation Steps

### A. Dependency Update
Add the Reactor Resilience4j starter dependency in [pom.xml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/pom.xml):
```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
        </dependency>
```

### B. Resilience4j Configurer
Create `com.chauhan.gatewayservice.config.CircuitBreakerConfig` defining a Java customizer to programmatically tune the defaults:
* **Sliding Window:** 10 requests.
* **Failure Rate Threshold:** 50%.
* **Open State Duration:** 10 seconds.
* **Timeout Duration:** 3 seconds (max duration a downstream call can take).

### C. Bind Filter in YAML (`application.yaml`)
Add the `CircuitBreaker` filter with its `name` and `fallbackUri` arguments to the `auth-service` and `user-service` routes.

### D. Fallback Controller
Create `com.chauhan.gatewayservice.controller.FallbackController` to handle any HTTP method (GET, POST, etc.) forwarding to `/fallback/auth` or `/fallback/user` and return a structured JSON response.

---

## 2. Step-by-Step Execution Plan

* [ ] **Step 1: Add dependency to pom.xml**
  * Update `gateway-service/pom.xml` with Resilience4j starter.
* [ ] **Step 2: Create CircuitBreakerConfig class**
  * Create `CircuitBreakerConfig.java` under package `com.chauhan.gatewayservice.config`.
* [ ] **Step 3: Create FallbackController class**
  * Create `FallbackController.java` under package `com.chauhan.gatewayservice.controller`.
* [ ] **Step 4: Update application.yaml**
  * Add the `CircuitBreaker` filters to routes.
* [ ] **Step 5: Verify & Compile**
  * Compile the workspace with Maven.
* [ ] **Step 6: Restart & Test**
  * Boot the gateway, stop the downstream service (e.g. `auth-service`), send requests, and verify the structured fallback JSON is returned.
