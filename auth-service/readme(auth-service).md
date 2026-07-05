# Enterprise Auth & Authorization Service — Architecture Blueprint
**Stack:** Java 21, Spring Boot 3.x, PostgreSQL, Redis, JWT, OAuth2, Maven

---

## 1. High-Level Architecture

```
                        ┌─────────────────────┐
   Client Apps  ───────▶│   API Gateway (opt)  │
                        └──────────┬───────────┘
                                   ▼
                     ┌──────────────────────────┐
                     │   Auth Service (this)     │
                     │  ┌────────────────────┐   │
                     │  │ Controllers        │   │
                     │  ├────────────────────┤   │
                     │  │ Services           │   │
                     │  ├────────────────────┤   │
                     │  │ Security Filters   │   │
                     │  ├────────────────────┤   │
                     │  │ Repositories       │   │
                     │  └─────────┬──────────┘   │
                     └────────────┼──────────────┘
                        ┌─────────┴─────────┐
                        ▼                   ▼
                  PostgreSQL              Redis
              (users, roles,        (sessions, token
               tokens, audit)        blacklist, rate-limit,
                                      OAuth state)
```

**Core flows (all stateless where possible):**

- **Registration (password-based):**
  1. `POST /auth/register` with username, email, password.
  2. Validate: email format, password strength, username/email uniqueness.
  3. Hash password (BCrypt) → create `User` with `enabled=true`, `emailVerified=false`.
  4. Assign default role (`ROLE_USER`).
  5. Generate `EmailVerificationToken` (hashed, short expiry e.g. 24h) → publish `UserRegisteredEvent` → async listener sends verification email with link containing the raw token.
  6. Response returns basic user info + a message like "check your email" — **do not issue JWTs yet** (see unverified-login flow below).

- **Email verification:**
  1. User clicks link → `GET/POST /auth/verify-email?token=...`.
  2. Look up token by hash, check not expired/used.
  3. Mark `User.emailVerified=true`, mark token used, publish `EmailVerifiedEvent`.
  4. Return success — front-end can now redirect to login.

- **Login attempt with unverified email (redirect-to-verify flow):**
  1. `POST /auth/login` with correct username/password.
  2. Credentials check passes, but `AuthenticationService` checks `emailVerified` **before** issuing tokens.
  3. If `false`: don't issue JWTs. Return a distinct response — e.g. `403` with `errorCode: EMAIL_NOT_VERIFIED` (not a generic 401), so the front-end knows to redirect to a "verify your email" screen instead of showing "wrong password."
  4. That screen offers a `POST /auth/resend-verification` action (rate-limited, invalidates the old token and issues a new one) in case the original email expired or was lost.
  5. Once verified, the user retries login normally and receives access + refresh tokens.

  The distinct error code is the key design point — collapsing this into a generic auth failure forces the user to guess why login is failing.

- **Login (password):** validate credentials → check account lock/verification → issue access + refresh JWT → persist refresh token hash → log LoginHistory → publish `LoginSuccessEvent`.
- **OAuth2 login:** redirect to provider → callback with code → exchange for provider token → fetch profile → find-or-create `User` + `OAuthAccount` → issue internal JWTs (same as password flow from here).
- **Refresh:** validate refresh token (DB + not blacklisted + not expired) → rotate (issue new refresh, revoke old) → issue new access token.
- **Logout:** blacklist current access token (Redis, TTL = remaining expiry) → revoke refresh token in DB.
- **Password reset:** request → generate one-time token → email link → verify token → update password → invalidate all sessions/refresh tokens.
- **Email verification:** on registration, generate token → email → verify → activate account.
- **Account lock:** N failed attempts (Redis counter) → lock account → unlock via admin or timed expiry.

---

## 2. Project Structure

