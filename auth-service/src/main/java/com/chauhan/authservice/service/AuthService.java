package com.chauhan.authservice.service;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.dto.request.LoginRequest;
import com.chauhan.authservice.dto.response.TokenResponse;

public interface AuthService {
    UserDto registerUser(UserDto userDto);
    
    TokenResponse login(LoginRequest loginRequest, String ipAddress, String userAgent);
    
    TokenResponse refresh(String refreshToken, String ipAddress, String userAgent);
    
    void logout(String accessToken, String refreshToken);
    
    void verifyEmail(String token);
    
    void resendVerification(String email);

    void forgotPassword(String email);

    void resetPassword(String token, String newPassword);
}