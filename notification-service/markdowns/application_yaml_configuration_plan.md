# Application YAML Configuration Plan: notification-service

This plan outlines the configuration details for `notification-service/src/main/resources/application.yaml` (and/or `application.yml`) to support Eureka Service Registration, RabbitMQ Listener/AMQP settings, JavaMailSender SMTP properties, and external credentials for multi-channel dispatch (Twilio, Firebase, Webhooks).

---

## Section 0: Architectural Rationale & Technical Decisions

### 1. Eureka Service Registration & Discovery
* **Why:** Registering `notification-service` with Eureka (`http://localhost:8761/eureka/`) enables service discovery and client-side load balancing via Spring Cloud Netflix Eureka Client.
* **Configuration:** `eureka.client.register-with-eureka: true`, `eureka.client.fetch-registry: true`, `eureka.instance.prefer-ip-address: true`.

### 2. RabbitMQ Listener & AMQP Integration
* **Why:** `notification-service` acts as an asynchronous consumer listening on RabbitMQ queues (`notification.email.registration`). Configuring consumer retries, initial intervals, and connection parameters prevents data loss and handles temporary broker outages.
* **Configuration:** `spring.rabbitmq.host`, `port`, `username`, `password`, and `spring.rabbitmq.listener.simple.retry` settings.

### 3. JavaMailSender (SMTP) Configuration
* **Why:** Standardized Spring Boot Mail starter properties (`spring.mail`) configure SMTP connections (e.g. Gmail/Mailtrap) with STARTTLS, connection timeouts, and authentication for transactional email dispatch.
* **Configuration:** `spring.mail.host`, `port`, `username`, `password`, `properties.mail.smtp.auth`, `starttls.enable`.

### 4. External Credentials & Channel Properties (`app.notification`)
* **Why:** Externalizing sensitive SDK credentials (Twilio Account SID, Auth Token, From Phone; Firebase Admin JSON path; Webhook HMAC secret key) ensures zero hardcoded secrets and enables dynamic environment variable injection (`${ENV_VAR:default}`).
* **Configuration Structure:**
  - `app.notification.twilio.*` (SMS Channel)
  - `app.notification.firebase.*` (Push Channel)
  - `app.notification.webhook.*` (Webhook HTTP Channel)
  - `app.notification.rabbitmq.*` (Exchange & Queue references)

---

## Proposed Configuration File Details

Target File: `notification-service/src/main/resources/application.yaml` (and `application.yml` symlink or standardized file).

```yaml
server:
  port: ${PORT:8084}

spring:
  application:
    name: notification-service

  # RabbitMQ Connection & Listener Retry Configuration
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}
    username: ${SPRING_RABBITMQ_USERNAME:myuser}
    password: ${SPRING_RABBITMQ_PASSWORD:mypassword}
    listener:
      simple:
        retry:
          enabled: true
          initial-interval: 1000ms
          max-attempts: 3
          multiplier: 2.0
    template:
      retry:
        enabled: true
        initial-interval: 1000ms
        max-attempts: 3
        multiplier: 2.0

  # Spring Mail (SMTP) Configuration
  mail:
    host: ${SPRING_MAIL_HOST:smtp.gmail.com}
    port: ${SPRING_MAIL_PORT:587}
    username: ${SPRING_MAIL_USERNAME:your-email@gmail.com}
    password: ${SPRING_MAIL_PASSWORD:your-app-password}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000

# Eureka Client Configuration
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
    fetch-registry: true
    register-with-eureka: true
  instance:
    prefer-ip-address: true

# Multi-Channel Notification Properties & External Credentials
app:
  notification:
    twilio:
      account-sid: ${TWILIO_ACCOUNT_SID:AC_dummy_account_sid}
      auth-token: ${TWILIO_AUTH_TOKEN:dummy_auth_token}
      from-phone-number: ${TWILIO_FROM_PHONE_NUMBER:+1234567890}
    firebase:
      credentials-path: ${FIREBASE_CREDENTIALS_PATH:classpath:firebase-service-account.json}
    webhook:
      secret-key: ${WEBHOOK_SECRET_KEY:default_webhook_hmac_secret_key}
      connect-timeout-ms: ${WEBHOOK_CONNECT_TIMEOUT_MS:5000}
      read-timeout-ms: ${WEBHOOK_READ_TIMEOUT_MS:5000}
    rabbitmq:
      exchange: ${NOTIFICATION_EXCHANGE:notification.exchange}
      queue:
        registration: ${NOTIFICATION_QUEUE_REGISTRATION:notification.email.registration}
      routing-key:
        registration: ${NOTIFICATION_ROUTING_KEY_REGISTRATION:user.registered}
      dlx: ${NOTIFICATION_DLX:notification.dlx}
      dlq: ${NOTIFICATION_DLQ:notification.dlq}

# Logging Configuration
logging:
  level:
    com.chauhan.notificationservice: DEBUG
    org.springframework.amqp: INFO
```

---

## Verification & Execution Plan

1. Create/update `application.yaml` and `application.yml` under `notification-service/src/main/resources/`.
2. Test compilation using JDK 25 constraint: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile` inside `notification-service`.
3. Update `notification-service/markdowns/checklist.md` marking the task as completed.
4. Perform git commit adhering to Conventional Commits rules.
