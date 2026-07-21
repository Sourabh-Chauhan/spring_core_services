package com.chauhan.notificationservice.exception;

/**
 * Exception indicating a temporary/transient failure (e.g. SMTP/HTTP timeout, network issue, rate-limit 5xx)
 * that should trigger automatic retry with exponential backoff.
 */
public class TransientNotificationException extends NotificationException {

    public TransientNotificationException(String message) {
        super(message);
    }

    public TransientNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
