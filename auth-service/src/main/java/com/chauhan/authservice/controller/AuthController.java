package com.chauhan.authservice.controller;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.dto.request.ForgotPasswordRequest;
import com.chauhan.authservice.dto.request.LoginRequest;
import com.chauhan.authservice.dto.request.RefreshTokenRequest;
import com.chauhan.authservice.dto.request.ResendVerificationRequest;
import com.chauhan.authservice.dto.request.ResetPasswordRequest;
import com.chauhan.authservice.dto.response.TokenResponse;
import com.chauhan.authservice.security.CookieUtilService;
import com.chauhan.authservice.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * RESPONSIBILITY:
 * Exposes public HTTP endpoints for authentication: /register, /login, /refresh, /logout, 
 * /verify-email, and /resend-verification.
 * It is strictly responsible for handling HTTP presentation logic, parsing request payloads, 
 * attaching cookies, and formatting API responses.
 * All core business logic is delegated to AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtilService cookieUtilService;

    /**
     * Handles user registration requests.
     */
    @PostMapping("/register")
    public ResponseEntity<UserDto> registerUser(@Valid @RequestBody UserDto userDto) {
        return ResponseEntity.status(201).body(authService.registerUser(userDto));
    }

    /**
     * Authenticates a user with email and password, and returns access and refresh tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        TokenResponse tokenResponse = authService.login(loginRequest);

        // Attach the refresh token as a secure, HTTP-only cookie.
        cookieUtilService.attachRefreshCookie(response, tokenResponse.refreshToken(), (int) tokenResponse.expiresIn());
        cookieUtilService.addNoStoreHeaders(response);

        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * Refreshes an access token using a valid refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest body, 
            HttpServletResponse response, 
            HttpServletRequest request
    ) {
        String refreshTokenString = readRefreshTokenFromRequest(body, request)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is missing"));

        TokenResponse tokenResponse = authService.refresh(refreshTokenString);

        cookieUtilService.attachRefreshCookie(response, tokenResponse.refreshToken(), (int) tokenResponse.expiresIn());
        cookieUtilService.addNoStoreHeaders(response);

        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * Logs out the user by revoking the refresh token and clearing the security context.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        readRefreshTokenFromRequest(null, request).ifPresent(authService::logout);

        cookieUtilService.clearRefreshCookie(response);
        cookieUtilService.addNoStoreHeaders(response);
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    /**
     * Verifies a user's email using the provided token.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    /**
     * Resends the verification email to the user if their email is not verified.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(Map.of("message", "If the email is registered, a new verification link has been sent."));
    }

    /**
     * Initiates a forgot password flow. Sends a reset link to the email if registered.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(Map.of("message", "If the email is registered, a password reset link has been sent."));
    }

    /**
     * Resets the user's password using the validation token.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now log in."));
    }

    /**
     * Reads the refresh token from various sources with a defined order of precedence.
     * Precedence: Cookie > Request Body > X-Refresh-Token Header > Authorization Header.
     */
    private Optional<String> readRefreshTokenFromRequest(RefreshTokenRequest body, HttpServletRequest request) {
        if (request.getCookies() != null && request.getCookies().length > 0) {
            Optional<String> fromCookie = Arrays.stream(request.getCookies())
                    .filter(c -> cookieUtilService.getRefreshTokenCookieName().equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(v -> !v.isBlank())
                    .findFirst();
            if (fromCookie.isPresent()) return fromCookie;
        }

        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return Optional.of(body.refreshToken());
        }

        String refreshHeader = request.getHeader("X-Refresh-Token");
        if (refreshHeader != null && !refreshHeader.isBlank()) {
            return Optional.of(refreshHeader.trim());
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String candidate = authHeader.substring(7).trim();
            if (!candidate.isEmpty()) {
                // If it looks like a refresh token, extract it.
                // We use basic checks here; the service will perform full validation.
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }
}