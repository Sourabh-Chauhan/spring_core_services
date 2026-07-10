package com.chauhan.gatewayservice.exception;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@Order(-2) // High priority to run before default Spring Boot error handlers
public class GlobalExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // If the response has already been committed, delegate down the filter chain
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorName = "Internal Server Error";
        String message = ex.getMessage();

        // 1. Handle ResponseStatusException (e.g. Gateway Timeouts, 429 Rate Limits, etc.)
        if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            status = rse.getStatusCode();
            message = rse.getReason();
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                errorName = "Too Many Requests";
                message = "Rate limit exceeded. Please try again later.";
            } else if (status == HttpStatus.UNAUTHORIZED) {
                errorName = "Unauthorized";
            } else if (status == HttpStatus.FORBIDDEN) {
                errorName = "Forbidden";
            }
        }
        // 2. Handle Spring Security AuthenticationException (401 Unauthorized)
        else if (ex instanceof AuthenticationException) {
            status = HttpStatus.UNAUTHORIZED;
            errorName = "Unauthorized";
            message = ex.getMessage();
        }
        // 3. Handle Spring Security AccessDeniedException (403 Forbidden)
        else if (ex instanceof AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
            errorName = "Forbidden";
            message = "Access Denied: You do not have permissions to access this resource.";
        }
        // 4. Handle HttpStatusCodeException (e.g. HttpClientErrorException.TooManyRequests)
        else if (ex instanceof org.springframework.web.client.HttpStatusCodeException) {
            org.springframework.web.client.HttpStatusCodeException hsce = (org.springframework.web.client.HttpStatusCodeException) ex;
            status = hsce.getStatusCode();
            message = hsce.getStatusText();
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                errorName = "Too Many Requests";
                message = "Rate limit exceeded. Please try again later.";
            } else if (status == HttpStatus.UNAUTHORIZED) {
                errorName = "Unauthorized";
            } else if (status == HttpStatus.FORBIDDEN) {
                errorName = "Forbidden";
            }
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Escape JSON double quotes if message contains them
        String cleanMessage = message != null ? message.replace("\"", "\\\"") : "";

        // Build standardized JSON response
        String json = String.format("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\",\"timestamp\":\"%s\"}",
                status.value(),
                errorName,
                cleanMessage,
                exchange.getRequest().getPath().value(),
                Instant.now().toString());

        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
