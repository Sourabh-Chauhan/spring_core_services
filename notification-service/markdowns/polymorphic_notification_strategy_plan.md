# Polymorphic Notification Strategy & Email Channel Implementation Plan

This plan details the design and implementation of the **Polymorphic Notification Strategy Architecture** and the **Email Notification Channel** using Spring `JavaMailSender` for the `notification-service`.

---

## Section 0: Architectural Rationale & Technical Decisions

### 1. Strategy Pattern for Multi-Channel Routing
* **Why:** Microservices often need to send alerts across multiple channels (Email, SMS, Push, Webhooks). Using a Strategy Pattern (`NotificationChannel` interface) decoupled from the event listener enables open-closed design (SOLID). New channels (e.g. Twilio SMS, Firebase Push) can be added without modifying existing dispatch logic or listener classes.

### 2. `NotificationDispatcher` Component
* **Why:** Spring automatically injects all beans implementing `NotificationChannel` into `List<NotificationChannel>`. The `NotificationDispatcher` queries `channel.supports(type)` and invokes `channel.send(payload)` on matching implementations.

### 3. Email Channel (`EmailNotificationChannel`) & HTML Email Support
* **Why:** `EmailNotificationChannel` leverages Spring's `JavaMailSender` and `MimeMessageHelper` to send multipart HTML emails with styled HTML formatting, custom subjects, and recipient addressing. 

---

## Proposed Classes & Package Structure

### 1. Domain Models (`com.chauhan.notificationservice.model`)
* `NotificationType.java`: Enum containing `EMAIL`, `SMS`, `PUSH`, `WEBHOOK`.
* `NotificationPayload.java`: Model containing:
  - `recipient`: String (e.g. target email address)
  - `subject`: String
  - `body`: String (HTML or text body)
  - `type`: `NotificationType`
  - `metadata`: `Map<String, Object>` (extra contextual attributes)

### 2. Strategy Interface (`com.chauhan.notificationservice.channel`)
* `NotificationChannel.java`:
  - `boolean supports(NotificationType type)`
  - `void send(NotificationPayload payload)`

### 3. Email Channel Implementation (`com.chauhan.notificationservice.channel.impl`)
* `EmailNotificationChannel.java`:
  - Implemented `@Component` bean implementing `NotificationChannel`.
  - Implements `supports(NotificationType.EMAIL)`.
  - Uses `JavaMailSender` to create a `MimeMessage`, sets `MimeMessageHelper(mimeMessage, true, "UTF-8")`, recipient, subject, and HTML text, then calls `mailSender.send(mimeMessage)`.

### 4. Notification Dispatcher (`com.chauhan.notificationservice.dispatcher`)
* `NotificationDispatcher.java`:
  - Injects `List<NotificationChannel> channels`.
  - Method `public void dispatch(NotificationPayload payload)` iterates over channels and dispatches to supporting channels.

### 5. Integration with `NotificationEventListener`
* Update `NotificationEventListener.java` to construct `NotificationPayload` for `UserRegisteredEvent` and delegate to `NotificationDispatcher`.

---

## Verification Plan

1. Compile check using JDK 25 constraint: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile -f notification-service/pom.xml`.
2. Update `notification-service/markdowns/checklist.md`.
3. Commit changes adhering to Conventional Commit standards.
