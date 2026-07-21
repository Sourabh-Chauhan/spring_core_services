package com.chauhan.notificationservice.exception;

/**
 * Exception indicating an unrecoverable/permanent failure (e.g. invalid payload format, malformed email/phone, 4xx client error)
 * that should immediately bypass retries and route the message to the Dead Letter Queue (DLQ).
 */
public class PermanentNotificationException extends NotificationException {

    public PermanentNotificationException(String message) {
        super(message);
    }

    public PermanentNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
