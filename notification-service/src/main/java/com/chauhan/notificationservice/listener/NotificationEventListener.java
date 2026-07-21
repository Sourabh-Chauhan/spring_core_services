package com.chauhan.notificationservice.listener;

import com.chauhan.notificationservice.config.RabbitMQConfig;
import com.chauhan.notificationservice.dispatcher.NotificationDispatcher;
import com.chauhan.notificationservice.event.UserRegisteredEvent;
import com.chauhan.notificationservice.exception.PermanentNotificationException;

import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import com.chauhan.notificationservice.service.TemplateRenderingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ Event Listener for handling incoming notification events with MDC context enrichment and externalized Thymeleaf template rendering.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationDispatcher notificationDispatcher;
    private final TemplateRenderingService templateRenderingService;

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
            String displayName = (event.getName() != null && !event.getName().isBlank()) ? event.getName() : "User";
            String token = (event.getVerificationToken() != null && !event.getVerificationToken().isBlank()) ? event.getVerificationToken() : "N/A";

            String htmlContent = templateRenderingService.render("email/welcome-email", Map.of(
                    "name", displayName,
                    "verificationToken", token
            ));

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
}
