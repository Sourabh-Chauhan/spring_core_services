# Implementation Plan: Spring Security Reactive Configuration

This plan outlines the steps to integrate Spring Security Reactive (WebFlux Security) into the `gateway-service` to manage edge security filters, disable CSRF, and configure path permissions.

---

## 0. Rationale: Why we add Spring Security and `SecurityWebFilterChain`

1. **Class Accessibility:** Adding `spring-boot-starter-security` equips the gateway classpath with WebFlux-compatible reactive security components (such as `SecurityWebFilterChain` and `ServerHttpSecurity`).
2. **Prevent Default Lockdown:** By default, if Spring Security is on the classpath, it immediately secures every endpoint with HTTP Basic Auth and auto-generates a console password. Declaring a custom `SecurityWebFilterChain` bean allows us to explicitly permit public/routed traffic.
3. **Stateless Compliance:** We disable CSRF globally (`.csrf(CsrfSpec::disable)`) because our microservices rely on stateless JWT tokens, not session-bound cookie headers.
4. **CORS De-duplication:** We disable Spring Security's internal CORS support (`.cors(CorsSpec::disable)`) since CORS is handled centrally by Spring Cloud Gateway (`spring.cloud.gateway.globalcors`). Leaving both enabled would result in duplicate CORS headers.

---

## 1. Architectural Strategy: Gateway Security Configuration

In a Spring Cloud Gateway service, we have two layers of request handling:
1. **Spring Security Filter Chain (`SecurityWebFilterChain`):** Operates globally at the web server layer.
2. **Gateway Route Filters (e.g., `JwtValidationFilter`):** Operates on specific routes defined in `application.yaml`.

### The Design:
* **Public Route Access:** We configure the global `SecurityWebFilterChain` to disable CSRF and permit all traffic (or permit specific public paths like `/api/v1/auth/**`). This ensures the gateway does not block authentication requests (login, registration, verification) before they reach the `auth-service`.
* **Route-Level Security:** We delegate token validation, JTI blacklist checks, and downstream header injection (token relay) to our custom `JwtValidationFilter`. This is the standard, flexible approach for API Gateways, as it allows us to selectively apply validation to certain routes while executing header mutation.

---

## 2. Proposed Configuration Setup

### A. Add Security Dependency
Add the Spring Security starter to [gateway-service/pom.xml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### B. Implement `SecurityConfig`
Create `com.chauhan.gatewayservice.config.SecurityConfig` with a `SecurityWebFilterChain` bean:
```java
package com.chauhan.gatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disabled as we use stateless JWTs
            .cors(ServerHttpSecurity.CorsSpec::disable) // Disabled as CORS is managed by spring.cloud.gateway.globalcors
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/v1/auth/**").permitAll() // Allow auth endpoints (login/register)
                .anyExchange().permitAll() // Permit other exchanges to let route filters handle specific authorization
            )
            .build();
    }
}
```

---

## 3. Step-by-Step Execution Plan

* [ ] **Step 1: Add Dependency**
  * Insert `spring-boot-starter-security` dependency in `gateway-service/pom.xml`.
* [ ] **Step 2: Create SecurityConfig Class**
  * Create `SecurityConfig.java` under the `com.chauhan.gatewayservice.config` package.
* [ ] **Step 3: Verify & Compile**
  * Run compilation with JDK 25.
  * Check off the goal in the [checklist.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/markdowns/checklist.md).
  * Automatically generate the git commit message.
