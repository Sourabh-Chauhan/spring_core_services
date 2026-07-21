package com.chauhan.notificationservice.listener;

import com.chauhan.notificationservice.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Dead Letter Queue (DLQ) Event Listener.
 * Consumes failed or unprocessable messages from 'notification.dlq', parses RabbitMQ 'x-death' headers,
 * and emits structured diagnostic logs for auditing and alerting.
 */
@Component
@Slf4j
public class DLQEventListener {

    /**
     * Consumes dead-lettered messages from the 'notification.dlq' queue.
     *
     * @param message The raw AMQP message containing message properties and headers.
     */
    @RabbitListener(queues = RabbitMQConfig.DLQ_QUEUE_NAME)
    public void handleDeadLetterMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        Map<String, Object> headers = properties.getHeaders();
        List<Map<String, Object>> xDeathList = (List<Map<String, Object>>) headers.get("x-death");

        String originalQueue = "UNKNOWN";
        String originalReason = "UNKNOWN";
        long deathCount = 1;

        if (xDeathList != null && !xDeathList.isEmpty()) {
            Map<String, Object> xDeath = xDeathList.get(0);
            originalQueue = String.valueOf(xDeath.get("queue"));
            originalReason = String.valueOf(xDeath.get("reason"));
            Object countObj = xDeath.get("count");
            if (countObj instanceof Number) {
                deathCount = ((Number) countObj).longValue();
            }
        }

        MDC.put("dlq", "true");
        MDC.put("originalQueue", originalQueue);
        MDC.put("deathCount", String.valueOf(deathCount));

        log.error("==================== DEAD LETTER QUEUE (DLQ) MESSAGE RECEIVED ====================");
        log.error("Original Queue  : {}", originalQueue);
        log.error("Death Reason    : {}", originalReason);
        log.error("Death Count     : {}", deathCount);
        log.error("Routing Key     : {}", properties.getReceivedRoutingKey());
        log.error("Message Payload : {}", payload);
        log.error("==================================================================================");

        MDC.remove("dlq");
        MDC.remove("originalQueue");
        MDC.remove("deathCount");
    }
}
