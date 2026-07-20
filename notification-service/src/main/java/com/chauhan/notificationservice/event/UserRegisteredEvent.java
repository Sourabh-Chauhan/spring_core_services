package com.chauhan.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Event received when a new user registers in auth-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisteredEvent implements Serializable {
    private UUID userId;
    private String email;
    private String name;
    private String verificationToken;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
