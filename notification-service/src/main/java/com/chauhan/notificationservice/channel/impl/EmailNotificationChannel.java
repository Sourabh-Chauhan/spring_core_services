package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.exception.PermanentNotificationException;
import com.chauhan.notificationservice.exception.TransientNotificationException;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Implementation of NotificationChannel for dispatching HTML emails using JavaMailSender,
 * complete with support for MailHog local development, structured MDC logging,
 * and exception classification (Transient vs Permanent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:localhost}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

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

        if (isMockMode()) {
            log.warn("[MOCK EMAIL] SMTP credentials unconfigured or default dummy. Simulated email dispatch to [{}]: subject=[{}]",
                    payload.getRecipient(), payload.getSubject());
            MDC.remove("channel");
            MDC.remove("recipient");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String fromAddress = (mailUsername != null && !mailUsername.isBlank() && mailUsername.contains("@"))
                    ? mailUsername : "noreply@chauhan.com";

            helper.setFrom(fromAddress);
            helper.setTo(payload.getRecipient());
            helper.setSubject(payload.getSubject());
            helper.setText(payload.getBody(), true);

            mailSender.send(mimeMessage);
            log.info("Successfully dispatched email notification to [{}] via SMTP host [{}]", payload.getRecipient(), mailHost);
        } catch (MailAuthenticationException e) {
            log.error("[EMAIL] SMTP authentication failed for host [{}] user [{}]. Please verify SMTP credentials.", mailHost, mailUsername, e);
            throw new PermanentNotificationException("SMTP Authentication failed for email recipient: " + payload.getRecipient(), e);
        } catch (MailException | MessagingException e) {
            log.error("Transient error encountered sending email notification to [{}] via host [{}]", payload.getRecipient(), mailHost, e);
            throw new TransientNotificationException("Transient error dispatching email to " + payload.getRecipient(), e);
        } catch (Exception e) {
            log.error("Permanent failure encountered sending email notification to [{}]", payload.getRecipient(), e);
            throw new PermanentNotificationException("Permanent error dispatching email to " + payload.getRecipient(), e);
        } finally {
            MDC.remove("channel");
            MDC.remove("recipient");
        }
    }

    private boolean isMockMode() {
        // Local MailHog / dev SMTP server (localhost / 127.0.0.1 / mailhog) does not require authentication
        if (isLocalSmtpServer()) {
            return false;
        }

        return (mailUsername == null || mailUsername.isBlank() || mailUsername.contains("your-email") || mailUsername.contains("dummy"))
                && (mailPassword == null || mailPassword.isBlank() || mailPassword.contains("your-app-password") || mailPassword.contains("dummy"));
    }

    private boolean isLocalSmtpServer() {
        return mailHost != null && (mailHost.equalsIgnoreCase("localhost") || mailHost.equals("127.0.0.1") || mailHost.toLowerCase().contains("mailhog"));
    }
}
