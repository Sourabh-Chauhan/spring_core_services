# Spring Core Services - Multi-Module Microservices Architecture

Welcome to **Spring Core Services**, an enterprise-grade, event-driven, multi-module microservices project built with Spring Boot, Spring Cloud, Netflix Eureka, Spring Cloud Gateway, PostgreSQL, Redis, and RabbitMQ.

---

## 🏗 System Architecture & Microservices Overview

This project is structured as a Maven multi-module application containing the following core services:

```text
                               ┌───────────────────────────────────┐
                               │     Client / Frontend (HTTP)     │
                               └─────────────────┬─────────────────┘
                                                 │
                                                 ▼
                               ┌───────────────────────────────────┐
                               │   API Gateway (gateway-service)   │
                               │           Port: 8080              │
                               └─────────┬─────────────────┬───────┘
                                         │                 │
                                         ▼                 ▼
                       ┌───────────────────┐             ┌───────────────────────────┐
                       │  Eureka Server    │             │   Auth & Identity Service │
                       │ (Eureka-server)   │             │      (auth-service)       │
                       │    Port: 8761     │             │        Port: 8083         │
                       └───────────────────┘             └─────────────┬─────────────┘
                                                                       │ (Publishes AMQP Event)
                                                                       ▼
                                                         ┌───────────────────────────┐
                                                         │     RabbitMQ Broker       │
                                                         │    (notification.exchange)│
                                                         └─────────────┬─────────────┘
                                                                       │ (Consumes AMQP Event)
                                                                       ▼
                                                         ┌───────────────────────────┐
                                                         │   Notification Service    │
                                                         │   (notification-service)  │
                                                         │        Port: 8084         │
                                                         └───────────────────────────┘
```

### Module Breakdown

| Module | Port | Technology Stack | Description |
| :--- | :--- | :--- | :--- |
| **`Eureka-server`** | `8761` | Spring Cloud Netflix Eureka Server | Centralized service registry for dynamic discovery and health monitoring. |
| **`gateway-service`** | `8080` | Spring Cloud Gateway (WebFlux), Redis, Resilience4j | Single API entry-point providing JWT validation, Redis IP rate limiting, circuit breakers, and CORS handling. |
| **`auth-service`** | `8083` | Spring Boot, Spring Security, PostgreSQL, Flyway, Redis, AMQP | Identity management, OAuth2 (Google/GitHub), JWT tokens, session blacklisting (Redis), and event publishing to RabbitMQ. |
| **`notification-service`** | `8084` | Spring Boot, Spring AMQP, JavaMailSender, Twilio, Firebase | Asynchronous multi-channel notification dispatcher (Email, SMS, Push, Webhooks) with Dead Letter Queue (DLQ) recovery. |

---

## 🧰 Infrastructure Prerequisites

Ensure you have the following installed on your system before setting up the project:

- **JDK:** Java 21 or Java 25 (`java -version`)
- **Build Tool:** Apache Maven 3.9+ (`mvn -v`)
- **Container Runtime:** Docker Desktop or Docker Engine (`docker --version`)

---

## 🔐 Environment & Credentials Configuration

To keep systems secure, credentials (database passwords, RabbitMQ credentials, SMTP mail logins, OAuth secrets) are externalized via environment variables. Configure your own credentials before running services.

| Environment Variable       | Description                               | Default Fallback            |
|:---------------------------|:------------------------------------------|:----------------------------|
| `SPRING_RABBITMQ_HOST`     | Host address for RabbitMQ broker          | `localhost`                 |
| `SPRING_RABBITMQ_PORT`     | Port for RabbitMQ broker                  | `5672`                      |
| `SPRING_RABBITMQ_USERNAME` | Custom RabbitMQ username                  | `<your-rabbitmq-user>`      |
| `SPRING_RABBITMQ_PASSWORD` | Custom RabbitMQ password                  | `<your-rabbitmq-password>`  |
| `SPRING_MAIL_USERNAME`     | SMTP Email login username                 | `<your-email@domain.com>`   |
| `SPRING_MAIL_PASSWORD`     | SMTP Email application password           | `<your-app-password>`       |
| `TWILIO_ACCOUNT_SID`       | Twilio Account SID (SMS Channel)          | `<your-twilio-account-sid>` |
| `TWILIO_AUTH_TOKEN`        | Twilio Auth Token (SMS Channel)           | `<your-twilio-auth-token>`  |
| `JWT_SECRET`               | Secret key for JWT signature verification | `<your-custom-jwt-secret>`  |

