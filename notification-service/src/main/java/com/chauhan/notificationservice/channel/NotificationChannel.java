package com.chauhan.notificationservice.channel;

import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;

/**
 * Strategy interface for multi-channel notification implementations (Email, SMS, Push, Webhook).
 */
public interface NotificationChannel {

    /**
     * Determines whether this channel supports the given notification type.
     *
     * @param type The notification type to check.
     * @return true if supported, false otherwise.
     */
    boolean supports(NotificationType type);

    /**
     * Dispatches the notification payload to the target channel.
     *
     * @param payload The notification payload.
     */
    void send(NotificationPayload payload);
}
