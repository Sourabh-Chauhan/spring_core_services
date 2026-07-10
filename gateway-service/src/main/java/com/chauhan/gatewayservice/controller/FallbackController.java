package com.chauhan.gatewayservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        return getFallbackResponse("Authentication Service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping("/user")
    public Mono<ResponseEntity<Map<String, Object>>> userFallback() {
        return getFallbackResponse("User Service is temporarily unavailable. Please try again later.");
    }

    private Mono<ResponseEntity<Map<String, Object>>> getFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", message);
        response.put("timestamp", Instant.now().toString());

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }
}
