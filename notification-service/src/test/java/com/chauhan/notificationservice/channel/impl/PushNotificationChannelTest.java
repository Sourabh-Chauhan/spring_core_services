package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushNotificationChannelTest {

    private PushNotificationChannel pushChannel;

    @BeforeEach
    void setUp() {
        pushChannel = new PushNotificationChannel();
    }

    @Test
    void testSupports() {
        assertTrue(pushChannel.supports(NotificationType.PUSH));
        assertFalse(pushChannel.supports(NotificationType.EMAIL));
        assertFalse(pushChannel.supports(NotificationType.SMS));
        assertFalse(pushChannel.supports(NotificationType.WEBHOOK));
    }

    @Test
    void testSendUninitializedMockMode() {
        NotificationPayload payload = NotificationPayload.builder()
                .recipient("test-device-token")
                .subject("Push Title")
                .body("Hello from Push Notification Channel!")
                .type(NotificationType.PUSH)
                .build();

        assertDoesNotThrow(() -> pushChannel.send(payload));
    }
}
