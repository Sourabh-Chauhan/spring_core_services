package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsNotificationChannelTest {

    private SmsNotificationChannel smsChannel;

    @BeforeEach
    void setUp() {
        smsChannel = new SmsNotificationChannel();
        ReflectionTestUtils.setField(smsChannel, "fromPhoneNumber", "+1234567890");
        ReflectionTestUtils.setField(smsChannel, "accountSid", "AC_dummy_account_sid");
    }

    @Test
    void testSupports() {
        assertTrue(smsChannel.supports(NotificationType.SMS));
        assertFalse(smsChannel.supports(NotificationType.EMAIL));
        assertFalse(smsChannel.supports(NotificationType.PUSH));
        assertFalse(smsChannel.supports(NotificationType.WEBHOOK));
    }

    @Test
    void testSendSimulatedMockMode() {
        NotificationPayload payload = NotificationPayload.builder()
                .recipient("+19876543210")
                .subject("SMS Subject")
                .body("Hello from SMS Notification Channel!")
                .type(NotificationType.SMS)
                .build();

        assertDoesNotThrow(() -> smsChannel.send(payload));
    }
}
