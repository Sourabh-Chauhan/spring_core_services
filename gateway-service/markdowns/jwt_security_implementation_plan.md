# Implementation Plan: JWT Utility & Edge JWT Validation Filter

This plan outlines the implementation of centralized JWT validation at the API Gateway boundary to authenticate incoming requests before they reach downstream microservices.

---

## 1. Audit & Code Reuse Identification

### Auth Service Implementation:
The `auth-service` contains [JwtUtil.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/security/JwtUtil.java) which:
1. Validates signature/expiry using `io.jsonwebtoken`.
2. Generates access/refresh tokens.
3. Extracts JTI, email, user ID, and roles.

### Mismatch & Adaptation for Gateway:
1. **Domain Dependency:** The generator methods in `auth-service`'s `JwtUtil` depend on database entity classes (`User`, `Role`) which are **not** present on the gateway's classpath. The gateway does not need to issue tokens. Therefore, all generation methods will be omitted.
2. **Context-Free Validation:** `auth-service` validates tokens against Spring Security's `UserDetails`. In the Gateway, we perform stateless edge validation. We will implement `validateToken(String)` which parses claims (automatically checking signature and expiry) and verifies that the token type is `access`.

---

## 2. Proposed Architecture & Design

### A. JWT Configuration Properties
We need to copy the same security configuration properties from `auth-service` to [gateway-service/src/main/resources/application.yaml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/resources/application.yaml) to ensure keys match:
```yaml
security:
  jwt:
    secret: ${JWT_SECRET:saiofal;vkasfbehwqpofhjashofjaswfpojasfvbafop[hfifonfo[shjfwefjjfslajfsahfoshffasnbash[f}
    issuer: ${JWT_ISSUER:api.substring.com}
    access-ttl-seconds: ${JWT_ACCESS_TTL_SECONDS:3000}
    refresh-ttl-seconds: ${JWT_REFRESH_TTL_SECONDS:86400}
```

### B. Gateway `JwtUtil` Class
We will implement `JwtUtil` in the `gateway-service` package under `com.chauhan.gatewayservice.security` with:
* Configurable `@Value` parameters.
* HMAC SHA512 signing key initialization.
* Expiry, JTI, Email, and Roles claim extractors.
* `validateToken(String)` returning boolean.

### C. `JwtValidationFilter`
We will implement `JwtValidationFilter` extending `AbstractGatewayFilterFactory` under `com.chauhan.gatewayservice.security` to intercept requests to protected routes:
1. Extract the token from `Authorization: Bearer <token>`.
2. Call `jwtUtil.validateToken(token)`.
3. Mutate the request to pass claims downstream via custom headers:
   * `X-User-Id`
   * `X-User-Email`
   * `X-User-Roles`
4. If validation fails, return `401 Unauthorized` directly to the client with a JSON response body.

---

## 3. Step-by-Step Execution Plan

* [ ] **Step 1: Update configuration**
  * Add `security.jwt` block to [gateway-service/src/main/resources/application.yaml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/resources/application.yaml).
* [ ] **Step 2: Create package `com.chauhan.gatewayservice.security`**
  * Create [JwtUtil.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/java/com/chauhan/gatewayservice/security/JwtUtil.java) containing only edge verification and claims extraction.
  * Create [JwtValidationFilter.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/java/com/chauhan/gatewayservice/security/JwtValidationFilter.java).
* [ ] **Step 3: Register Filter in Routing Rules**
  * Apply `JwtValidationFilter` to the `/api/v1/users/**` route in the gateway configuration.
* [ ] **Step 4: Verify & Compile**
  * Compile the full workspace and check off the completed checklist items in `gateway-service` and `auth-service` checklists.
