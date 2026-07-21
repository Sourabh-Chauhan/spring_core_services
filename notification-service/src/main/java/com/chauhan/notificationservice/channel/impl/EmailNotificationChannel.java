package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.exception.PermanentNotificationException;
import com.chauhan.notificationservice.exception.TransientNotificationException;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Implementation of NotificationChannel for dispatching HTML emails using JavaMailSender,
 * complete with structured MDC logging and exception classification (Transient vs Permanent).
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
        if (payload.getRecipient() == null || payload.getRecipient().isBlank() || !payload.getRecipient().contains("@")) {
            log.error("[MDC channel=EMAIL] Invalid email recipient address: [{}]", payload.getRecipient());
            throw new PermanentNotificationException("Invalid recipient email address: " + payload.getRecipient());
        }

        MDC.put("channel", "EMAIL");
        MDC.put("recipient", payload.getRecipient());
        log.info("Sending Email notification to [{}] with subject [{}]", payload.getRecipient(), payload.getSubject());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(payload.getRecipient());
            helper.setSubject(payload.getSubject());
            helper.setText(payload.getBody(), true);

            mailSender.send(mimeMessage);
            log.info("Successfully dispatched email notification to [{}]", payload.getRecipient());
        } catch (MailException | MessagingException e) {
            log.error("Transient error encountered sending email notification to [{}]", payload.getRecipient(), e);
            throw new TransientNotificationException("Transient error dispatching email to " + payload.getRecipient(), e);
        } catch (Exception e) {
            log.error("Permanent failure encountered sending email notification to [{}]", payload.getRecipient(), e);
            throw new PermanentNotificationException("Permanent error dispatching email to " + payload.getRecipient(), e);
        } finally {
            MDC.remove("channel");
            MDC.remove("recipient");
        }
    }
}
