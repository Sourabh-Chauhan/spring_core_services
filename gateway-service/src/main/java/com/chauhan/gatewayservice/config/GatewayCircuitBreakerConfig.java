package com.chauhan.gatewayservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class GatewayCircuitBreakerConfig {

    /**
     * Configures the default circuit breaker and time limiter settings.
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)                  // Track health of last 10 requests
                        .failureRateThreshold(50.0f)            // Trip open if 50% or more fail
                        .waitDurationInOpenState(Duration.ofSeconds(10)) // Wait 10 seconds before switching to half-open
                        .permittedNumberOfCallsInHalfOpenState(3)        // Test with 3 requests in half-open state
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3)) // Downstream timeout of 3 seconds
                        .build())
                .build());
    }
}
