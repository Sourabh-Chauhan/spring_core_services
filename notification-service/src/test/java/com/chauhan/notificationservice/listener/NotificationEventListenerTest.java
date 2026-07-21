package com.chauhan.notificationservice.listener;

import com.chauhan.notificationservice.dispatcher.NotificationDispatcher;
import com.chauhan.notificationservice.event.UserRegisteredEvent;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class NotificationEventListenerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new NotificationEventListener(notificationDispatcher);
    }

    @Test
    void testHandleUserRegistrationEvent_Success() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .name("John Doe")
                .verificationToken("token-12345")
                .timestamp(Instant.now())
                .build();

        assertDoesNotThrow(() -> listener.handleUserRegistrationEvent(event));

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationDispatcher).dispatch(payloadCaptor.capture());

        NotificationPayload payload = payloadCaptor.getValue();
        assertNotNull(payload);
        assertEquals("test@example.com", payload.getRecipient());
        assertEquals(NotificationType.EMAIL, payload.getType());
        assertTrue(payload.getBody().contains("John Doe"));
        assertTrue(payload.getBody().contains("token-12345"));
        assertTrue(payload.getBody().contains("width=\"100%\""));
    }
}
