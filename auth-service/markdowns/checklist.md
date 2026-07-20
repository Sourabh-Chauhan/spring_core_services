# Auth Service Development Checklist

This checklist outlines the development goals for the authentication and authorization service, based on the architecture blueprint.

## Phase 1: Core Password-Based Authentication

- [x] **Project Setup:**
    - [x] Initialize a multi-module Maven project with a parent `pom.xml`.
    - [x] Create the `auth-service` module.
- [x] **Entities and Database:**
    - [x] Implement `User`, `Role`, and `Permission` entities.
    - [x] Set up Flyway for database migrations and create the initial schema.
- [x] **Registration:**
    - [x] Create `POST /auth/register` endpoint.
    - [x] Implement validation for email, password strength, and uniqueness.
    - [x] Hash passwords using BCrypt.
    - [x] Assign a default role to new users.
- [x] **Login:**
    - [x] Create `POST /auth/login` endpoint.
    - [x] Validate credentials.
    - [x] Issue short-lived JWT access tokens and longer-lived refresh tokens.
    - [x] Persist a hash of the refresh token.
- [x] **Security:**
    - [x] Implement `JwtAuthenticationFilter` to validate access tokens.
    - [x] Configure Spring Security for stateless sessions and public endpoints.
    - [x] Set up global exception handling for consistent error responses.
- [x] **Token Refresh:**
    - [x] Create `POST /auth/refresh` endpoint.
    - [x] Implement refresh token rotation.

## Phase 2: Email Verification and Account Management

- [x] **Email Verification:**
    - [x] Generate and email a verification token upon registration.
    - [x] Create `GET /auth/verify-email` endpoint to handle token verification.
    - [x] Prevent login for users with unverified emails.
    - [x] Implement a `POST /auth/resend-verification` endpoint.
- [x] **Password Reset:**
    - [x] Implement a "forgot password" flow that emails a password reset link.
    - [x] Create `POST /auth/reset-password` endpoint to handle the password update.
- [x] **Logout:**
    - [x] Create `POST /auth/logout` endpoint.
    - [x] Implement a token blacklist using Redis to invalidate access tokens.
    - [x] Revoke the refresh token in the database.

## Phase 3: OAuth2 and Social Login

- [x] **OAuth2 Configuration:**
    - [x] Configure Spring Security for OAuth2 login with providers like Google, GitHub, etc.
    - [x] Implement `OAuth2SuccessHandler` and `OAuth2FailureHandler`.
- [x] **User Provisioning:**
    - [x] Find or create a user account based on the email from the OAuth2 provider.
    - [x] Link the OAuth2 account to the internal user.
    - [x] Issue internal JWTs upon successful OAuth2 login.

## Phase 4: Advanced Features and Production Hardening

- [x] **Role-Based Access Control (RBAC):**
    - [x] Implement endpoints for managing roles and permissions.
    - [x] Use `@PreAuthorize` for method-level security.
- [x] **Session Management:**
    - [x] Create endpoints to view and revoke user sessions.
- [x] **Auditing and Logging:**
    - [x] Implement audit logging for important events (e.g., login, password change).
    - [x] Use Spring Events for asynchronous logging.
- [x] **Asynchronous Messaging & Decoupling:**
    - [x] Refactor email/SMS tasks to publish events to RabbitMQ, handled by dedicated `notification-service` (detailed in [`event_driven_notification_producer_plan.md`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/markdowns/event_driven_notification_producer_plan.md) and [`notification_service_design_plan.md`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/markdowns/notification_service_design_plan.md)).
- [x] **Rate Limiting:**
    - [x] Implement rate limiting on sensitive endpoints (Implemented at API Gateway edge via RequestRateLimiter).
- [x] **Scheduled Jobs:**
    - [x] Create a scheduled job to clean up expired tokens and old log entries.
- [ ] **Observability:**
    - [ ] Configure Micrometer and Prometheus for metrics.
    - [ ] Set up Actuator health checks.
- [ ] **Testing:**
    - [ ] Write integration tests using Testcontainers for PostgreSQL and Redis.
    - [ ] Write unit tests for controllers, services, and security components.
- [ ] **Containerization:**
    - [ ] Create a `Dockerfile` for the `auth-service`.
    - [ ] Set up a `docker-compose.yml` for local development.

## Phase 5: Security Hardening & Improvements (from TODO)

- [ ] **Security & Endpoint Authorization:**
    - [ ] Secure User Management Endpoints (re-enable SecurityConfig matching and add `@PreAuthorize("hasRole('ADMIN')")` to UserController).
    - [ ] Restrict CORS allowed origins via properties to fix Insecure CORS Wildcard.
    - [ ] Implement password verification for password changes and re-verification workflow for email updates.
- [ ] **API Gateway Compatibility & Shared Redis Blacklist:**
    - [ ] Resolve Blacklist Incompatibility (refactor TokenBlacklistServiceImpl to store JTI instead of raw token).
    - [x] Integrate Service Discovery (Eureka Client dependency added and configured).
- [x] **Performance & Architecture Fixes:**
    - [x] Implement Skip-Null-Fields Update Strategy (HTTP PATCH mapping with map-based field presence checking).
- [x] **Configuration & Deployment Fixes:**
    - [x] Avoid Hardcoded Client Redirect URLs (inject `app.frontend-url` property in handlers).