# Implementation Plan: Auth Service AMQP Producer Configuration

This document outlines the detailed step-by-step plan for configuring `auth-service` as an asynchronous AMQP message producer using Spring Boot Starter AMQP and RabbitMQ.

---

## 1. Goal Overview
To enable `auth-service` to broadcast domain events (such as `UserRegisteredEvent`) asynchronously to RabbitMQ without directly coupling to mail sending or notification handling.

---

## 2. Step-by-Step Implementation Steps

### Step 1: Add Dependency to `auth-service/pom.xml`
Add `spring-boot-starter-amqp` under `<dependencies>` in [`auth-service/pom.xml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

---

### Step 2: Configure Connection Properties in `application-dev.yml`
Add RabbitMQ connection properties under `spring:` in [`auth-service/src/main/resources/application-dev.yml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/resources/application-dev.yml):
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

### Step 3: Create `RabbitMQProducerConfig.java`
Create a configuration class [`RabbitMQProducerConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/RabbitMQProducerConfig.java) in package `com.chauhan.authservice.config`.

Key Beans to configure:
1. **TopicExchange (`notification.exchange`)**: Ensures the exchange exists when `auth-service` starts.
2. **Jackson2JsonMessageConverter (`MessageConverter`)**: Serializes Java DTOs (`UserRegisteredEvent`) into standard JSON payloads.
3. **RabbitTemplate**: Injected with `Jackson2JsonMessageConverter` so calls to `rabbitTemplate.convertAndSend(...)` automatically format event objects into JSON bytes.

---

### Step 4: Verification & Build Validation
1. Execute `./mvnw clean compile -DskipTests` in `auth-service` to verify code compiles cleanly.
2. Verify Spring context loading and bean registration for `RabbitTemplate` and `Jackson2JsonMessageConverter`.
