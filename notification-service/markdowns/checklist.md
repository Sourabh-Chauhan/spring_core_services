# Notification Service Implementation Goal Checklist

This checklist tracks the step-by-step development and integration of the decoupled, asynchronous `notification-service` based on the architectural plan in [`notification_service_design_plan.md`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/markdowns/notification_service_design_plan.md).

---

## 1. Message Broker & Infrastructure Setup
- [x] **Docker & RabbitMQ Configuration**
  - [x] Run `rabbitmq:management-alpine` container via `.run/RabbitMQ.run.xml` (ports `5672` for AMQP, `15672` for Management Web UI). Credentials: `myuser` / `mypassword`.
  - [x] Verify RabbitMQ service starts and management console is accessible at `http://localhost:15672`.
- [x] **Exchange & Queue Architecture**
  - [x] Define Topic Exchange: `notification.exchange`.
  - [x] Define Queue: `notification.email.registration`.
  - [x] Define Routing Key: `user.registered`.
  - [x] Configure Dead Letter Exchange (`notification.dlx`) and Dead Letter Queue (`notification.dlq`) for failed message handling in [`RabbitMQConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/notification-service/src/main/java/com/chauhan/notificationservice/config/RabbitMQConfig.java).

---

## 2. Refactor Producer (`auth-service`)
- [x] **Dependencies & Configurations**
  - [x] Add `spring-boot-starter-amqp` to [`auth-service/pom.xml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/pom.xml).
  - [x] Configure `RabbitTemplate` and Jackson JSON Message Converter in [`RabbitMQProducerConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/RabbitMQProducerConfig.java).
  - [x] Configure RabbitMQ connection properties in [`application-dev.yml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/resources/application-dev.yml).
- [x] **Event Declaration & Publishing**
  - [x] Create `UserRegisteredEvent` DTO in [`UserRegisteredEvent.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/event/UserRegisteredEvent.java) and `PasswordResetRequestedEvent` in [`PasswordResetRequestedEvent.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/event/PasswordResetRequestedEvent.java).
  - [x] Refactor registration flow in [`AuthServiceImpl.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/service/impl/AuthServiceImpl.java) to publish `UserRegisteredEvent` to `notification.exchange` with routing key `user.registered`.
  - [x] Remove synchronous JavaMailSender / mail dispatch calls and `EmailService` from `auth-service`.

---

## 3. Bootstrap & Core Development (`notification-service`)
- [x] **Project Setup & Dependencies**
  - [x] Review and update [`notification-service/pom.xml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/notification-service/pom.xml) and root [`pom.xml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/pom.xml) dependencies:
    - [x] `spring-boot-starter-amqp`
    - [x] `spring-boot-starter-mail`
    - [x] `spring-cloud-starter-netflix-eureka-client`
    - [x] `spring-boot-starter-webflux` (for non-blocking WebClient)
    - [x] `com.twilio.sdk:twilio` (v10.6.0)
    - [x] `com.google.firebase:firebase-admin` (v9.4.3)
    - [x] `org.projectlombok:lombok`
  - [x] Configure `application.yml` for Eureka registration, RabbitMQ listener, mail server settings, and external credentials.
- [x] **Listener Implementation**
  - [x] Create Jackson JSON deserialization configuration for RabbitMQ listener.
  - [x] Implement `@RabbitListener` method listening on `notification.email.registration` queue.
- [x] **Polymorphic Notification Strategy Architecture**
  - [x] Create `NotificationChannel` interface (`supports(NotificationType type)`, `send(NotificationPayload payload)`).
  - [x] Create `NotificationDispatcher` component to route payloads based on user preferences.
  - [x] **Channel 1: Email Notification Channel**
    - [x] Implement `EmailNotificationChannel` using `JavaMailSender` and HTML template rendering.
  - [x] **Channel 2: SMS Notification Channel**
    - [x] Implement `SmsNotificationChannel` using Twilio / AWS SNS SDK.
  - [x] **Channel 3: Push Notification Channel**
    - [x] Implement `PushNotificationChannel` using Firebase `firebase-admin` SDK.
  - [x] **Channel 4: Webhook Notification Channel**
    - [x] Implement `WebhookNotificationChannel` using non-blocking `WebClient`.
    - [x] Add HMAC-SHA256 signature generation attaching `X-Hub-Signature-256` header for payload verification.

---

## 4. Resilience, Error Handling & DLQ
- [ ] Implement automatic retries with exponential backoff for transient failures (e.g. SMTP/HTTP timeout).
- [ ] Route unprocessable or repeatedly failing messages to Dead Letter Queue (`DLQ`).
- [ ] Add structured logging and error handling across all dispatch channels.

---

## 5. End-to-End Integration Verification
- [ ] Start Eureka Server, Gateway, Auth Service, Notification Service, and RabbitMQ.
- [ ] Send `POST /api/v1/auth/register` request via API Gateway.
- [ ] Verify HTTP 201 Created instant response from `auth-service` without blocking on notification processing.
- [ ] Verify RabbitMQ event publication and consumption logs in `notification-service`.
- [ ] Verify execution across channels (Email, SMS, Push, Webhook).
