# Implementation Plan: Resilience, Error Handling & Dead Letter Queue (DLQ)

## Section 0: Architectural Rationale & Technical Context

### 1. Architectural Overview
In an asynchronous event-driven microservices ecosystem, network degradation, external API rate-limiting, temporary SMTP server outages, and transient database/HTTP timeouts are expected occurrences. Failing to handle these transient failures gracefully can result in lost notification events or message processing loops.

To achieve enterprise-grade resilience:
1. **Transient vs. Permanent Exception Classification**: We classify exceptions into **Transient** (e.g., connection timeouts, 503 HTTP responses, temporary mail server unreachability) and **Permanent** (e.g., invalid payload, malformed email address, 400 Bad Request).
2. **Automatic Exponential Backoff Retries**: For transient failures, Spring AMQP automatically retries message processing with exponential backoff (e.g., initial interval 1000ms, multiplier 2.0, max attempts 3).
3. **Dead Letter Queue (DLQ) Rerouting**: When retries are exhausted or when a permanent exception occurs, the listener rejects the message without requeueing (`AmqpRejectAndDontRequeueException`). RabbitMQ's exchange arguments (`x-dead-letter-exchange` and `x-dead-letter-routing-key`) automatically reroute the rejected payload to `notification.dlq`.
4. **DLQ Listener & Structured Audit Logging**: A dedicated Dead Letter Queue listener consumes events from `notification.dlq`, parses the `x-death` headers (original exchange, routing key, reason, death count), and emits structured MDC-based audit logs for alerting and troubleshooting.

---

## 2. Component Design & Changes

### A. Exception Hierarchy (`com.chauhan.notificationservice.exception`)
- **`NotificationException`**: Base runtime exception.
- **`TransientNotificationException`**: Extends `NotificationException` for errors that can be solved by retrying (e.g., connection timeout, 5xx server errors).
- **`PermanentNotificationException`**: Extends `NotificationException` for unrecoverable errors (e.g., invalid payload format, 4xx client errors) that should immediately trigger DLQ routing without retrying.

### B. Updated RabbitMQ Configuration (`RabbitMQConfig.java`)
- Inject `SimpleRabbitListenerContainerFactoryConfigurer` into `@Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory` so that Spring Boot's application properties (`spring.rabbitmq.listener.simple.retry.*`) are bound.
- Configure `Jackson2JsonMessageConverter` with `ObjectMapper` (fixing the existing String conversion bug).
- Set default re-queue rejected behavior to `false` so unhandled/rejected messages flow directly to `notification.dlx` -> `notification.dlq`.

### C. Channel Implementations Refactoring
- **`EmailNotificationChannel`**: Catch transient mail errors (`MailSendException`, `MessagingException`) and throw `TransientNotificationException`.
- **`SmsNotificationChannel`**: Distinguish between Twilio API connection errors (`TransientNotificationException`) and invalid parameter errors (`PermanentNotificationException`).
- **`PushNotificationChannel`**: Handle Firebase messaging exceptions, distinguishing network glitches from invalid token/topic errors.
- **`WebhookNotificationChannel`**: Distinguish 5xx server / timeout errors (`TransientNotificationException`) from 4xx client errors (`PermanentNotificationException`).

### D. Dead Letter Queue Event Listener (`DLQEventListener.java`)
- Create `@Component DLQEventListener` listening on `notification.dlq`.
- Extract RabbitMQ headers (`x-death`, `x-first-death-reason`, `x-first-death-queue`, death count).
- Log structured warning/error details with MDC (`eventId`, `failedQueue`, `reason`, `retryCount`).

---

## 3. Verification & Testing Plan
1. **Compilation Check**: Build `notification-service` with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile`.
2. **Unit / Integration Tests**: Verify listener retry logic and DLQ routing when simulated exceptions are thrown.
3. **Checklist Update**: Update `notification-service/markdowns/checklist.md` marking Section 4 tasks as completed.
