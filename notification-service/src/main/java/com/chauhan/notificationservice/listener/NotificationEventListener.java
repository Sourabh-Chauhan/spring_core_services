package com.chauhan.notificationservice.listener;

import com.chauhan.notificationservice.config.RabbitMQConfig;
import com.chauhan.notificationservice.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ Event Listener for handling incoming notification events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    /**
     * Consumes user registration events from the 'notification.email.registration' queue.
     *
     * @param event The deserialized UserRegisteredEvent payload.
     */
    @RabbitListener(queues = RabbitMQConfig.REGISTRATION_QUEUE_NAME)
    public void handleUserRegistrationEvent(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: userId={}, email={}, name={}, timestamp={}",
                event.getUserId(), event.getEmail(), event.getName(), event.getTimestamp());

        try {
            // Process user registration notification (e.g. email dispatch)
            log.info("Successfully processed UserRegisteredEvent for user: {}", event.getEmail());
        } catch (Exception e) {
            log.error("Failed to process UserRegisteredEvent for user: {}", event.getEmail(), e);
            throw e; // Re-throw to trigger RabbitMQ retry / Dead Letter Queue routing
        }
    }
}
