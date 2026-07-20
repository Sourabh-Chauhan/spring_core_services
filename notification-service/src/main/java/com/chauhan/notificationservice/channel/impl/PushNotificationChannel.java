package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementation of NotificationChannel for dispatching Push Notifications using Firebase Admin SDK.
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
        log.info("Sending Push notification to [{}] with title [{}]", payload.getRecipient(), payload.getSubject());

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[MOCK PUSH] FirebaseApp uninitialized. Simulated Push notification to [{}]: title=[{}], body=[{}]",
                    payload.getRecipient(), payload.getSubject(), payload.getBody());
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
            if (recipient != null && recipient.startsWith("/topics/")) {
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
        } catch (Exception e) {
            log.error("Failed to send Push notification to [{}]", payload.getRecipient(), e);
            throw new RuntimeException("Push notification dispatch failed for recipient: " + payload.getRecipient(), e);
        }
    }
}
