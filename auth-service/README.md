# Authentication & Authorization Microservice (Auth Service)

This is a robust, secure, and production-ready Authentication and Authorization microservice built using **Spring Boot
**, **Spring Security**, **PostgreSQL**, and **Redis**. It handles traditional password-based authentication, OAuth2
social login (Google & GitHub), token lifecycle management (rotation, revocation, blacklisting), and granular Role-Based
Access Control (RBAC).

---

## 🚀 Key Features

* **Authentication Models:**
    * **Password-based Auth:** Secure registration and login using salted BCrypt password hashing.
    * **Social Auth (OAuth2):** Federated login using Google and GitHub with automatic account provisioning and account
      linking (links local user account if email matches).
* **Token Lifecycle Management:**
    * **JWT Access Tokens:** Stateless, short-lived tokens containing user identity and granted authorities.
    * **JWT Refresh Tokens:** Stateful, longer-lived tokens with rotation (JTI tracking in PostgreSQL) to protect
      against replay attacks.
    * **Instant Revocation (Logout):** Access tokens are blacklisted in **Redis** with auto-expiration matches (
      preventing memory bloat).
* **Role-Based Access Control (RBAC):**
    * Coarse-grained **Roles** (e.g., `ROLE_USER`, `ROLE_ADMIN`).
    * Fine-grained **Permissions** (e.g., `user:read`, `read:financials`) mapped to Roles.
    * Method-level protection using `@PreAuthorize` annotations.
* **Account Verification & Recovery:**
    * Email verification token sent upon registration (blocks login until verified).
    * Safe "forgot password" flow with signed recovery tokens.
    * Simulated email operations logs / Local SMTP integration.

---

## 🛠️ Technology Stack

* **Language:** Java 21+ (Java 25 compatible)
* **Framework:** Spring Boot 4.1.0 & Spring Security 7.0.8 (stateless sessions)
* **Databases:**
    * **PostgreSQL:** Persistent storage for user records, roles, permissions, and active refresh token JTIs.
    * **Redis:** In-memory key-value cache for access token blacklisting.
* **Libraries:** JSON Web Tokens (JJWT), Lombok, ModelMapper, Jakarta Validation.
* **Build System:** Maven

---

## 📋 Prerequisites

Ensure you have the following installed and running:

1. **JDK 21+** (Verify with `java -version`)
2. **PostgreSQL** running on port `5001` (with database `auth_db`)
3. **Redis** running on port `6379`
4. **MailHog** (Optional) running on port `1025` for SMTP simulation

---

## ⚙️ Configuration & Environment Variables

The default development configuration is located at `src/main/resources/application-dev.yml`. You can override
parameters using the following environment variables:

| Variable                  | Description                           | Default Value          |
|---------------------------|---------------------------------------|------------------------|
| `SPRING_REDIS_HOST`       | Hostname of the Redis server          | `localhost`            |
| `SPRING_REDIS_PORT`       | Port of the Redis server              | `6379`                 |
| `SPRING_REDIS_PASSWORD`   | Password for Redis (if enabled)       | *(empty)*              |
| `JWT_SECRET`              | 512-bit signing key for JWT signature | *(auto-generated)*     |
| `JWT_ACCESS_TTL_SECONDS`  | Time-to-Live for access tokens        | `3000`                 |
| `JWT_REFRESH_TTL_SECONDS` | Time-to-Live for refresh tokens       | `86400` (24h)          |
| `GOOGLE_CLIENT_ID`        | OAuth2 Google Client ID               | `google-client-id`     |
| `GOOGLE_CLIENT_SECRET`    | OAuth2 Google Client Secret           | `google-client-secret` |
| `GITHUB_CLIENT_ID`        | OAuth2 GitHub Client ID               | `github-client-id`     |
| `GITHUB_CLIENT_SECRET`    | OAuth2 GitHub Client Secret           | `github-client-secret` |

---

## 🏁 Getting Started

### 1. Start External Services (Database & Redis)

Ensure PostgreSQL and Redis are running. If you are using Docker, you can start them with:

```bash
# Start PostgreSQL (Map host port 5001 to container port 5432)
docker run --name auth-postgres -p 5001:5432 -e POSTGRES_DB=auth_db -e POSTGRES_USER=admin_user -e POSTGRES_PASSWORD=password -d postgres:alpine

# Start Redis
docker run --name auth-redis -p 6379:6379 -d redis:alpine
```

### 2. Build the Application

Compile the code and execute dependencies mapping:

```bash
# Clean and compile
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile
```

### 3. Run the Tests

Verify the complete test suite (unit + integration tests) passes successfully:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn test
```

### 4. Run the Application

Start the auth-service application:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn spring-boot:run
```

The service will start on the default port (typically `8083` as mapped in Eureka/gateway environments).

---

## 📂 Project Architecture

```text
auth-service/
├── src/main/java/com/chauhan/authservice/
│   ├── config/         # App configs (CORS, RedisConfig, PasswordEncoderConfig)
│   ├── controller/     # REST Controllers (Auth, User, RolePermission)
│   ├── dto/            # DTO schemas & Request/Response payload models
│   ├── entity/         # JPA Database Entities (User, Role, Permission, RefreshToken)
│   ├── exceptions/     # Custom Exceptions & Centralized GlobalExceptionHandler
│   ├── repository/     # Spring Data JPA Interfaces
│   ├── security/       # Core Filter chain security configuration, JWT Filter & Handlers
│   └── service/        # Service interfaces & business implementations
└── src/main/resources/
    ├── application.yaml
    └── application-dev.yml
```

---

## 🧪 API Documentation

The complete API references detailing HTTP Method types, request JSON body models, parameter schemas, and response
formats for testing in **Postman** can be found in:

* [API Note.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/markdowns/API%20Note.md)
