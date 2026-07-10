package com.chauhan.gatewayservice.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class JwtValidationFilter extends AbstractGatewayFilterFactory<JwtValidationFilter.Config> {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    public JwtValidationFilter(JwtUtil jwtUtil, ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Extract Bearer Token from Authorization Header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header"));
            }

            String token = authHeader.substring(7);

            // 2. Validate JWT Signature and Expiration
            try {
                if (!jwtUtil.validateToken(token)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired JWT access token"));
                }

                String jti = jwtUtil.getJti(token);

                // 3. Check Shared Redis Blacklist
                return redisTemplate.hasKey("blacklist:" + jti)
                        .flatMap(isBlacklisted -> {
                            if (Boolean.TRUE.equals(isBlacklisted)) {
                                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is blacklisted (logged out)"));
                            }

                            // 4. Inject Downstream Headers (Token Relay)
                            ServerHttpRequest mutatedRequest = request.mutate()
                                    .header("X-User-Id", jwtUtil.getUserId(token).toString())
                                    .header("X-User-Email", jwtUtil.getEmail(token))
                                    .header("X-User-Roles", String.join(",", jwtUtil.getRoles(token)))
                                    .build();

                            return chain.filter(exchange.mutate().request(mutatedRequest).build());
                        });
            } catch (Exception e) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT validation failed: " + e.getMessage()));
            }
        };
    }

    public static class Config {
        // Configuration parameters can be defined here if needed
    }
}
