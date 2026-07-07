package com.chauhan.authservice.event;

import com.chauhan.authservice.entity.AuditLog;
import com.chauhan.authservice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    public void handleAuditEvent(AuditEvent event) {
        log.info("Processing audit event asynchronously: {}", event);
        try {
            AuditLog auditLog = AuditLog.builder()
                    .eventType(event.getEventType())
                    .email(event.getEmail() != null ? event.getEmail() : "UNKNOWN")
                    .ipAddress(event.getIpAddress() != null ? event.getIpAddress() : "UNKNOWN")
                    .userAgent(event.getUserAgent() != null ? event.getUserAgent() : "UNKNOWN")
                    .details(event.getDetails())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for event: {}", event, e);
        }
    }
}
