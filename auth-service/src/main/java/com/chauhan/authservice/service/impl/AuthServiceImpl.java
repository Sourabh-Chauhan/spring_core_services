package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.entity.VerificationToken;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.service.AuthService;
import com.chauhan.authservice.service.EmailService;
import com.chauhan.authservice.service.UserService;
import com.chauhan.authservice.service.VerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RESPONSIBILITY:
 * Orchestrates the public-facing authentication business logic, currently acting as a pass-through 
 * wrapper around UserService for user registration.
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Missing Role Defaulting: Does not assign default roles (e.g., GUEST or USER) to newly registered users, 
 *    relying purely on what's passed in the DTO, which is a privilege escalation vulnerability.
 * 2. Over-delegation: It does not implement any distinct logic from UserServiceImpl for registration.
 *
 * TODO:
 * - Ensure registerUser explicitly strips incoming roles and sets a default role (e.g., "ROLE_USER" or "ROLE_GUEST").
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserService userService;
    private final UserRepository userRepository;
    private final VerificationTokenService tokenService;
    private final EmailService emailService;

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
}