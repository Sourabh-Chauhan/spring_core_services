# RabbitMQ Listener & Jackson Deserialization Implementation Plan

This plan details the step-by-step implementation for consuming event messages off RabbitMQ in `notification-service`.

---

## Section 0: Architectural Rationale & Technical Decisions

### 1. Decoupled Event DTO Definition
* **Why:** In microservices architecture, shared DTOs should not force a direct compile-time binary dependency between `auth-service` and `notification-service`. Defining localized `UserRegisteredEvent` and `PasswordResetRequestedEvent` classes in `notification-service` matching the producer's JSON structure ensures clean service boundary separation.

### 2. Jackson JSON Converter Configuration with `JavaTimeModule`
* **Why:** Standard AMQP payloads are transmitted as JSON byte arrays. Spring AMQP uses `MessageConverter` (`JacksonJsonMessageConverter`) to convert JSON into Java objects.
* **Technical Details:** 
  - `ObjectMapper` requires `JavaTimeModule` to handle Java 8 time types (`Instant`, `LocalDateTime`).
  - `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` is set to `false` so future fields added by producers will not break consumer deserialization.
  - Trusted package type mappings are configured so Jackson safely maps payload headers across different Java packages.

### 3. `@RabbitListener` Asynchronous Processing
* **Why:** The listener processes messages off the durable queue `notification.email.registration` (bound to `notification.exchange` with routing key `user.registered`).
* **DLQ Integration:** Unhandled exceptions during processing will trigger container retries; if retries are exhausted, RabbitMQ automatically routes the message to `notification.dlx` -> `notification.dlq`.

---

## Proposed Changes

### 1. Event DTOs
Create package `com.chauhan.notificationservice.event`:
* `UserRegisteredEvent.java`: `userId` (UUID), `email` (String), `name` (String), `verificationToken` (String), `timestamp` (Instant).
* `PasswordResetRequestedEvent.java`: `userId` (UUID), `email` (String), `name` (String), `resetToken` (String), `timestamp` (Instant).

### 2. Enhanced RabbitMQ Configuration
Update `com.chauhan.notificationservice.config.RabbitMQConfig`:
* Register custom `ObjectMapper` with `JavaTimeModule` and `FAIL_ON_UNKNOWN_PROPERTIES = false`.
* Configure `JacksonJsonMessageConverter` with trusted packages `*`.
* Configure `SimpleRabbitListenerContainerFactory` bean.

### 3. Listener Implementation
Create package `com.chauhan.notificationservice.listener`:
* `NotificationEventListener.java`:
  - Annotated with `@Component` and `@Slf4j`.
  - `@RabbitListener(queues = RabbitMQConfig.REGISTRATION_QUEUE_NAME)`
  - `public void handleUserRegistrationEvent(UserRegisteredEvent event)`

---

## Verification Plan

1. Compile check using JDK 25 constraint: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile -f notification-service/pom.xml`.
2. Update `notification-service/markdowns/checklist.md`.
