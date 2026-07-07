package com.chauhan.authservice.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

@Getter
@ToString
public class AuditEvent extends ApplicationEvent {
    private final String eventType;
    private final String email;
    private final String ipAddress;
    private final String userAgent;
    private final String details;

    public AuditEvent(Object source, String eventType, String email, String ipAddress, String userAgent, String details) {
        super(source);
        this.eventType = eventType;
        this.email = email;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.details = details;
    }
}
