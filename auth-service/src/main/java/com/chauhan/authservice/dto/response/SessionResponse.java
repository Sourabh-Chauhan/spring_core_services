package com.chauhan.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    private UUID sessionId;
    private String ipAddress;
    private String deviceInfo;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean currentSession;
}
