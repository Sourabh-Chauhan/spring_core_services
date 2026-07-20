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
- [ ] **Dependencies & Configurations**
  - [ ] Add `spring-boot-starter-amqp` to `auth-service/pom.xml`.
  - [ ] Configure `RabbitTemplate` and Jackson JSON Message Converter (`Jackson2JsonMessageConverter`).
  - [ ] Configure RabbitMQ connection properties in `application.yml`.
- [ ] **Event Declaration & Publishing**
  - [ ] Create `UserRegisteredEvent` DTO (`userId`, `email`, `fullName`, `timestamp`, etc.).
  - [ ] Refactor registration flow in `auth-service` to publish `UserRegisteredEvent` to `notification.exchange` with routing key `user.registered`.
  - [ ] Remove synchronous JavaMailSender / mail dispatch calls from `auth-service`.

---

## 3. Bootstrap & Core Development (`notification-service`)
- [ ] **Project Setup & Dependencies**
  - [ ] Review and update `notification-service/pom.xml` dependencies:
    - [ ] `spring-boot-starter-amqp`
    - [ ] `spring-boot-starter-mail`
    - [ ] `spring-cloud-starter-netflix-eureka-client`
    - [ ] `spring-boot-starter-webflux` (for non-blocking WebClient)
    - [ ] `com.twilio.sdk:twilio`
    - [ ] `com.google.firebase:firebase-admin`
  - [ ] Configure `application.yml` for Eureka registration, RabbitMQ listener, mail server settings, and external credentials.
- [ ] **Listener Implementation**
  - [ ] Create Jackson JSON deserialization configuration for RabbitMQ listener.
  - [ ] Implement `@RabbitListener` method listening on `notification.email.registration` queue.
- [ ] **Polymorphic Notification Strategy Architecture**
  - [ ] Create `NotificationChannel` interface (`supports(NotificationType type)`, `send(NotificationPayload payload)`).
  - [ ] Create `NotificationDispatcher` component to route payloads based on user preferences.
  - [ ] **Channel 1: Email Notification Channel**
    - [ ] Implement `EmailNotificationChannel` using `JavaMailSender` and HTML template rendering.
  - [ ] **Channel 2: SMS Notification Channel**
    - [ ] Implement `SmsNotificationChannel` using Twilio / AWS SNS SDK.
  - [ ] **Channel 3: Push Notification Channel**
    - [ ] Implement `PushNotificationChannel` using Firebase `firebase-admin` SDK.
  - [ ] **Channel 4: Webhook Notification Channel**
    - [ ] Implement `WebhookNotificationChannel` using non-blocking `WebClient`.
    - [ ] Add HMAC-SHA256 signature generation attaching `X-Hub-Signature-256` header for payload verification.

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
