# Auth Service Development Checklist

This checklist outlines the development goals for the authentication and authorization service, based on the architecture blueprint.

## Phase 1: Core Password-Based Authentication

- [x] **Project Setup:**
    - [ ] Initialize a multi-module Maven project with a parent `pom.xml`.
    - [x] Create the `auth-service` module.
- [x] **Entities and Database:**
    - [x] Implement `User`, `Role`, and `Permission` entities.
    - [ ] Set up Flyway for database migrations and create the initial schema.
- [x] **Registration:**
    - [x] Create `POST /auth/register` endpoint.
    - [x] Implement validation for email, password strength, and uniqueness.
    - [x] Hash passwords using BCrypt.
    - [ ] Assign a default role to new users.
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

- [ ] **Email Verification:**
    - [ ] Generate and email a verification token upon registration.
    - [ ] Create `GET /auth/verify-email` endpoint to handle token verification.
    - [ ] Prevent login for users with unverified emails.
    - [ ] Implement a `POST /auth/resend-verification` endpoint.
- [ ] **Password Reset:**
    - [ ] Implement a "forgot password" flow that emails a password reset link.
    - [ ] Create `POST /auth/reset-password` endpoint to handle the password update.
- [x] **Logout:**
    - [x] Create `POST /auth/logout` endpoint.
    - [ ] Implement a token blacklist using Redis to invalidate access tokens.
    - [x] Revoke the refresh token in the database.

## Phase 3: OAuth2 and Social Login

- [ ] **OAuth2 Configuration:**
    - [ ] Configure Spring Security for OAuth2 login with providers like Google, GitHub, etc.
    - [ ] Implement `OAuth2SuccessHandler` and `OAuth2FailureHandler`.
- [ ] **User Provisioning:**
    - [ ] Find or create a user account based on the email from the OAuth2 provider.
    - [ ] Link the OAuth2 account to the internal user.
    - [ ] Issue internal JWTs upon successful OAuth2 login.

## Phase 4: Advanced Features and Production Hardening

- [ ] **Role-Based Access Control (RBAC):**
    - [ ] Implement endpoints for managing roles and permissions.
    - [ ] Use `@PreAuthorize` for method-level security.
- [ ] **Session Management:**
    - [ ] Create endpoints to view and revoke user sessions.
- [ ] **Auditing and Logging:**
    - [ ] Implement audit logging for important events (e.g., login, password change).
    - [ ] Use Spring Events for asynchronous logging.
- [ ] **Rate Limiting:**
    - [ ] Implement rate limiting on sensitive endpoints like login and password reset.
- [ ] **Scheduled Jobs:**
    - [ ] Create a scheduled job to clean up expired tokens and old log entries.
- [ ] **Observability:**
    - [ ] Configure Micrometer and Prometheus for metrics.
    - [ ] Set up Actuator health checks.
- [ ] **Testing:**
    - [ ] Write integration tests using Testcontainers for PostgreSQL and Redis.
    - [ ] Write unit tests for controllers, services, and security components.
- [ ] **Containerization:**
    - [ ] Create a `Dockerfile` for the `auth-service`.
    - [ ] Set up a `docker-compose.yml` for local development.