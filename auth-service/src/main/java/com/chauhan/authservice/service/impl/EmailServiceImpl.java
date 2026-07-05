package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${security.verification.app-url:http://localhost:8082}")
    private String appUrl;

    @Override
    public void sendVerificationEmail(String to, String token) {
        String verificationUrl = appUrl + "/api/v1/auth/verify-email?token=" + token;
        String subject = "Email Verification";
        String content = "Thank you for registering. Please click the link below to verify your email address:\n"
                + verificationUrl + "\n\nThis link is valid for 24 hours.";

        logger.info("==================================================");
        logger.info("VERIFICATION EMAIL SIMULATION (LOCAL LOG):");
        logger.info("To: {}", to);
        logger.info("Subject: {}", subject);
        logger.info("Link: {}", verificationUrl);
        logger.info("==================================================");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            logger.info("Verification email successfully sent to {} via SMTP server.", to);
        } catch (Exception e) {
            logger.warn("Failed to send verification email to {} via SMTP server. Make sure MailHog is running. Error: {}", to, e.getMessage());
        }
    }
}
