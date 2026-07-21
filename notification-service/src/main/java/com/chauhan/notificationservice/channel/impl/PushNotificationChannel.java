package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.exception.PermanentNotificationException;
import com.chauhan.notificationservice.exception.TransientNotificationException;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Implementation of NotificationChannel for dispatching Push Notifications using Firebase Admin SDK,
 * with structured MDC logging and exception handling.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushNotificationChannel implements NotificationChannel {

    @Override
    public boolean supports(NotificationType type) {
        return NotificationType.PUSH.equals(type);
    }

    @Override
    public void send(NotificationPayload payload) {
        if (payload.getRecipient() == null || payload.getRecipient().isBlank()) {
            log.error("[MDC channel=PUSH] Missing recipient device token or topic.");
            throw new PermanentNotificationException("Recipient token/topic is required for Push channel.");
        }

        MDC.put("channel", "PUSH");
        MDC.put("recipient", payload.getRecipient());
        log.info("Sending Push notification to [{}] with title [{}]", payload.getRecipient(), payload.getSubject());

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[MOCK PUSH] FirebaseApp uninitialized. Simulated Push notification to [{}]: title=[{}], body=[{}]",
                    payload.getRecipient(), payload.getSubject(), payload.getBody());
            MDC.remove("channel");
            MDC.remove("recipient");
            return;
        }

        try {
            Notification notification = Notification.builder()
                    .setTitle(payload.getSubject())
                    .setBody(payload.getBody())
                    .build();

            Message.Builder messageBuilder = Message.builder()
                    .setNotification(notification);

            String recipient = payload.getRecipient();
            if (recipient.startsWith("/topics/")) {
                messageBuilder.setTopic(recipient.substring("/topics/".length()));
            } else {
                messageBuilder.setToken(recipient);
            }

            if (payload.getMetadata() != null && !payload.getMetadata().isEmpty()) {
                payload.getMetadata().forEach((key, value) -> {
                    if (value != null) {
                        messageBuilder.putData(key, String.valueOf(value));
                    }
                });
            }

            Message message = messageBuilder.build();
            String response = FirebaseMessaging.getInstance().send(message);

            log.info("Successfully dispatched Push notification to [{}] with Firebase message ID [{}]", recipient, response);
        } catch (FirebaseMessagingException e) {
            String errorCode = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "UNKNOWN";
            if ("INVALID_ARGUMENT".equals(errorCode) || "UNREGISTERED".equals(errorCode) || "SENDER_ID_MISMATCH".equals(errorCode)) {
                log.error("Permanent Firebase Push error for recipient [{}]: code={}", payload.getRecipient(), errorCode, e);
                throw new PermanentNotificationException("Firebase Push client error: " + errorCode, e);
            }
            log.error("Transient Firebase Push error for recipient [{}]: code={}", payload.getRecipient(), errorCode, e);
            throw new TransientNotificationException("Firebase Push transient error: " + errorCode, e);
        } catch (Exception e) {
            log.error("Failed to send Push notification to [{}]", payload.getRecipient(), e);
            throw new TransientNotificationException("Push notification dispatch failed for recipient: " + payload.getRecipient(), e);
        } finally {
            MDC.remove("channel");
            MDC.remove("recipient");
        }
    }
}
