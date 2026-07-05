package com.chauhan.authservice.service;

public interface EmailService {
    
    /**
     * Sends an email verification link to the user.
     * @param to The recipient's email address.
     * @param token The email verification token.
     */
    void sendVerificationEmail(String to, String token);
}
