package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Implementation of NotificationChannel for dispatching HTML emails using JavaMailSender.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@chauhan.com}")
    private String fromEmail;

    @Override
    public boolean supports(NotificationType type) {
        return NotificationType.EMAIL.equals(type);
    }

    @Override
    public void send(NotificationPayload payload) {
        log.info("Sending Email notification to [{}] with subject [{}]", payload.getRecipient(), payload.getSubject());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(payload.getRecipient());
            helper.setSubject(payload.getSubject());

            // Set HTML body content
            helper.setText(payload.getBody(), true);

            mailSender.send(mimeMessage);
            log.info("Successfully dispatched email notification to [{}]", payload.getRecipient());
        } catch (Exception e) {
            log.error("Failed to send email notification to [{}]", payload.getRecipient(), e);
            throw new RuntimeException("Email dispatch failed for recipient: " + payload.getRecipient(), e);
        }
    }
}
