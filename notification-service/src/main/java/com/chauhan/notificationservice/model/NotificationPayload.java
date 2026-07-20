package com.chauhan.notificationservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Universal payload object passed to notification channels for dispatching.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPayload {

    private String recipient;
    private String subject;
    private String body;
    private NotificationType type;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
