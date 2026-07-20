package com.chauhan.notificationservice.channel.impl;

import com.chauhan.notificationservice.channel.NotificationChannel;
import com.chauhan.notificationservice.model.NotificationPayload;
import com.chauhan.notificationservice.model.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Implementation of NotificationChannel for dispatching Webhook HTTP notifications using non-blocking WebClient with HMAC-SHA256 security signatures.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookNotificationChannel implements NotificationChannel {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.notification.webhook.secret-key:default_webhook_hmac_secret_key}")
    private String secretKey;

    @Value("${app.notification.webhook.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Override
    public boolean supports(NotificationType type) {
        return NotificationType.WEBHOOK.equals(type);
    }

    @Override
    public void send(NotificationPayload payload) {
        String targetUrl = payload.getRecipient();
        log.info("Dispatching Webhook notification to URL [{}]", targetUrl);

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            String signatureHeader = calculateHmacSha256(jsonPayload, secretKey);

            log.debug("Generated X-Hub-Signature-256 header: [{}] for URL [{}]", signatureHeader, targetUrl);

            webClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Hub-Signature-256", signatureHeader)
                    .bodyValue(jsonPayload)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(readTimeoutMs));

            log.info("Successfully dispatched Webhook notification to URL [{}]", targetUrl);
        } catch (Exception e) {
            log.error("Failed to send Webhook notification to URL [{}]", targetUrl, e);
            throw new RuntimeException("Webhook notification dispatch failed for URL: " + targetUrl, e);
        }
    }

    /**
     * Computes the HMAC-SHA256 signature of the raw JSON body and formats it with the standard sha256= prefix.
     *
     * @param data   The raw payload string to sign.
     * @param secret The shared secret key.
     * @return Formatted signature string (e.g. sha256=a1b2c3...)
     */
    public String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }
}
