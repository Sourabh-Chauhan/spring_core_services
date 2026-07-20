package com.chauhan.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Event received when a user requests a password reset in auth-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetRequestedEvent implements Serializable {
    private UUID userId;
    private String email;
    private String name;
    private String resetToken;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
