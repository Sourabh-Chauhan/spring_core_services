# Implementation Plan: Token Blacklist using Redis

This plan details the steps required to implement a secure, high-performance token blacklist using Redis to invalidate stateless JWT access tokens upon logout.

---

## 1. Prerequisites & Dependencies

We will add the Spring Boot Redis starter to the `pom.xml` file.

* **File to modify:** [pom.xml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/pom.xml)
* **Dependency to add:**
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  ```

---

## 2. Configuration Properties

We will configure Redis connection parameters (host, port, password, etc.) in the properties files. We will use standard defaults (`localhost:6379`) with environment variable overrides.

* **File to modify:** [application-dev.yml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/resources/application-dev.yml)
* **Configurations to add:**
  ```yaml
  spring:
    data:
      redis:
        host: ${SPRING_REDIS_HOST:localhost}
        port: ${SPRING_REDIS_PORT:6379}
        password: ${SPRING_REDIS_PASSWORD:}
  ```

---

## 3. Redis Setup & Configuration Bean

We will create a configuration class to configure Spring Data Redis, specifically establishing a `StringRedisTemplate` to read and write key-value pairs (where key is `blacklist:<token>` and value is a static indicator like `true` or `"blacklisted"`).

* **New File to create:** `com.chauhan.authservice.config.RedisConfig`
* **Contents:**
  * Define a `@Bean` returning a `RedisTemplate<String, String>` or utilize `StringRedisTemplate` directly.

---

## 4. Helper Methods in JwtUtil

To blacklist a token, we must compute the exact remaining duration before it naturally expires. This allows us to set the TTL in Redis accordingly, ensuring that Redis automatically garbage collects expired tokens to conserve memory.

* **File to modify:** [JwtUtil.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/security/JwtUtil.java)
* **Method to add:**
  ```java
  public long getRemainingTtlSeconds(String token) {
      Claims claims = getAllClaimsFromToken(token);
      Date expiration = claims.getExpiration();
      long remainingMillis = expiration.getTime() - System.currentTimeMillis();
      return Math.max(0, remainingMillis / 1000);
  }
  ```

---

## 5. Token Blacklist Service

We will define an interface and implementation for interacting with Redis to manage the token blacklist.

* **New Interface:** `com.chauhan.authservice.service.TokenBlacklistService`
* **New Implementation:** `com.chauhan.authservice.service.impl.TokenBlacklistServiceImpl`
* **Responsibilities:**
  * `void blacklistToken(String token, long ttlSeconds)`: Sets `blacklist:<token>` in Redis with the given expiration time.
  * `boolean isTokenBlacklisted(String token)`: Checks if `blacklist:<token>` exists in Redis.

---

## 6. Update Security Filter (JwtFilter)

We will intercept requests in `JwtFilter` to ensure that even if a token is cryptographically valid (valid signature, not expired), it is rejected if it is present in the Redis blacklist.

* **File to modify:** [JwtFilter.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/security/JwtFilter.java)
* **Changes:**
  * Inject `TokenBlacklistService`.
  * After verifying that a JWT is present and parses properly, perform a blacklist check:
    ```java
    if (tokenBlacklistService.isTokenBlacklisted(jwt)) {
        logger.debug("JWT token is blacklisted.");
        request.setAttribute("jwt_error", "Token is blacklisted.");
        filterChain.doFilter(request, response);
        return;
    }
    ```

---

## 7. Revoke Token on Logout

We will update the logout endpoints to retrieve the active access token and black list it.

* **Files to modify:**
  * `AuthService.java` / [AuthServiceImpl.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/service/impl/AuthServiceImpl.java)
  * [AuthController.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/controller/AuthController.java)
* **Changes:**
  * Modify `AuthService.logout` signature to: `void logout(String accessToken, String refreshToken)`.
  * In [AuthServiceImpl.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/service/impl/AuthServiceImpl.java#L112-L116):
    * Calculate the remaining TTL of the `accessToken` using `jwtUtil.getRemainingTtlSeconds(accessToken)`.
    * Call `tokenBlacklistService.blacklistToken(accessToken, ttlSeconds)`.
    * Delegate refresh token revocation to `refreshTokenService.revokeRefreshToken(refreshToken)`.
  * In [AuthController.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/controller/AuthController.java#L88-L97):
    * Extract the `accessToken` from the `Authorization` header.
    * Extract the `refreshToken` from the request.
    * Pass both to `authService.logout(accessToken, refreshToken)`.
