# Implementation Plan: Database Migrations with Flyway

This plan details the setup and configuration of **Flyway** in the `auth-service` module to manage schema versioning and database migrations.

---

## 0. Rationale: Why Flyway instead of Hibernate Auto-DDL?

While Hibernate's `ddl-auto: update` is convenient for quick local prototyping, it is highly discouraged and unsafe for production-grade microservices for several reasons:

1. **Lack of Versioning and History:** 
   Hibernate silently modifies the schema on startup but keeps no record of *what* changed, *when*, or *why*. Flyway maintains a version history table (`flyway_schema_history`) recording every migration's date, execution time, and checksum.
2. **Inability to Handle Complex Schema Changes:** 
   If you rename a column, Hibernate cannot intelligently migrate data; it will simply create a new column and leave the old one orphaned. Flyway allows executing both DDL (schema) and DML (data migration) in raw SQL, ensuring no data loss.
3. **Determinism and Environmental Parity:** 
   Using raw SQL migration scripts ensures that Dev, QA, Staging, and Production databases are updated in the exact same sequence. Hibernate auto-update can result in different indexes or column constraints across environments due to database-specific state or driver versions.
4. **Production Security:** 
   Running applications with administrative rights to modify database schemas (`ddl-auto`) is a major security risk. Production applications should run with restricted DML privileges (SELECT/INSERT/UPDATE/DELETE), while schema changes should be handled independently in deployment pipelines using tools like Flyway.

---

## 1. Objectives
* Add Flyway migration dependencies for PostgreSQL.
* Transition database schema management from Hibernate's `ddl-auto: update` to Flyway migrations (`ddl-auto: validate`).
* Write the initial migration script (`V1__initial_schema.sql`) covering all existing JPA entities:
  * `permissions`
  * `roles`
  * `role_permissions` (Join table)
  * `users`
  * `user_roles` (Join table)
  * `verification_tokens`
  * `password_reset_tokens`
  * `refresh_tokens`
  * `audit_logs` (from the recent Auditing implementation)
* Seed initial roles (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_GUEST`) to prevent startup lag or empty states.

---

## 2. Configuration & Dependency Changes

### A. Dependencies in `pom.xml`
Add the standard Spring Boot starter for Flyway along with the PostgreSQL database module (required since Flyway 9+):

* **Target File:** [pom.xml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/pom.xml)

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

### B. Spring Application Properties Configuration
Update configuration profiles to disable Hibernate schema modification and let Flyway execute migrations on startup.

* **Target File:** [application-dev.yml](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/resources/application-dev.yml)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate # Let Hibernate only check schema matching, disabling auto-update
  
  # Flyway configuration
  flyway:
    enabled: true
    baseline-on-migrate: true # Baselines an existing database if applicable
    baseline-version: 0
    locations: classpath:db/migration
```

---

## 3. Initial Schema: `V1__initial_schema.sql`
We will create the migration file in the standard Flyway migration directory: `src/main/resources/db/migration/V1__initial_schema.sql`.

* **File Location:** `src/main/resources/db/migration/V1__initial_schema.sql`

```sql
-- 1. Permissions Table
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- 2. Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- 3. Role Permissions Join Table
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

-- 4. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    user_email VARCHAR(300) UNIQUE NOT NULL,
    user_name VARCHAR(500),
    password VARCHAR(255),
    image VARCHAR(255),
    enable BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255)
);

-- 5. User Roles Join Table
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- 6. Verification Tokens Table
CREATE TABLE IF NOT EXISTS verification_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 7. Password Reset Tokens Table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 8. Refresh Tokens Table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    jti VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by_token VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    device_info VARCHAR(150),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS refresh_tokens_user_id_idx ON refresh_tokens (user_id);

-- 9. Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    details VARCHAR(2000),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. Seed Default Roles
INSERT INTO roles (id, name) VALUES 
('c53ea45b-d1e9-4e78-9e6e-213c9e6eb1de', 'ROLE_USER'),
('c53ea45b-d1e9-4e78-9e6e-213c9e6eb1df', 'ROLE_ADMIN'),
('c53ea45b-d1e9-4e78-9e6e-213c9e6eb1e0', 'ROLE_GUEST')
ON CONFLICT (name) DO NOTHING;
```

---

## 4. Verification Plan
1. Recompile and build the project using Maven:
   `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean compile`
2. Run all integration tests using `mvn test`. During the test suite startup, Spring Boot will automatically launch Flyway, execute the initial migration scripts on the in-memory/test database, and validate that the JPA models match the database schema precisely.
