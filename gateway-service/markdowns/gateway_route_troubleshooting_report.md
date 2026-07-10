# API Gateway Routing: Diagnostics & Resolution Report

This document consolidates the troubleshooting, diagnostics, and final resolutions for the API Gateway routing and filter implementation.

---

## 1. Problem Statement & Initial Analysis

### The Issue
Incoming HTTP requests to `http://localhost:8080/api/v1/auth/login` returned a `404 Not Found` error.
* Direct requests to `auth-service` on port `8082` returned `401 Unauthorized`, proving the service was active.
* Both services were registered successfully with Eureka.
* The Gateway was unable to match any route definition for `/api/v1/auth/**`.

---

## 2. Investigation Phases

### Phase 2.1: Predicate Syntax Auditing
The original configuration used comma-separated shortcut paths:
```yaml
predicates:
  - Path=/api/v1/auth/**, /api/v1/admin/**, /api/v1/sessions/**
```
* **Hypothesis:** Leading spaces after commas in the shortcut format caused parsing errors in the Spring properties binder.
* **Attempted Fix:** Converted to the explicit list format:
  ```yaml
  predicates:
    - name: Path
      args:
        patterns:
          - /api/v1/auth/**
          - /api/v1/admin/**
  ```
  This formatting failed to match the routes as well, indicating a deeper property-binding issue.

### Phase 2.2: Programmatic Java Routing (GatewayConfig)
To bypass any silent configuration properties binding issues, we implemented programmatic routes in Java using `RouteLocatorBuilder`:
```java
@Configuration
public class GatewayConfig {
    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, JwtValidationFilter jwtValidationFilter) {
        return builder.routes()
                .route("auth-service", r -> r.path("/api/v1/auth/**", "/api/v1/admin/**", "/api/v1/sessions/**").uri("lb://auth-service"))
                .route("user-service", r -> r.path("/api/v1/users/**")
                        .filters(f -> f.filter(jwtValidationFilter.apply(new JwtValidationFilter.Config())))
                        .uri("lb://user-service"))
                .build();
    }
}
```
* **Result:** Programmatic configuration successfully bypassed YAML parsing and successfully forwarded requests to `auth-service`. This proved Eureka and load balancing were fully functional, pointing to a configuration binding problem.

---

## 3. Diagnostics & Root Cause Discovery

### The Diagnostic Runner
A temporary `RouteDiagnosticRunner` bean was created to inspect the auto-configured `GatewayProperties` bean on startup:
```java
log.info("GatewayProperties routes count = {}", gatewayProperties.getRoutes().size());
log.info("Env spring.cloud.gateway.routes[0].id = {}", environment.getProperty("spring.cloud.gateway.routes[0].id"));
```
* **Observation:** The Spring `Environment` successfully held the properties from `application.yaml`, but `GatewayProperties` had **0 routes** bound.
* **Reflection Check:** Checking the `@ConfigurationProperties` prefix annotation on `GatewayProperties.class`:
  ```java
  ConfigurationProperties anno = GatewayProperties.class.getAnnotation(ConfigurationProperties.class);
  // Returned: 'spring.cloud.gateway.server.webflux'
  ```

### Root Cause
In the version of Spring Cloud Gateway resolved on the classpath (Spring Cloud 2025 / Spring Boot 4.x using `spring-cloud-starter-gateway-server-webflux`), the configuration binding prefix has been updated from `spring.cloud.gateway` to **`spring.cloud.gateway.server.webflux`**. Any configuration placed under `spring.cloud.gateway.routes` was ignored during binding.

---

## 4. Final Resolutions

### A. Namespace Configuration Update
We restored default YAML routing by wrapping `routes`, `default-filters`, and `globalcors` under the correct namespace prefix:
```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          default-filters:
            - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
          globalcors:
            cors-configurations:
              '[/**]':
                allowedOrigins: "*"
                allowedMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
          routes:
            - id: auth-service
              uri: lb://auth-service
              predicates:
                - Path=/api/v1/auth/**,/api/v1/admin/**,/api/v1/sessions/**
            - id: user-service
              uri: lb://user-service
              predicates:
                - Path=/api/v1/users/**
              filters:
                - JwtValidationFilter
```

### B. Global Logging Filter Implementation
To output request routes and their downstream physical destinations, we registered a custom global `LoggingFilter`:
```java
@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);

        if (route != null && routeUri != null) {
            log.info("Gateway Route matched - ID: '{}', Request Path: '{}' -> Forwarded to: '{}'",
                    route.getId(), path, routeUri);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 10160; // Run immediately after instance load balancing
    }
}
```
* **Output:**
  `INFO --- c.c.gatewayservice.filter.LoggingFilter : Gateway Route matched - ID: 'auth-service', Request Path: '/api/v1/auth/login' -> Forwarded to: 'http://192.168.1.16:8082/api/v1/auth/login'`
