# Implementation Plan: Notification Service Dependencies & Project Setup

This plan details the dependency review, technical rationale, and setup steps for bootstrapping `notification-service` to support multi-channel notifications (Email, SMS, Push, Webhooks) and Eureka service discovery.

---

## Section 0: Technical Rationale for Dependencies & Architecture

### 1. `spring-boot-starter-amqp`
* **Purpose:** Provides Spring AMQP infrastructure (`@RabbitListener`, `RabbitTemplate`, `MessageConverter`) to consume events asynchronously from RabbitMQ exchanges and queues (e.g. `notification.email.registration`).
* **Why Required:** Connects `notification-service` to RabbitMQ broker as an event consumer.

### 2. `spring-boot-starter-mail`
* **Purpose:** Integrates Spring's `JavaMailSender` abstraction and JavaMail API.
* **Why Required:** Executes SMTP delivery for verification emails, welcome messages, and password resets via MailHog / Gmail SMTP servers.

### 3. `spring-cloud-starter-netflix-eureka-client`
* **Purpose:** Enrolls `notification-service` as an active microservice client in the Eureka Service Registry (`Eureka-server`).
* **Why Required:** Enables dynamic service discovery, health monitoring, and routing across the Spring Cloud microservices ecosystem.

### 4. `spring-boot-starter-webflux`
* **Purpose:** Provides the reactive, non-blocking HTTP client (`WebClient`).
* **Why Required:** Executes non-blocking HTTP POST requests to third-party client endpoints for Webhook callbacks without consuming worker threads.

### 5. `com.twilio.sdk:twilio`
* **Purpose:** Official Java SDK for Twilio REST APIs.
* **Why Required:** Powers `SmsNotificationChannel` to dispatch SMS notifications globally to mobile numbers.

### 6. `com.google.firebase:firebase-admin`
* **Purpose:** Official Google Firebase Admin SDK for Java.
* **Why Required:** Powers `PushNotificationChannel` to send push alerts (`FirebaseMessaging`) to Android, iOS, and Web devices.

### 7. `org.projectlombok:lombok`
* **Purpose:** Boilerplate code generator (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`).
* **Why Required:** Maintains clean, readable, and concise DTOs and strategy channel classes.

---

## Step-by-Step Execution Plan

### Step 1: Update Root `pom.xml` Dependency Management
1. Add version properties under `<properties>` in [`pom.xml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/pom.xml):
   ```xml
   <twilio.version>10.6.0</twilio.version>
   <firebase-admin.version>9.4.3</firebase-admin.version>
   ```
2. Add dependency definitions to `<dependencyManagement>` in root `pom.xml`.

### Step 2: Update `notification-service/pom.xml`
Add dependencies for AMQP, Mail, WebFlux, Eureka Client, Twilio, Firebase Admin, and Lombok to [`notification-service/pom.xml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/notification-service/pom.xml).

### Step 3: Verification & Compilation
Run Maven compile using JDK 25 constraint:
```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile -DskipTests
```
Ensure 0 compilation or dependency resolution errors.
