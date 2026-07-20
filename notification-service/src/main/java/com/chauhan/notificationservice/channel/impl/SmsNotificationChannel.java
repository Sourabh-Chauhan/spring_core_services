package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementation of NotificationChannel for dispatching SMS notifications using Twilio SDK.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationChannel implements NotificationChannel {

    @Value("${app.notification.twilio.from-phone-number:+1234567890}")
    private String fromPhoneNumber;

    @Value("${app.notification.twilio.account-sid:AC_dummy_account_sid}")
    private String accountSid;

    @Override
    public boolean supports(NotificationType type) {
        return NotificationType.SMS.equals(type);
    }

    @Override
    public void send(NotificationPayload payload) {
        log.info("Sending SMS notification to [{}]", payload.getRecipient());

        if (accountSid == null || accountSid.contains("dummy")) {
            log.warn("[MOCK SMS] Twilio credentials not configured. Simulated SMS dispatch to [{}]: {}",
                    payload.getRecipient(), payload.getBody());
            return;
        }

        try {
            Message message = Message.creator(
                    new PhoneNumber(payload.getRecipient()),
                    new PhoneNumber(fromPhoneNumber),
                    payload.getBody()
            ).create();

            log.info("Successfully dispatched SMS notification to [{}] with Twilio SID [{}]",
                    payload.getRecipient(), message.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS notification to [{}]", payload.getRecipient(), e);
            throw new RuntimeException("SMS dispatch failed for recipient: " + payload.getRecipient(), e);
        }
    }
}
