package com.chauhan.authservice.security;

import com.chauhan.authservice.dto.response.ApiError;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomAccessDeniedHandler.class);
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        
        // <<< THE CRITICAL DEBUG LOG FOR 403 ERRORS >>>
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            logger.warn("Access Denied: User '{}' with authorities '{}' attempted to access a protected resource: {} {}",
                    auth.getName(),
                    auth.getAuthorities(),
                    request.getMethod(),
                    request.getRequestURI());
        } else {
            // This case is rare but possible if the security context was cleared somehow
            logger.warn("Access Denied: An unauthenticated or anonymous user attempted to access a protected resource: {} {}",
                    request.getMethod(),
                    request.getRequestURI());
        }

        // Set the response status and content type
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");

        // Create the standardized error response
        ApiError error = ApiError.of(
            HttpStatus.FORBIDDEN.value(),
            "Access Denied",
            accessDeniedException.getMessage() +"(Check User Roles)",
            request.getRequestURI(),
            true
        );
        
        // Write the JSON response
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}