---

## 🚀 Step-by-Step Setup Guide

### 1. Clone the Repository

```bash
git clone https://github.com/Sourabh-Chauhan/spring_core_services.git
cd spring_core_services
```

### 2. Start Infrastructure Containers (Docker)

Spin up container instances using your own custom credentials:

```bash
# 1. PostgreSQL Database (Port 5001)
docker run -d \
  --name postgres-auth \
  -p 5001:5432 \
  -e POSTGRES_DB=auth_db \
  -e POSTGRES_USER=<your-postgres-user> \
  -e POSTGRES_PASSWORD=<your-postgres-password> \
  postgres:latest

# 2. Redis Server (Port 6379)
docker run -d \
  --name redis-server \
  -p 6379:6379 \
  redis:alpine

# 3. RabbitMQ Broker with Management Console (Ports 5672, 15672)
docker run -d \
  --name rabbitmq-service \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=<your-rabbitmq-user> \
  -e RABBITMQ_DEFAULT_PASS=<your-rabbitmq-password> \
  rabbitmq:management-alpine
```

### 3. Build the Project

Run Maven clean package from the root project folder. If using JDK 25, pass your local `JAVA_HOME`:

```bash
# Linux / macOS
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean package -DskipTests

# Windows / Standard Maven
mvn clean package -DskipTests
```

---

## 🚦 Service Launch Order

Launch microservices in the following order, supplying your custom environment credentials:

### Step 1: Start Eureka Discovery Server
```bash
cd Eureka-server
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn spring-boot:run
```
*Dashboard accessible at:* `http://localhost:8761`

### Step 2: Start Auth Service
```bash
cd ../auth-service
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn spring-boot:run
```
*Port:* `8083` (Automatically applies Flyway DB migrations to `auth_db`).

### Step 3: Start Notification Service
```bash
cd ../notification-service
SPRING_RABBITMQ_USERNAME=<your-rabbitmq-user> \
SPRING_RABBITMQ_PASSWORD=<your-rabbitmq-password> \
SPRING_MAIL_USERNAME=<your-email@gmail.com> \
SPRING_MAIL_PASSWORD=<your-app-password> \
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn spring-boot:run
```
*Port:* `8084` (Listens asynchronously on RabbitMQ queue `notification.email.registration`).

### Step 4: Start API Gateway
```bash
cd ../gateway-service
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn spring-boot:run
```
*Port:* `8080` (Routes HTTP requests to microservices dynamically via Eureka).

---

## 📡 Essential API Routes & Testing

All client requests route through the API Gateway at `http://localhost:8080`.

### 1. User Registration
* **Endpoint:** `POST http://localhost:8080/api/v1/auth/register`
* **Request Body:**
```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "name": "Jane Doe"
}
```
* **Behavior:** `auth-service` saves the user, returns HTTP `201 Created`, and publishes a `UserRegisteredEvent` to RabbitMQ. `notification-service` consumes the event asynchronously and sends an HTML welcome email.

### 2. User Login
* **Endpoint:** `POST http://localhost:8080/api/v1/auth/login`
* **Request Body:**
```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```
* **Response:** Returns JWT access and refresh tokens.

---

## 🌐 Infrastructure Management Consoles

* **Eureka Service Discovery:** `http://localhost:8761`
* **RabbitMQ Management Console:** `http://localhost:15672` (Login using your configured `<your-rabbitmq-user>` & `<your-rabbitmq-password>`)
