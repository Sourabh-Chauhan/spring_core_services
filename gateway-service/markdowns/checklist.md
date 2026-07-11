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
- [x] **Security Config:**
    - [x] Configure `SecurityWebFilterChain` (Spring Security Reactive) to permit public routes and validate authentication on others.

## Phase 4: Edge Rate Limiting, Resiliency & Error Handling

- [x] **Rate Limiter Configuration:**
    - [x] Implement the `ipKeyResolver` bean to identify users by remote IP address.
    - [x] Bind the `RequestRateLimiter` filter to the Gateway routes.
    - [x] Configure appropriate `replenishRate` and `burstCapacity` parameters.
- [x] **Circuit Breaker Configuration (Resiliency):**
    - [x] Add Resilience4j dependency to `gateway-service/pom.xml`.
    - [x] Configure Resilience4j settings (time limit, sliding window size, failure rate threshold).
    - [x] Bind the `CircuitBreaker` filter with custom fallback URIs to routes in `application.yaml`.
    - [x] Implement fallback controllers to return standardized fallback JSON payloads when downstream services fail.
- [x] **Retry Configuration (Fault Tolerance):**
    - [x] Add the `Retry` filter to the `user-service` route in `application.yaml`.
    - [x] Restrict retries to safe `GET` requests only.
    - [x] Configure to retry on `SERVER_ERROR` series (5xx errors) and timeouts up to 3 times.
- [x] **Global Exception Handler:**
    - [x] Implement a custom error handler to return standardized JSON responses on:
        - [x] `401 Unauthorized` (invalid/expired JWT, blacklisted token).
        - [x] `403 Forbidden` (invalid permissions).
        - [x] `429 Too Many Requests` (rate limited).

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

## Phase 6: Asynchronous Messaging & Decoupling

- [ ] **Asynchronous Messaging & Decoupling:**
    - [ ] Add RabbitMQ message broker container to local Docker configuration.
    - [ ] Add RabbitMQ AMQP dependencies to `auth-service` and configure publishing events.
    - [ ] Create standalone `notification-service` microservice module.
    - [ ] Implement RabbitMQ event listener in `notification-service` to consume `user.registered` messages.
    - [ ] Integrate JavaMailSender in `notification-service` to process email sending asynchronously.
    - [ ] Integrate Twilio SMS SDK in `notification-service` to process SMS sending asynchronously.
    - [ ] Integrate Firebase Admin SDK for Push Notifications (FCM).
    - [ ] Implement Webhook subscription lookup and HTTP POST callback mechanism with HMAC verification.