```
com.chauhan.authservice
├── config/
│   ├── SecurityConfig.java         # filter chain, CORS, CSRF, public endpoints
│   ├── JwtConfig.java              # signing key, expiry properties
│   ├── RedisConfig.java            # RedisTemplate, cache manager
│   ├── OAuth2Config.java           # client registrations (Google/GitHub/MS)
│   ├── OpenApiConfig.java          # Swagger metadata
│   └── AsyncConfig.java            # thread pool for events/emails
│
├── controller/
│   ├── AuthenticationController.java
│   ├── OAuth2Controller.java
│   ├── UserController.java
│   ├── RoleController.java
│   ├── PermissionController.java
│   ├── SessionController.java
│   ├── AdminController.java
│   └── HealthController.java
│
├── service/
│   ├── AuthenticationService.java
│   ├── JwtService.java
│   ├── TokenBlacklistService.java
│   ├── RefreshTokenService.java
│   ├── OAuth2Service.java
│   ├── UserService.java
│   ├── RoleService.java
│   ├── PermissionService.java
│   ├── PasswordService.java
│   ├── EmailVerificationService.java
│   ├── SessionService.java
│   ├── AuditService.java
│   ├── RateLimitService.java
│   └── EmailService.java (or NotificationService)
│
├── repository/
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── PermissionRepository.java
│   ├── OAuthAccountRepository.java
│   ├── RefreshTokenRepository.java
│   ├── PasswordResetTokenRepository.java
│   ├── EmailVerificationTokenRepository.java
│   ├── UserSessionRepository.java
│   ├── LoginHistoryRepository.java
│   └── AuditLogRepository.java
│
├── entity/
│   ├── User.java
│   ├── Role.java
│   ├── Permission.java
│   ├── OAuthAccount.java
│   ├── RefreshToken.java
│   ├── PasswordResetToken.java
│   ├── EmailVerificationToken.java
│   ├── UserSession.java
│   ├── LoginHistory.java
│   └── AuditLog.java
│
├── security/
│   ├── JwtAuthenticationFilter.java
│   ├── CustomUserDetailsService.java
│   ├── CustomAuthenticationProvider.java
│   ├── OAuth2SuccessHandler.java
│   ├── OAuth2FailureHandler.java
│   └── AuthEntryPoint.java          # 401/403 responses
│
├── dto/
│   ├── request/  (LoginRequest, RegisterRequest, RefreshRequest, ...)
│   └── response/ (AuthResponse, UserResponse, ApiResponse<T>, ErrorResponse)
│
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── InvalidCredentialsException.java
│   ├── AccountLockedException.java
│   ├── TokenExpiredException.java
│   └── ...
│
├── event/
│   ├── UserRegisteredEvent.java, LoginSuccessEvent.java, ...
│   └── listener/ (sends emails, writes audit logs, async)
│
├── scheduler/
│   └── TokenCleanupJob.java        # purge expired tokens/sessions/logs
│
└── mapper/                          # MapStruct entity<->DTO mappers
```

---

## 3. Entities (core fields)

**User**
`id (UUID), username, email (unique), passwordHash (nullable for OAuth-only), enabled, emailVerified, accountLocked, failedLoginAttempts, mfaEnabled, mfaSecret, createdAt, updatedAt`
→ `Set<Role> roles` (many-to-many via `user_roles`)

**Role** — `id, name (ROLE_ADMIN etc.), description` → `Set<Permission>` (many-to-many via `role_permissions`)

**Permission** — `id, name (user:read, user:write), description`

**OAuthAccount** — `id, user_id (FK), provider (GOOGLE/GITHUB/MS), providerUserId, email, accessToken(encrypted), refreshToken(encrypted), createdAt`. Unique constraint on `(provider, providerUserId)`.

**RefreshToken** — `id, user_id, tokenHash, deviceInfo, ipAddress, expiresAt, revoked, createdAt`. Index on `tokenHash`, `user_id`.

**PasswordResetToken / EmailVerificationToken** — `id, user_id, tokenHash, expiresAt, used`.

**UserSession** — `id, user_id, deviceInfo, ipAddress, lastActiveAt, expiresAt, active`.

**LoginHistory** — `id, user_id, ipAddress, userAgent, success, timestamp, failureReason`.

**AuditLog** — `id, actorId, action, entityType, entityId, oldValue, newValue, timestamp, correlationId`.

**TokenBlacklist** — usually **Redis**, not a table (`key = jti`, `value = expiry TTL`) — avoids DB writes on every logout.

---

## 4. Controllers → Key Endpoints

