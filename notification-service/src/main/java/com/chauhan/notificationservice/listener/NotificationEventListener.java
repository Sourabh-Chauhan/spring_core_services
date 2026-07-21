package com.chauhan.notificationservice.listener;

import com.chauhan.notificationservice.config.RabbitMQConfig;
import com.chauhan.notificationservice.dispatcher.NotificationDispatcher;
import com.chauhan.notificationservice.event.UserRegisteredEvent;
import com.chauhan.notificationservice.exception.PermanentNotificationException;

import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ Event Listener for handling incoming notification events with MDC context enrichment and exception propagation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationDispatcher notificationDispatcher;

    /**
     * Consumes user registration events from the 'notification.email.registration' queue.
     *
     * @param event The deserialized UserRegisteredEvent payload.
     */
    @RabbitListener(queues = RabbitMQConfig.REGISTRATION_QUEUE_NAME)
    public void handleUserRegistrationEvent(UserRegisteredEvent event) {
        if (event == null || event.getEmail() == null) {
            log.error("Received null or malformed UserRegisteredEvent payload.");
            throw new PermanentNotificationException("Received null or malformed UserRegisteredEvent payload.");
        }

        MDC.put("userId", String.valueOf(event.getUserId()));
        MDC.put("email", event.getEmail());

        log.info("Processing UserRegisteredEvent: userId={}, email={}, name={}, timestamp={}",
                event.getUserId(), event.getEmail(), event.getName(), event.getTimestamp());

        try {
            String htmlContent = buildWelcomeEmailHtml(event.getName(), event.getVerificationToken());

            NotificationPayload payload = NotificationPayload.builder()
                    .recipient(event.getEmail())
                    .subject("Welcome to Spring Core Services - Verify Your Account")
                    .body(htmlContent)
                    .type(NotificationType.EMAIL)
                    .build();

            notificationDispatcher.dispatch(payload);
            log.info("Successfully completed processing UserRegisteredEvent for user: {}", event.getEmail());
        } catch (Exception e) {
            log.error("Failed to process UserRegisteredEvent for user: {}", event.getEmail(), e);
            throw e;
        } finally {
            MDC.remove("userId");
            MDC.remove("email");
        }
    }

    private String buildWelcomeEmailHtml(String name, String verificationToken) {
        String displayName = (name != null && !name.isBlank()) ? name : "User";
        String token = (verificationToken != null && !verificationToken.isBlank()) ? verificationToken : "N/A";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <title>Welcome to Spring Core Services</title>
                </head>
                <body style="font-family: Arial, sans-serif; background-color: #f4f6f8; margin: 0; padding: 20px;">
                  <table width="100%" border="0" cellspacing="0" cellpadding="0" style="max-width: 600px; margin: auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.05);">
                    <tr style="background-color: #1a73e8; color: #ffffff;">
                      <td style="padding: 20px; text-align: center;">
                        <h1 style="margin: 0; font-size: 24px;">Welcome to Spring Core Services</h1>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding: 30px; color: #333333; line-height: 1.6;">
                        <p>Hi <strong>{{NAME}}</strong>,</p>
                        <p>Thank you for registering! Your account has been created successfully. Please use the verification token below to complete your registration:</p>
                        <div style="background-color: #f0f4f9; padding: 15px; border-radius: 6px; font-family: monospace; font-size: 16px; word-break: break-all; margin: 20px 0; text-align: center; color: #1a73e8;">
                          {{TOKEN}}
                        </div>
                        <p>If you did not initiate this request, please ignore this email.</p>
                        <br>
                        <p>Best regards,<br>The Spring Core Team</p>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.replace("{{NAME}}", displayName).replace("{{TOKEN}}", token);
    }
}
