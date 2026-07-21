package com.chauhan.notificationservice.listener;

import com.chauhan.notificationservice.dispatcher.NotificationDispatcher;
import com.chauhan.notificationservice.event.UserRegisteredEvent;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import com.chauhan.notificationservice.service.TemplateRenderingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventListenerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private TemplateRenderingService templateRenderingService;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new NotificationEventListener(notificationDispatcher, templateRenderingService);
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

        when(templateRenderingService.render(eq("email/welcome-email"), anyMap()))
                .thenReturn("<html>Welcome John Doe token-12345</html>");

        assertDoesNotThrow(() -> listener.handleUserRegistrationEvent(event));

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationDispatcher).dispatch(payloadCaptor.capture());

        NotificationPayload payload = payloadCaptor.getValue();
        assertNotNull(payload);
        assertEquals("test@example.com", payload.getRecipient());
        assertEquals(NotificationType.EMAIL, payload.getType());
        assertTrue(payload.getBody().contains("John Doe"));
        assertTrue(payload.getBody().contains("token-12345"));
    }
}
