package com.chauhan.notificationservice.dispatcher;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.exception.PermanentNotificationException;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatcher component that routes notification payloads to all registered and matching NotificationChannels.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    /**
     * Dispatches a notification payload to matching channels based on payload type.
     *
     * @param payload The notification payload to dispatch.
     */
    public void dispatch(NotificationPayload payload) {
        if (payload == null || payload.getType() == null) {
            log.warn("Cannot dispatch null payload or unassigned notification type.");
            throw new PermanentNotificationException("Cannot dispatch null payload or unassigned notification type.");
        }

        NotificationType type = payload.getType();
        log.info("Dispatching notification payload of type [{}] to recipient [{}]", type, payload.getRecipient());

        boolean dispatched = false;
        for (NotificationChannel channel : channels) {
            if (channel.supports(type)) {
                try {
                    channel.send(payload);
                    dispatched = true;
                } catch (Exception e) {
                    log.error("Failed to send notification via channel [{}] for recipient [{}]",
                            channel.getClass().getSimpleName(), payload.getRecipient(), e);
                    throw e;
                }
            }
        }

        if (!dispatched) {
            log.warn("No matching notification channel registered for type [{}]", type);
            throw new PermanentNotificationException("No matching channel registered for type: " + type);
        }
    }
}