| Controller | Endpoints |
|---|---|
| AuthenticationController | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/verify-email`, `/auth/forgot-password`, `/auth/reset-password` |
| OAuth2Controller | `GET /oauth2/authorize/{provider}`, `GET /oauth2/callback/{provider}` (mostly handled by Spring Security OAuth2 client, thin controller) |
| UserController | `GET/PUT /users/me`, `GET /users/{id}` (admin), `PATCH /users/{id}/status` |
| RoleController | CRUD `/roles`, `POST /roles/{id}/permissions` |
| PermissionController | CRUD `/permissions` |
| SessionController | `GET /sessions`, `DELETE /sessions/{id}` (force logout a device), `DELETE /sessions` (logout all) |
| AdminController | user search, lock/unlock, role assignment, audit log viewing |
| HealthController | `/actuator/health` (mostly Spring Boot Actuator, custom checks for DB/Redis) |

Every mutating endpoint: `@Valid` DTO, wrapped in `ApiResponse<T>`, errors go through `GlobalExceptionHandler` → consistent `ErrorResponse{code, message, timestamp, path}`.

---

## 5. Services — Responsibilities

- **AuthenticationService** — orchestrates register/login/logout; calls JwtService, UserService, AuditService.
- **JwtService** — generate/parse/validate access & refresh tokens; embeds `sub, roles, permissions, jti, deviceId`.
- **TokenBlacklistService** — Redis-backed; check-on-filter for every request.
- **RefreshTokenService** — rotation logic, revoke-on-use-detection (reuse = revoke entire family, security signal).
- **OAuth2Service** — provider profile mapping, account linking (match by verified email), first-login provisioning.
- **UserService / RoleService / PermissionService** — standard domain CRUD + validation.
- **PasswordService** — BCrypt hashing, strength validation, reset token lifecycle.
- **SessionService** — tracks active sessions per device, force-logout.
- **AuditService** — writes AuditLog rows, listens to domain events.
- **RateLimitService** — Redis token-bucket, per-IP and per-account (login attempts especially).

---

## 6. Security Module

- `SecurityConfig`: stateless session policy, public endpoints (`/auth/**`, `/oauth2/**`, swagger), `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.
- `JwtAuthenticationFilter`: extracts bearer token → checks blacklist → validates signature/expiry → sets `SecurityContext`.
- Method-level: `@PreAuthorize("hasPermission(...)")` for fine-grained checks alongside `hasRole(...)`.
- Passwords: BCrypt (cost factor 12+), never logged.
- Secrets: signing key from env var / vault, **not** committed; rotate via key ID (`kid`) in JWT header supporting multiple active keys during rotation.

**JWT design:** short-lived access token (~15 min), longer refresh token (~7–30 days, rotated on use, stored hashed in DB), `jti` claim enables targeted revocation, `aud`/`iss` validated.

---

## 7. Cross-Cutting Concerns

- **Exception handling:** `GlobalExceptionHandler` (`@RestControllerAdvice`) maps each exception → HTTP status + error code.
- **Events:** Spring `ApplicationEventPublisher` for `UserRegistered`, `LoginSuccess/Failure`, `PasswordChanged`, etc. — async listeners send emails and write audit logs without blocking the request thread.
- **Scheduled jobs:** `@Scheduled` cleanup for expired refresh/reset/verification tokens and old audit/login-history rows (retention policy).
- **Rate limiting:** Redis + Bucket4j on `/auth/login`, `/auth/forgot-password`.
- **Migrations:** Flyway (`V1__init.sql`, versioned incrementally) — never edit applied migrations.
- **Observability:** Micrometer + Prometheus metrics, Actuator health checks (DB, Redis), structured JSON logs with correlation ID (via `MDC`, propagated through a filter).
- **Testing:** Testcontainers (Postgres + Redis) for integration tests, MockMvc for controller tests, dedicated tests for JWT edge cases (expired, tampered, blacklisted) and OAuth2 callback mocking.

---

## Suggested build order (practical, not all-at-once)
1. User/Role/Permission entities + Flyway migrations
2. Password auth (register/login/JWT/refresh) end-to-end
3. Security filter chain + exception handling
4. RBAC/permission checks on protected endpoints
5. OAuth2 (Google first, then generalize)
6. Sessions, audit, rate limiting, cleanup jobs
7. Observability + Docker/Compose for local dev

This is deliberately structured so each phase is independently testable and deployable — don't build all 10 entities before you have one working login flow.
