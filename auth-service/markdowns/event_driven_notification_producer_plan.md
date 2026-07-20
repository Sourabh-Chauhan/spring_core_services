# Architectural Design & Implementation Plan: Event-Driven Notification Producer (`auth-service`)

This plan details the technical changes made to `auth-service` to decouple notification and email delivery into an asynchronous, event-driven producer architecture using RabbitMQ AMQP messaging.

---

## 1. Architectural Overview & Rationale

Previously, `auth-service` sent emails synchronously during user signup, verification resend, and password reset requests. Synchronous SMTP connections introduce user-facing latency (2-5s) and create direct dependency vulnerabilities (e.g. SMTP server outages taking down auth registration).

By introducing **AMQP Messaging with RabbitMQ**:
* `auth-service` acts strictly as an **Event Producer**.
* Domain events (`UserRegisteredEvent`, `PasswordResetRequestedEvent`) are published to the topic exchange `notification.exchange`.
* `auth-service` no longer requires `JavaMailSender` or direct SMTP configuration.
* HTTP requests (e.g. `POST /api/v1/auth/register`) return instant HTTP 201 Created responses.

---

## 2. Event Architecture & Topic Exchange Routing

```text
               +-------------------+
               |   auth-service    |
               +---------+---------+
                         |
                         | (Publish Event DTOs as JSON)
                         v
      +------------------------------------------+
      | Topic Exchange: notification.exchange     |
      +--------------------+---------------------+
                           |
            +--------------+--------------+
            |                             |
Routing Key: "user.registered"   Routing Key: "user.password-reset"
            |                             |
            v                             v
+-----------------------+     +------------------------+
| Queue:                |     | Queue:                 |
| notification.email.   |     | notification.email.    |
| registration          |     | password-reset         |
+-----------------------+     +------------------------+
```

---

## 3. Implementation Summary & Code Changes

### Step 1: Added Spring Boot Starter AMQP Dependency
Added `spring-boot-starter-amqp` to [`auth-service/pom.xml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

---

### Step 2: Configured RabbitMQ Connection Properties
Added RabbitMQ properties to [`auth-service/src/main/resources/application-dev.yml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/resources/application-dev.yml):
```yaml
spring:
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}
    username: ${SPRING_RABBITMQ_USERNAME:myuser}
    password: ${SPRING_RABBITMQ_PASSWORD:mypassword}
    template:
      retry:
        enabled: true
        initial-interval: 1000ms
        max-attempts: 3
        multiplier: 2.0
```

---

### Step 3: Created Producer Configuration Class
Created [`RabbitMQProducerConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/RabbitMQProducerConfig.java):
* Declares `TopicExchange` (`notification.exchange`).
* Declares constants:
  - `NOTIFICATION_EXCHANGE = "notification.exchange"`
  - `ROUTING_KEY_USER_REGISTERED = "user.registered"`
  - `ROUTING_KEY_PASSWORD_RESET = "user.password-reset"`
* Configures `Jackson2JsonMessageConverter` for serializing event objects into standard JSON payloads.
* Configures `RabbitTemplate` with Jackson JSON conversion.

---

### Step 4: Declared Event DTO Models
Created dedicated event DTOs in package `com.chauhan.authservice.event`:
1. [`UserRegisteredEvent.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/event/UserRegisteredEvent.java):
   ```java
   public class UserRegisteredEvent implements Serializable {
       private UUID userId;
       private String email;
       private String name;
       private String verificationToken;
       private Instant timestamp;
   }
   ```
2. [`PasswordResetRequestedEvent.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/event/PasswordResetRequestedEvent.java):
   ```java
   public class PasswordResetRequestedEvent implements Serializable {
       private UUID userId;
       private String email;
       private String name;
       private String resetToken;
       private Instant timestamp;
   }
   ```

---

### Step 5: Refactored `AuthServiceImpl` & Removed Direct Mail Dependencies
* Replaced `EmailService` with `RabbitTemplate` in [`AuthServiceImpl.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/service/impl/AuthServiceImpl.java).
* Refactored `registerUser(...)` and `resendVerification(...)` to convert and send `UserRegisteredEvent` via `rabbitTemplate.convertAndSend(...)`.
* Refactored `forgotPassword(...)` to convert and send `PasswordResetRequestedEvent`.
* Removed `EmailService.java`, `EmailServiceImpl.java`, `spring-boot-starter-mail` dependency, and obsolete `spring.mail` properties from `application-dev.yml`.

---

## 4. Verification & Testing Strategy

1. **Compilation Validation**:
   - `mvn clean compile -DskipTests` in `auth-service` compiled cleanly with 0 errors.
2. **End-to-End Verification Flow**:
   - Trigger registration via `POST /api/v1/auth/register`.
   - Verify event presence in RabbitMQ exchange `notification.exchange` and queue `notification.email.registration`.
   - Confirm consumption by `notification-service`.
