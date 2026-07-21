package com.chauhan.notificationservice.config;

import com.chauhan.notificationservice.exception.PermanentNotificationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * RabbitMQ Configuration for Notification Service.
 * Defines Topic Exchange, Queues, Routing Keys, Dead Letter Exchange (DLX),
 * Dead Letter Queue (DLQ), Jackson JSON deserialization, exponential retry interceptor,
 * and custom Fatal Exception Strategy for Permanent errors.
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
     * Configures JacksonJsonMessageConverter for AMQP JSON payload serialization/deserialization.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return new JacksonJsonMessageConverter(String.valueOf(mapper));
    }

    /**
     * Stateless Retry Operations Interceptor with exponential backoff (initial=1000ms, multiplier=2.0, max=10000ms, maxRetries=3)
     * and RejectAndDontRequeueRecoverer to ensure failed messages route directly to DLQ after retries.
     */
    @Bean
    public MethodInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /**
     * Custom ConditionalRejectingErrorHandler that treats PermanentNotificationException as fatal,
     * immediately rejecting the message to DLQ without retrying.
     */
    @Bean
    public ConditionalRejectingErrorHandler customErrorHandler() {
        return new ConditionalRejectingErrorHandler(new ConditionalRejectingErrorHandler.DefaultExceptionStrategy() {
            @Override
            public boolean isFatal(Throwable t) {
                if (t != null && (t instanceof PermanentNotificationException || t.getCause() instanceof PermanentNotificationException)) {
                    return true;
                }
                return super.isFatal(t);
            }
        });
    }

    /**
     * Configures SimpleRabbitListenerContainerFactory to use JacksonJsonMessageConverter,
     * exponential retry interceptor, and custom error handler.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAdviceChain(retryInterceptor());
        factory.setErrorHandler(customErrorHandler());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
