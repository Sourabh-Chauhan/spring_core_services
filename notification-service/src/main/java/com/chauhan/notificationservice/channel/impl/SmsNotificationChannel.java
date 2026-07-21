package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.exception.PermanentNotificationException;
import com.chauhan.notificationservice.exception.TransientNotificationException;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementation of NotificationChannel for dispatching SMS notifications using Twilio SDK,
 * with structured MDC logging and exception handling.
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
        if (payload.getRecipient() == null || payload.getRecipient().isBlank()) {
            log.error("[MDC channel=SMS] Missing recipient phone number.");
            throw new PermanentNotificationException("Recipient phone number is required for SMS channel.");
        }

        MDC.put("channel", "SMS");
        MDC.put("recipient", payload.getRecipient());
        log.info("Sending SMS notification to [{}]", payload.getRecipient());

        if (accountSid == null || accountSid.contains("dummy")) {
            log.warn("[MOCK SMS] Twilio credentials not configured. Simulated SMS dispatch to [{}]: {}",
                    payload.getRecipient(), payload.getBody());
            MDC.remove("channel");
            MDC.remove("recipient");
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
        } catch (ApiException e) {
            Integer statusCode = e.getStatusCode();
            if (statusCode != null && statusCode >= 400 && statusCode < 500) {
                log.error("Permanent client error from Twilio API for recipient [{}]: statusCode={}", payload.getRecipient(), statusCode, e);
                throw new PermanentNotificationException("Twilio client error: " + e.getMessage(), e);
            }
            log.error("Transient error from Twilio API for recipient [{}]", payload.getRecipient(), e);
            throw new TransientNotificationException("Twilio transient error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send SMS notification to [{}]", payload.getRecipient(), e);
            throw new TransientNotificationException("SMS dispatch failed for recipient: " + payload.getRecipient(), e);
        } finally {
            MDC.remove("channel");
            MDC.remove("recipient");
        }
    }
}
