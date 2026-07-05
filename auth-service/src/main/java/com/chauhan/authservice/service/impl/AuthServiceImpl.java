package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.service.AuthService;
import com.chauhan.authservice.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;


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
 * - Implement email validation / verification flows during the registration process.
 */
@Service
@AllArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserService userService;

    @Override
    public UserDto registerUser(UserDto userDto) {

        return  userService.createUser(userDto);
    }
}