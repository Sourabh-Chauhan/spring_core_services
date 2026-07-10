# Implementation Plan: Gateway Properties Audit & Optimization

This plan outlines the audit and clean-up of `application.yaml` inside the `gateway-service`.

---

## 1. Audit Findings & Proposed Changes

### A. Remove Redundant JWT properties
* **Finding:** The properties `security.jwt.access-ttl-seconds` and `security.jwt.refresh-ttl-seconds` are configured in [application.yaml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/resources/application.yaml).
* **Rationale:** The Gateway only validates existing access tokens and extracts claims (`JwtUtil` and `JwtValidationFilter`). Token generation and expiration handling (which require TTL properties) are managed exclusively by the downstream `auth-service`.
* **Action:** Remove `security.jwt.access-ttl-seconds` and `security.jwt.refresh-ttl-seconds` from the Gateway configuration.

### B. Correct CORS Wildcard Conflict
* **Finding:** `allowedOrigins: "*"` is configured alongside `allowCredentials: true` under `globalcors`.
* **Rationale:** Under W3C CORS specifications, browsers reject credentialed requests (containing cookies, authorization headers) targeting the literal wildcard `*`.
* **Action:** Replace `allowedOrigins: "*"` with `allowedOriginPatterns: "*"`. This resolves the browser-side CORS conflict while keeping the gateway permissive for development.

### C. Clean Up Debug Logging
* **Finding:** TRACE logging is enabled for `org.springframework.cloud.gateway` and `org.springframework.web.reactive.DispatcherHandler`.
* **Rationale:** While useful during initial routing setup, trace logging outputs massive debug text for every request, cluttering local console outputs.
* **Action:** Restore standard logging levels to `INFO` for standard operations.

---

## 2. Step-by-Step Execution Plan

* [ ] **Step 1: Edit application.yaml**
  * Remove redundant TTL keys.
  * Update `allowedOrigins: "*"` to `allowedOriginPatterns: "*"`.
  * Adjust `logging.level` settings to standard values.
* [ ] **Step 2: Verification**
  * Restart `gateway-service` and send a request to verify that CORS headers and JWT validation continue to work perfectly.
