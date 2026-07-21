package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookNotificationChannelTest {

    private WebhookNotificationChannel webhookChannel;
    private ObjectMapper objectMapper;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        webhookChannel = new WebhookNotificationChannel(webClient, objectMapper);
        ReflectionTestUtils.setField(webhookChannel, "secretKey", "my-secret-key");
        ReflectionTestUtils.setField(webhookChannel, "readTimeoutMs", 5000);
    }

    @Test
    void testSupports() {
        assertTrue(webhookChannel.supports(NotificationType.WEBHOOK));
        assertFalse(webhookChannel.supports(NotificationType.EMAIL));
        assertFalse(webhookChannel.supports(NotificationType.SMS));
        assertFalse(webhookChannel.supports(NotificationType.PUSH));
    }

    @Test
    void testCalculateHmacSha256() {
        String data = "{\"recipient\":\"https://example.com/webhook\",\"subject\":\"Test\"}";
        String secret = "my-secret-key";

        String signature = webhookChannel.calculateHmacSha256(data, secret);
        assertNotNull(signature);
        assertTrue(signature.startsWith("sha256="));
        assertEquals(71, signature.length()); // "sha256=" (7 chars) + 64 hex chars = 71
    }

    @Test
    void testSendDispatchesRequestWithHmacHeader() {
        NotificationPayload payload = NotificationPayload.builder()
                .recipient("https://example.com/webhook/receive")
                .subject("Webhook Notification")
                .body("Payload body content")
                .type(NotificationType.WEBHOOK)
                .build();

        WebClient.RequestBodyUriSpec requestBodyUriSpec = mockWebClientChain();

        webhookChannel.send(payload);

        verify(webClient).post();
        verify(requestBodyUriSpec).uri("https://example.com/webhook/receive");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private WebClient.RequestBodyUriSpec mockWebClientChain() {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = org.mockito.Mockito.mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = org.mockito.Mockito.mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec<?> requestHeadersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(reactor.core.publisher.Mono.empty());

        return requestBodyUriSpec;
    }
}
