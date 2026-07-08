# Implementation Plan: Global CORS Configuration

This plan outlines the steps to configure and enforce a centralized CORS policy at the API Gateway perimeter, while resolving duplicate header conflicts with downstream microservices.

---

## 1. Audit & Problem Identification

### Current Setup:
1. **API Gateway:** Contains a central CORS mapping under `spring.cloud.gateway.globalcors` allowing `http://localhost:3000` with `allowCredentials: true`.
2. **Auth Service:** Standard Spring Security configuration contains an active `CorsConfigurationSource` bean allowing wildcard origins (`*`) and setting `allowCredentials: false`.

### The Problem:
When a client sends a request through the Gateway, both the Gateway and the downstream `auth-service` append CORS headers (`Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials`). 
This results in **duplicate and conflicting CORS headers** returned to the browser (e.g., `Access-Control-Allow-Origin: http://localhost:3000, *`), causing the browser to immediately block the request for security reasons.

---

## 2. Proposed Architecture & Solution

To resolve this conflict permanently:

### A. Centralize CORS at the Gateway Perimeter
We will keep the Gateway as the single source of truth for CORS. We will add a `DedupeResponseHeader` filter to `spring.cloud.gateway.default-filters` in [gateway-service/src/main/resources/application.yaml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/resources/application.yaml).
This ensures that the Gateway intercepts and de-duplicates any headers received from downstream services before sending the response to the browser.

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
```

### B. Disable Downstream CORS Filters
Since downstream microservices operate behind the gateway boundary, they do not require browser CORS checks. We will refactor [SecurityConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/SecurityConfig.java) in the `auth-service` to disable CORS:
```java
// Disable CORS downstream as it is handled by the API Gateway perimeter
.cors(AbstractHttpConfigurer::disable)
```

---

## 3. Step-by-Step Execution Plan

* [ ] **Step 1: Configure Gateway De-duplication**
  * Update [gateway-service/src/main/resources/application.yaml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/src/main/resources/application.yaml) to add the `DedupeResponseHeader` default filter.
* [ ] **Step 2: Disable Downstream CORS**
  * Modify [SecurityConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/SecurityConfig.java) in `auth-service` to disable CORS processing on Spring Security.
* [ ] **Step 3: Verification & Compilation**
  * Compile the full workspace using JDK 25 and check for any compilation issues.
  * Check off the CORS configuration goals in the [checklist.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/gateway-service/markdowns/checklist.md).
