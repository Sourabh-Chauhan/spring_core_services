# API Gateway Service Development Checklist

This checklist tracks the implementation and verification goals for the centralized API Gateway service, matching the architecture design.

## Phase 1: Project Setup & Service Discovery

- [x] **Dependencies Setup:**
    - [x] Add `spring-cloud-starter-gateway-server-webflux` to `gateway-service/pom.xml`.
    - [x] Add `spring-cloud-starter-netflix-eureka-client` for service discovery.
    - [x] Add `spring-boot-starter-data-redis-reactive` for rate limiting and blacklist lookup.
    - [x] Add JSON Web Token dependencies (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`).
- [x] **Eureka Integration:**
    - [x] Add `@EnableDiscoveryClient` to the Gateway main application class.
    - [x] Configure `eureka.client.service-url.defaultZone` in `application.yml`.
    - [x] Uncomment/enable Eureka client settings in `auth-service/src/main/resources/application-dev.yml`.
- [x] **Redis Connection:**
    - [x] Configure Redis connection details (`host`, `port`) in `application.yml`.

## Phase 2: Edge Routing & CORS

- [x] **Dynamic Routing Rules:**
    - [x] Define route `/api/v1/auth/**`, `/api/v1/admin/**`, and `/api/v1/sessions/**` forwarding to `lb://auth-service`.
    - [x] Define route `/api/v1/users/**` forwarding to `lb://user-service`.
- [x] **Global CORS Configuration:**
    - [x] Configure central CORS rules in `application.yml` targeting `[/**]` to allow frontend domain (`http://localhost:3000`).
    - [x] Enable `allowCredentials: true` and configure allowed headers/methods.

## Phase 3: Centralized Security & Header Injection

- [x] **JWT Utility:**
    - [x] Implement `JwtUtil` in the gateway service to parse claims (JTI, User ID, Email, Roles) and validate signatures.
- [x] **JWT Validation Filter:**
    - [x] Create custom `JwtValidationFilter` extending `AbstractGatewayFilterFactory`.
    - [x] Extract bearer token from the `Authorization` header.
    - [x] Validate signature and expiration status.
- [x] **Shared Redis Blacklist Integration:**
    - [x] Read `blacklist:<jti>` from Reactive Redis in `JwtValidationFilter` to check token revocation.
- [x] **Header Injection (Token Relay):**
    - [x] Mutate downstream requests to inject validated details:
        - [x] `X-User-Id`
        - [x] `X-User-Email`
        - [x] `X-User-Roles`
- [ ] **Security Config:**
    - [ ] Configure `SecurityWebFilterChain` (Spring Security Reactive) to permit public routes and validate authentication on others.

## Phase 4: Edge Rate Limiting & Error Handling

- [ ] **Rate Limiter Configuration:**
    - [ ] Implement the `ipKeyResolver` bean to identify users by remote IP address.
    - [ ] Bind the `RequestRateLimiter` filter to the Gateway routes.
    - [ ] Configure appropriate `replenishRate` and `burstCapacity` parameters.
- [ ] **Global Exception Handler:**
    - [ ] Implement a custom error handler to return standardized JSON responses on:
        - [ ] `401 Unauthorized` (invalid/expired JWT, blacklisted token).
        - [ ] `403 Forbidden` (invalid permissions).
        - [ ] `429 Too Many Requests` (rate limited).

## Phase 5: Testing & Production Hardening

- [ ] **Verification & Testing:**
    - [ ] Verify that public endpoints bypass validation and are routed successfully.
    - [ ] Verify that protected endpoints require valid JWT headers.
    - [ ] Verify that blacklisted JTIs (following logout) are rejected by the Gateway.
    - [ ] Verify that rate-limited clients receive `429 Too Many Requests`.
- [ ] **Observability:**
    - [ ] Set up Actuator health check and Prometheus metrics endpoints.
- [ ] **Containerization:**
    - [ ] Create a `Dockerfile` for the `gateway-service`.
    - [ ] Add `gateway-service` and Eureka Server configurations to local `docker-compose.yml`.
