package com.chauhan.gatewayservice.config;

import com.chauhan.gatewayservice.security.JwtValidationFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Uncomment the @Annotation to enable Programmatic Java Routing
//@Configuration
public class GatewayConfig {

   // @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, JwtValidationFilter jwtValidationFilter) {
        return builder.routes()
                // Route to Auth Service (Public endpoints)
                .route("auth-service", r -> r.path(
                        "/api/v1/auth/**",
                        "/api/v1/admin/**",
                        "/api/v1/sessions/**"
                ).uri("lb://auth-service"))

                // Route to User Service (Secured via JwtValidationFilter)
                .route("user-service", r -> r.path("/api/v1/users/**")
                        .filters(f -> f.filter(jwtValidationFilter.apply(new JwtValidationFilter.Config())))
                        .uri("lb://user-service"))
                .build();
    }
}
