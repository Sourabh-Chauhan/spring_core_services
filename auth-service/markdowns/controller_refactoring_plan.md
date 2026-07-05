# Refactoring Plan: Clean AuthController

This document outlines the refactoring strategy for [AuthController.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/controller/AuthController.java) to solve controller bloat and restore clean boundaries between HTTP presentation and business logic.

---

## 1. Current Issues in AuthController

1. **Too Many Dependencies (9 dependencies)**:
   - Injected beans: `AuthService`, `AuthenticationManager`, `JwtUtil`, `CookieUtilService`, `ModelMapper`, `RefreshTokenService`, `VerificationTokenService`, `UserRepository`, `EmailService`.
2. **Direct DB Operations**:
   - Uses `UserRepository` directly inside endpoint mappings (e.g. `userRepository.save()` in `/verify-email` and `userRepository.findByEmail()` in `/resend-verification`).
3. **Business Logic Orchestration**:
   - Contains business logic mapping, such as validation of token expiration, verification state, token rotation, and email delivery orchestration.

---

## 2. Proposed Architecture

```mermaid
graph TD
    Client[HTTP Client] --> AuthController[AuthController]
    
    subgraph Presentation Layer (HTTP-Specific)
        AuthController
    end
    
    subgraph Service Layer (Business Logic)
        AuthController --> AuthService[AuthService / AuthServiceImpl]
        AuthService --> RefreshTokenService[RefreshTokenService]
        AuthService --> VerificationTokenService[VerificationTokenService]
        AuthService --> EmailService[EmailService]
    end
    
    subgraph Data Access Layer
        AuthService --> UserRepository[UserRepository]
        VerificationTokenService --> VerificationTokenRepository[VerificationTokenRepository]
    end
```

---

## 3. Dependency Comparison

| Component / Bean | Current AuthController | Refactored AuthController | Refactored AuthServiceImpl |
| :--- | :---: | :---: | :---: |
| `AuthService` | ✅ | ✅ | *N/A (Self)* |
| `CookieUtilService` | ✅ | ✅ | ❌ |
| `AuthenticationManager` | ✅ | ❌ | ✅ |
| `JwtUtil` | ✅ | ❌ | ✅ |
| `ModelMapper` | ✅ | ❌ | ✅ |
| `RefreshTokenService` | ✅ | ❌ | ✅ |
| `VerificationTokenService` | ✅ | ❌ | ✅ |
| `UserRepository` | ✅ | ❌ | ✅ |
| `EmailService` | ✅ | ❌ | ✅ |

*Note: In the new structure, `AuthController` only needs **two** dependencies: `AuthService` and `CookieUtilService` (to handle HTTP-specific cookie attachment).*

---

## 4. Refactored Interfaces & Responsibilities

### Update `AuthService` Interface
We will expand `AuthService` to expose all core auth activities:

```java
public interface AuthService {
    UserDto registerUser(UserDto userDto);
    
    // Core login, return tokens & user details
    TokenResponse login(LoginRequest loginRequest);
    
    // Token refresh rotation
    TokenResponse refresh(String refreshToken);
    
    // Logout invalidation
    void logout(String refreshToken);
    
    // Email verification
    void verifyEmail(String token);
    
    // Resending email verification
    void resendVerification(String email);
}
```

---

## 5. Refactored Controller Structure

The refactored controller will delegate all work to `AuthService` and focus strictly on handling mapping, request reading, cookie writing, and returning status codes.

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtilService cookieUtilService;

    @PostMapping("/register")
    public ResponseEntity<UserDto> registerUser(@Valid @RequestBody UserDto userDto) {
        return ResponseEntity.status(201).body(authService.registerUser(userDto));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        TokenResponse tokenResponse = authService.login(loginRequest);
        cookieUtilService.attachRefreshCookie(response, tokenResponse.refreshToken(), (int) tokenResponse.expiresIn());
        cookieUtilService.addNoStoreHeaders(response);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestBody(required = false) RefreshTokenRequest body, HttpServletResponse response, HttpServletRequest request) {
        String refreshTokenString = readRefreshTokenFromRequest(body, request)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is missing"));
        
        TokenResponse tokenResponse = authService.refresh(refreshTokenString);
        cookieUtilService.attachRefreshCookie(response, tokenResponse.refreshToken(), (int) tokenResponse.expiresIn());
        cookieUtilService.addNoStoreHeaders(response);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        readRefreshTokenFromRequest(null, request).ifPresent(authService::logout);
        cookieUtilService.clearRefreshCookie(response);
        cookieUtilService.addNoStoreHeaders(response);
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(Map.of("message", "If the email is registered, a new verification link has been sent."));
    }
    
    // ... private readRefreshTokenFromRequest helper remains
}
```
