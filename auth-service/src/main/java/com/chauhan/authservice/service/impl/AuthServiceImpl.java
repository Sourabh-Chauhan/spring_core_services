package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.dto.request.LoginRequest;
import com.chauhan.authservice.dto.response.TokenResponse;
import com.chauhan.authservice.entity.PasswordResetToken;
import com.chauhan.authservice.entity.RefreshToken;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.entity.VerificationToken;
import com.chauhan.authservice.exceptions.EmailNotVerifiedException;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.security.JwtUtil;
import com.chauhan.authservice.service.AuthService;
import com.chauhan.authservice.service.EmailService;
import com.chauhan.authservice.service.PasswordResetTokenService;
import com.chauhan.authservice.service.UserService;
import com.chauhan.authservice.service.VerificationTokenService;
import com.chauhan.authservice.service.TokenBlacklistService;
import com.chauhan.authservice.config.AppConstants;
import com.chauhan.authservice.event.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserService userService;
    private final UserRepository userRepository;
    private final VerificationTokenService tokenService;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final ModelMapper mapper;
    private final PasswordResetTokenService passwordResetTokenService;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UserDto registerUser(UserDto userDto) {
        UserDto registeredUserDto = userService.createUser(userDto);

        User user = userRepository.findByEmail(registeredUserDto.getEmail())
                .orElseThrow(() -> new IllegalStateException("Registered user not found in database."));

        // Generate email verification token
        VerificationToken verificationToken = tokenService.createTokenForUser(user);

        // Send verification email
        emailService.sendVerificationEmail(user.getEmail(), verificationToken.getToken());

        String[] clientInfo = getRequestIpAndUserAgent();
        eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_REGISTRATION, user.getEmail(), clientInfo[0], clientInfo[1], "User registered successfully"));

        return registeredUserDto;
    }

    @Override
    public TokenResponse login(LoginRequest loginRequest, String ipAddress, String userAgent) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
            );
        } catch (org.springframework.security.core.AuthenticationException e) {
            eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_LOGIN_FAILURE, loginRequest.email(), ipAddress, userAgent, "Authentication failed: " + e.getMessage()));
            throw new BadCredentialsException("Invalid Username or Password !!");
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User authenticatedUser)) {
            throw new IllegalStateException("Authentication principal is not a User instance");
        }

        if (!authenticatedUser.isEmailVerified()) {
            eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_LOGIN_FAILURE, authenticatedUser.getEmail(), ipAddress, userAgent, "Login failed: Email is not verified"));
            throw new EmailNotVerifiedException("Email is not verified. Please check your inbox for verification link.");
        }

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(authenticatedUser, ipAddress, userAgent);
        String accessToken = jwtUtil.generateAccessToken(authenticatedUser, refreshToken.getJti());
        String refreshTokenString = jwtUtil.generateRefreshToken(authenticatedUser, refreshToken.getJti());

        eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_LOGIN_SUCCESS, authenticatedUser.getEmail(), ipAddress, userAgent, "User logged in successfully"));

        return TokenResponse.of(
                accessToken,
                refreshTokenString,
                jwtUtil.getAccessTtlSeconds(),
                mapper.map(authenticatedUser, UserDto.class)
        );
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshTokenString, String ipAddress, String userAgent) {
        RefreshToken newRefreshToken = refreshTokenService.validateAndRotateRefreshToken(refreshTokenString, ipAddress, userAgent);
        User user = newRefreshToken.getUser();

        String newAccessToken = jwtUtil.generateAccessToken(user, newRefreshToken.getJti());
        String newRefreshTokenString = jwtUtil.generateRefreshToken(user, newRefreshToken.getJti());

        return TokenResponse.of(
                newAccessToken,
                newRefreshTokenString,
                jwtUtil.getAccessTtlSeconds(),
                mapper.map(user, UserDto.class)
        );
    }

    @Override
    @Transactional
    public void logout(String accessToken, String refreshTokenString) {
        if (accessToken != null && !accessToken.isBlank()) {
            long ttlSeconds = jwtUtil.getRemainingTtlSeconds(accessToken);
            if (ttlSeconds > 0) {
                tokenBlacklistService.blacklistToken(accessToken, ttlSeconds);
            }
        }
        if (refreshTokenString != null && !refreshTokenString.isBlank()) {
            refreshTokenService.revokeRefreshToken(refreshTokenString);
        }
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        VerificationToken verificationToken = tokenService.validateToken(token);
        User user = verificationToken.getUser();
        
        user.setEmailVerified(true);
        userRepository.save(user);
        
        tokenService.deleteToken(verificationToken);

        String[] clientInfo = getRequestIpAndUserAgent();
        eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_EMAIL_VERIFIED, user.getEmail(), clientInfo[0], clientInfo[1], "Email verified successfully"));
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isEmailVerified()) {
                VerificationToken token = tokenService.createTokenForUser(user);
                emailService.sendVerificationEmail(user.getEmail(), token.getToken());
            }
        }
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            PasswordResetToken token = passwordResetTokenService.createTokenForUser(user);
            emailService.sendPasswordResetEmail(user.getEmail(), token.getToken());

            String[] clientInfo = getRequestIpAndUserAgent();
            eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_PASSWORD_RESET_REQUEST, user.getEmail(), clientInfo[0], clientInfo[1], "Password reset requested"));
        }
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenService.validateToken(token);
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordResetTokenService.deleteToken(resetToken);

        String[] clientInfo = getRequestIpAndUserAgent();
        eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_PASSWORD_CHANGE, user.getEmail(), clientInfo[0], clientInfo[1], "Password reset successfully using token"));
    }

    private String[] getRequestIpAndUserAgent() {
        String ipAddress = "UNKNOWN";
        String userAgent = "UNKNOWN";
        try {
            RequestAttributes attributes =  RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
                HttpServletRequest request = servletRequestAttributes.getRequest();
                ipAddress = request.getRemoteAddr();
                userAgent = request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            // Fallback
        }
        return new String[]{ipAddress, userAgent};
    }
}