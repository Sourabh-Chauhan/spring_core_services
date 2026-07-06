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
            throw new BadCredentialsException("Invalid Username or Password !!");
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User authenticatedUser)) {
            throw new IllegalStateException("Authentication principal is not a User instance");
        }

        if (!authenticatedUser.isEmailVerified()) {
            throw new EmailNotVerifiedException("Email is not verified. Please check your inbox for verification link.");
        }

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(authenticatedUser, ipAddress, userAgent);
        String accessToken = jwtUtil.generateAccessToken(authenticatedUser, refreshToken.getJti());
        String refreshTokenString = jwtUtil.generateRefreshToken(authenticatedUser, refreshToken.getJti());

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
    }
}