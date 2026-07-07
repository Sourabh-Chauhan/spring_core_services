# Implementation Plan: Scheduled Cleanup Jobs

This plan details the implementation of scheduled background tasks in `auth-service` to periodically purge expired tokens (verification, password reset, and refresh tokens) and old audit log entries.

---

## 0. Rationale: Why Auth Service and not API Gateway?
1. **Database Ownership (Loose Coupling):** The database tables for tokens and audit logs belong exclusively to `auth-service`. In a microservice architecture, only the owner service can modify its database tables.
2. **Separation of Concerns:** The API Gateway's job is routing, load balancing, rate limiting, and global filter handling. Background scheduling tasks that execute database operations belong in backend services to keep the gateway fast and stateless.

---

## 1. Objectives
* Enable Spring Scheduling in the application.
* Define bulk delete queries in the respective JpaRepositories.
* Create a configurable log retention property in `application-dev.yml`.
* Build a scheduled task runner (`CleanupScheduler.java`) that executes a cleanup routine daily at midnight.

---

## 2. Detailed Steps

### A. Add Query Methods to Repositories
Add `@Modifying` query methods to delete expired entries:

1. **`VerificationTokenRepository`** ([VerificationTokenRepository.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/repository/VerificationTokenRepository.java)):
   ```java
   @Modifying
   void deleteByExpiryDateBefore(java.time.Instant now);
   ```

2. **`PasswordResetTokenRepository`** ([PasswordResetTokenRepository.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/repository/PasswordResetTokenRepository.java)):
   ```java
   @Modifying
   void deleteByExpiryDateBefore(java.time.Instant now);
   ```

3. **`RefreshTokenRepository`** ([RefreshTokenRepository.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/repository/RefreshTokenRepository.java)):
   ```java
   @Modifying
   void deleteByExpiresAtBefore(java.time.Instant now);
   ```

4. **`AuditLogRepository`** ([AuditLogRepository.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/repository/AuditLogRepository.java)):
   ```java
   @Modifying
   void deleteByTimestampBefore(java.time.Instant cutoff);
   ```

### B. Configuration Properties
Define a retention period for old log entries.

* **Target File:** [application-dev.yml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/resources/application-dev.yml)

```yaml
app:
  scheduling:
    audit-log-retention-days: 30
```

### C. Enable Spring Scheduling
Add `@EnableScheduling` to enable scheduling capabilities.

* **Target File:** [AuthServiceApplication.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/AuthServiceApplication.java)

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuthServiceApplication { ... }
```

### D. Create the Cleanup Scheduler
* **File Location:** `com.chauhan.authservice.scheduler.CleanupScheduler` ([CleanupScheduler.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/scheduler/CleanupScheduler.java))

```java
package com.chauhan.authservice.scheduler;

import com.chauhan.authservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {

    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.scheduling.audit-log-retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "0 0 0 * * ?") // Run every day at midnight
    @Transactional
    public void cleanupExpiredEntries() {
        log.info("Starting scheduled cleanup of expired tokens and old audit logs...");
        Instant now = Instant.now();

        try {
            // 1. Clean expired verification tokens
            verificationTokenRepository.deleteByExpiryDateBefore(now);
            log.info("Cleaned up expired verification tokens.");

            // 2. Clean expired password reset tokens
            passwordResetTokenRepository.deleteByExpiryDateBefore(now);
            log.info("Cleaned up expired password reset tokens.");

            // 3. Clean expired refresh tokens
            refreshTokenRepository.deleteByExpiresAtBefore(now);
            log.info("Cleaned up expired refresh tokens.");

            // 4. Clean audit logs older than N days
            Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
            auditLogRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up audit logs older than {} days.", retentionDays);

        } catch (Exception e) {
            log.error("Error occurred during scheduled cleanup: ", e);
        }
    }
}
```

---

## 3. Verification Plan
1. Run `mvn clean compile` to check for compilation/lint errors.
2. Create a unit test `CleanupSchedulerTests.java` inside `src/test/java` that saves expired tokens and old logs, triggers the scheduler method manually, and asserts that they are deleted from the database.
