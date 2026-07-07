package com.chauhan.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(unique = true, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String eventType; // e.g., LOGIN_SUCCESS, LOGIN_FAILURE, PASSWORD_CHANGE

    private String email;     // The email or username of the user who initiated the event

    private String ipAddress;

    private String userAgent;

    @Column(length = 2000)
    private String details;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
