package com.chauhan.authservice.service;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.dto.request.LoginRequest;
import com.chauhan.authservice.dto.response.TokenResponse;

public interface AuthService {
    UserDto registerUser(UserDto userDto);
    
    TokenResponse login(LoginRequest loginRequest);
    
    TokenResponse refresh(String refreshToken);
    
    void logout(String refreshToken);
    
    void verifyEmail(String token);
    
    void resendVerification(String email);
}