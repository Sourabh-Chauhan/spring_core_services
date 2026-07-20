package com.chauhan.notificationservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Notification Service.
 * Defines Topic Exchange, Queues, Routing Keys, Dead Letter Exchange (DLX),
 * and Jackson JSON deserialization configuration for AMQP listeners.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "notification.exchange";
    public static final String REGISTRATION_QUEUE_NAME = "notification.email.registration";
    public static final String REGISTRATION_ROUTING_KEY = "user.registered";

    public static final String DLX_EXCHANGE_NAME = "notification.dlx";
    public static final String DLQ_QUEUE_NAME = "notification.dlq";
    public static final String DLQ_ROUTING_KEY = "notification.dlq.routingKey";

    /**
     * Primary Topic Exchange for routing notification events.
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    /**
     * Dead Letter Exchange (DLX) for capturing failed or unprocessable messages.
     */
    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX_EXCHANGE_NAME);
    }

    /**
     * Dead Letter Queue (DLQ) to store failed messages for inspection/retry.
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE_NAME).build();
    }

    /**
     * Binds Dead Letter Queue to Dead Letter Exchange.
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }

    /**
     * Main Queue for user registration email notifications.
     * Configured with Dead Letter Exchange arguments for automatic rerouting on failure.
     */
    @Bean
    public Queue registrationNotificationQueue() {
        return QueueBuilder.durable(REGISTRATION_QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Binds main registration queue to primary topic exchange using routing key 'user.registered'.
     */
    @Bean
    public Binding registrationBinding() {
        return BindingBuilder.bind(registrationNotificationQueue())
                .to(notificationExchange())
                .with(REGISTRATION_ROUTING_KEY);
    }

    /**
     * Configures Jackson2JsonMessageConverter for AMQP JSON payload serialization/deserialization.
     * Registers JavaTimeModule for Java 8 Instant handling and disables failure on unknown properties.
     */
    @Bean
    @SuppressWarnings("deprecation")
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * Configures SimpleRabbitListenerContainerFactory to use Jackson2JsonMessageConverter.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
