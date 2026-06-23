package com.chauhan.authservice.auth.controllers;

import com.chauhan.authservice.auth.entities.User;
import com.chauhan.authservice.auth.payload.UserDto;
import com.chauhan.authservice.auth.payload.auth.LoginRequest;
import com.chauhan.authservice.auth.payload.auth.TokenResponse;
import com.chauhan.authservice.auth.security.JwtUtil;
import com.chauhan.authservice.auth.services.AuthService;
import com.chauhan.authservice.auth.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final ModelMapper mapper;
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/register")
    public ResponseEntity<UserDto> registerUser(@RequestBody UserDto userDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(userDto));
    }

    /**
     * Authenticates a user with their email and password.
     *
     * @param loginRequest The request body containing the user's credentials.
     * @return A ResponseEntity containing the access and refresh tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        // 1. Authenticate the user using Spring Security's AuthenticationManager
        Authentication authentication = getAuthentication(loginRequest);

        UserDto user = userService.getUserByEmail(loginRequest.email());
        logger.debug("Processing request to user with email: {}", user.getEmail());
        if (user == null) throw new BadCredentialsException("Invalid Username or Password !!");
        if (!user.getEnable())  throw new DisabledException("User is disabled");

        String jti = UUID.randomUUID().toString();
//        var refreshTokenOb = RefreshToken.builder()
//                .jti(jti)
//                .user(user)
//                .createdAt(Instant.now())
//                .expiresAt(Instant.now().plusSeconds(jwtUtil.getRefreshTtlSeconds()))
//                .revoked(false)
//                .build();


        // 2. If authentication is successful, set the authentication in the security context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Get the authenticated user principal
        User authenticatedUser = (User) authentication.getPrincipal();

        // 4. Generate access and refresh tokens
        assert authenticatedUser != null;
        String accessToken = jwtUtil.generateAccessToken(authenticatedUser);
        String refreshToken = jwtUtil.generateRefreshToken(authenticatedUser, "some-jti"); // You might want a more sophisticated JTI handling

        // 5. Build the response
//        TokenResponse tokenResponse = TokenResponse.builder().accessToken(accessToken).refreshToken(refreshToken).build();
        TokenResponse tokenResponse = TokenResponse.of(accessToken, refreshToken, jwtUtil.getAccessTtlSeconds(), mapper.map(user, UserDto.class));

        return ResponseEntity.ok(tokenResponse);
    }

    private @NonNull Authentication getAuthentication(LoginRequest loginRequest) {
        try {
            return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new BadCredentialsException("Invalid Username or Password !!");
        }
    }
}
