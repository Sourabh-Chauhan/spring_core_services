# Auth Service Improvement & Security Task List (TODO)

This document lists the recommended security enhancements, bug fixes, and architectural improvements identified during the audit of the `auth-service`.

---

## 1. Security & Endpoint Authorization

### 🚨 Critical Vulnerability: Unsecured User Management Endpoints
- **File:** [UserController.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/controller/UserController.java)
- **Description:** 
  Currently, all HTTP endpoints under `/api/v1/users` (for retrieving all users, updating users, deleting users, etc.) are accessible to **any authenticated user**. 
  1. The role-based request matchers in [SecurityConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/SecurityConfig.java#L107-L113) are commented out:
     ```java
     // .requestMatchers(AppConstants.AUTH_ADMIN_URLS).hasRole(AppConstants.ADMIN_ROLE)
     ```
  2. [UserController.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/controller/UserController.java) is missing class-level or method-level authorization annotations (e.g., `@PreAuthorize("hasRole('ADMIN')")`).
- **Remediation:** 
  - Add `@PreAuthorize("hasRole('ADMIN')")` at the class-level of `UserController` (or selectively on sensitive endpoints like `getAllUsers` and `deleteUser`).
  - Uncomment and verify the role request matching rules in `SecurityConfig.java`.

### ⚠️ Insecure CORS Wildcard
- **File:** [SecurityConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/SecurityConfig.java#L65-L78)
- **Description:** 
  The CORS configuration permits all origins (`List.of("*")`) and sets `allowCredentials` to false. For production environments, wildcard origins are insecure and can lead to cross-origin data exposure.
- **Remediation:** 
  - Configure a whitelist of allowed origins and resolve them dynamically from properties (e.g., `${cors.allowed-origins}`).

### 🔑 Unverified Password & Email Changes in User Updates
- **File:** [UserServiceImpl.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/service/impl/UserServiceImpl.java#L117-L174)
- **Description:** 
  1. In `updateUser()`, when a password change is requested, the password is encrypted and saved without validating the user's *current* password.
  2. The email update logic is currently commented out. If uncommented, updating the email address must trigger a re-verification process to ensure the user owns the new address.
- **Remediation:**
  - Create a dedicated change-password endpoint requiring both the old and new passwords.
  - Implement a verification workflow when updating user emails.

---

## 2. API Gateway Compatibility & Shared Redis Blacklist

### 🔗 Blacklist Incompatibility: Token vs. JTI
- **File:** [TokenBlacklistServiceImpl.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/service/impl/TokenBlacklistServiceImpl.java#L19-L35)
- **Description:** 
  `TokenBlacklistService` currently saves the entire JWT access token as the Redis key (`blacklist:<jwt_string>`). 
  However, the planned API Gateway design (detailed in [api_gateway_plan.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/markdowns/api_gateway_plan.md#L81-L146)) validates token blacklisting by extracting and checking the JTI claim (`blacklist:<jti>`). 
  Because of this mismatch, blacklisted tokens will NOT be blocked by the Gateway. Storing full tokens in Redis also wastes memory.
- **Remediation:** 
  - Modify `TokenBlacklistServiceImpl` to extract the JTI from the token using `jwtUtil.getJti(token)` and use `blacklist:<jti>` as the Redis key.

---

## 3. Performance & Architecture

### 📧 Blocking/Synchronous Email Dispatches
- **File:** [EmailServiceImpl.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/service/impl/EmailServiceImpl.java)
- **Description:** 
  Mail sending operations (verification email, reset link email) are executed synchronously. If the SMTP server (e.g., MailHog) is slow or unresponsive, user requests (like registration) will block.
- **Remediation:** 
  - Annotate `sendVerificationEmail` and `sendPasswordResetEmail` in `EmailServiceImpl` with `@Async("taskExecutor")` to execute them asynchronously in the background. (Or fully decouple via RabbitMQ and `notification-service`).
