package com.chauhan.gatewayservice.config;

import com.chauhan.gatewayservice.security.JwtValidationFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

// @Configuration
public class GatewayConfig {

    /**
     * IP Key Resolver for Rate Limiting.
     * Resolves the request rate limiting key using the client's remote IP address.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .defaultIfEmpty("127.0.0.1");
    }

    /**
     * Programmatic Route Configuration Reference.
     * Note: The @Bean annotation is commented out for now to allow properties-based routing
     * (configured in application.yaml under spring.cloud.gateway.server.webflux) to take precedence.
     *
     * To switch to programmatic routing:
     * 1. Uncomment the @Bean annotation below.
     * 2. Remove the routes configuration from application.yaml.
     */
    // @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, JwtValidationFilter jwtValidationFilter, KeyResolver ipKeyResolver) {
        return builder.routes()
                // Route to Auth Service (Public endpoints)
                .route("auth-service", r -> r.path(
                        "/api/v1/auth/**",
                        "/api/v1/admin/**",
                        "/api/v1/sessions/**"
                )
                // To configure the rate limiter filter programmatically, you would call:
                // .filters(f -> f.requestRateLimiter(c -> c.setKeyResolver(ipKeyResolver)))
                .uri("lb://auth-service"))

                // Route to User Service (Secured via JwtValidationFilter)
                .route("user-service", r -> r.path("/api/v1/users/**")
                        .filters(f -> f
                                .filter(jwtValidationFilter.apply(new JwtValidationFilter.Config()))
                                // Programmatic rate limiting:
                                // .requestRateLimiter(c -> c.setKeyResolver(ipKeyResolver))
                        )
                        .uri("lb://user-service"))
                .build();
    }
}
