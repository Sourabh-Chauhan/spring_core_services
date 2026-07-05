package com.chauhan.authservice.controller;

import com.chauhan.authservice.entity.RefreshToken;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.dto.request.LoginRequest;
import com.chauhan.authservice.dto.request.RefreshTokenRequest;
import com.chauhan.authservice.dto.response.TokenResponse;
import com.chauhan.authservice.security.CookieUtilService;
import com.chauhan.authservice.security.JwtUtil;
import com.chauhan.authservice.service.AuthService;
import com.chauhan.authservice.service.impl.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Optional;

/**
 * RESPONSIBILITY:
 * Exposes authentication endpoints: /register, /login, /refresh, and /logout.
 * It handles raw HTTP requests, coordinates with Spring Security's AuthenticationManager,
 * generates JWT tokens, and attaches refresh tokens as secure HTTP-only cookies.
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Privilege Escalation: The `/register` endpoint accepts UserDto directly. If UserDto contains roles,
 *    a malicious user could register themselves with the ADMIN role.
 * 2. Outdated TODO: The controller Javadoc previously suggested moving exception handling to @ControllerAdvice,
 *    but GlobalExceptionHandler is already implemented.
 *
 * TODO:
 * - Sanitise input / remove roles from UserDto on public registration.
 * - Implement request body validation using @Valid for register and login payloads.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CookieUtilService cookieUtilService;
    private final ModelMapper mapper;
    private final RefreshTokenService refreshTokenService;

    /**
     * Handles user registration requests.
     * @param userDto DTO containing the new user's details.
     * @return A response entity with the created user's DTO.
     */
    @PostMapping("/register")
    public ResponseEntity<UserDto> registerUser(@RequestBody UserDto userDto) {
        return ResponseEntity.status(201).body(authService.registerUser(userDto));
    }

    /**
     * Authenticates a user with email and password, and returns JWTs.
     * This method follows security best practices by delegating authentication to Spring Security's
     * AuthenticationManager and using a single, verified User object post-authentication.
     * @param loginRequest DTO containing the user's credentials.
     * @param response The HTTP response, used to attach the refresh token cookie.
     * @return A response entity containing the access and refresh tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        // This is the primary entry point for the authentication process.
        // It delegates the core authentication logic to the AuthenticationManager.
        Authentication authentication = getAuthentication(loginRequest);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // This is a critical safety check to ensure the principal is of the correct type.
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User authenticatedUser)) {
            throw new IllegalStateException("Authentication principal is not a User instance");
        }

        // Once authenticated, generate the necessary tokens.
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(authenticatedUser);
        String accessToken = jwtUtil.generateAccessToken(authenticatedUser);
        String refreshTokenString = jwtUtil.generateRefreshToken(authenticatedUser, refreshToken.getJti());

        TokenResponse tokenResponse = TokenResponse.of(accessToken, refreshTokenString, jwtUtil.getAccessTtlSeconds(), mapper.map(authenticatedUser, UserDto.class));

        // Attach the refresh token as a secure, HTTP-only cookie.
        cookieUtilService.attachRefreshCookie(response, refreshTokenString, (int) jwtUtil.getRefreshTtlSeconds());
        cookieUtilService.addNoStoreHeaders(response);

        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * This endpoint allows clients to obtain a new access token without re-authenticating.
     * @param body The request body, which may contain the refresh token.
     * @param response The HTTP response, used to attach the new refresh token cookie.
     * @param request The HTTP request, used to read the refresh token from cookies or headers.
     * @return A response entity with the new access and refresh tokens.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestBody(required = false) RefreshTokenRequest body, HttpServletResponse response, HttpServletRequest request) {
        String refreshTokenString = readRefreshTokenFromRequest(body, request)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is missing"));

        // The service layer handles the complex logic of validating and rotating the token.
        RefreshToken newRefreshToken = refreshTokenService.validateAndRotateRefreshToken(refreshTokenString);
        User user = newRefreshToken.getUser();

        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshTokenString = jwtUtil.generateRefreshToken(user, newRefreshToken.getJti());

        cookieUtilService.attachRefreshCookie(response, newRefreshTokenString, (int) jwtUtil.getRefreshTtlSeconds());
        cookieUtilService.addNoStoreHeaders(response);

        return ResponseEntity.ok(TokenResponse.of(newAccessToken, newRefreshTokenString, jwtUtil.getAccessTtlSeconds(), mapper.map(user, UserDto.class)));
    }

    /**
     * Logs out the user by revoking the refresh token and clearing the security context.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        readRefreshTokenFromRequest(null, request).ifPresent(refreshTokenService::revokeRefreshToken);

        cookieUtilService.clearRefreshCookie(response);
        cookieUtilService.addNoStoreHeaders(response);
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    /**
     * A private helper method to encapsulate the authentication logic.
     * @param loginRequest The user's login credentials.
     * @return A fully authenticated Authentication object.
     */
    private @NonNull Authentication getAuthentication(LoginRequest loginRequest) {
        try {
            return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            // Masking internal exceptions to a generic one for security.
            throw new BadCredentialsException("Invalid Username or Password !!");
        }
    }

    /**
     * Reads the refresh token from various sources with a defined order of precedence.
     * This makes the API flexible for different client types (e.g., web browsers using cookies, mobile apps using headers).
     * Order of precedence: Cookie > Request Body > X-Refresh-Token Header > Authorization Header.
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
                try {
                    if (jwtUtil.isRefreshToken(candidate)) {
                        return Optional.of(candidate);
                    }
                } catch (Exception ignored) {
                    // Ignore exceptions here, as the token might be an access token, which is expected.
                }
            }
        }

        return Optional.empty();
    }
}