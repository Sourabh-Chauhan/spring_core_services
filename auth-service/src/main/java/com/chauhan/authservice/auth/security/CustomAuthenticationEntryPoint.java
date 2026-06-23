package com.chauhan.authservice.auth.security;

import com.chauhan.authservice.dtos.ApiError;
//import com.fasterxml.jackson.databind.ObjectMapper; // Corrected import
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(CustomAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {

        // Determine the error message in a single step and make it final
        final String errorMessage = determineErrorMessage(request, authException);

        // Log the final, most specific error message
        logger.warn("Authentication Failed for request to '{}'. Reason: {}", request.getRequestURI(), errorMessage);

        // Set the response status and content type
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");

        // Create the standardized error response
        ApiError error = ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            "Authentication Failed",
            errorMessage,
            request.getRequestURI(),
            true
        );

        // Write the JSON response
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    /**
     * Determines the most specific error message available, preferring a message
     * set by the JwtFilter over the generic one from the AuthenticationException.
     */
    private String determineErrorMessage(HttpServletRequest request, AuthenticationException authException) {
        Object jwtErrorAttribute = request.getAttribute("jwt_error");
        if (jwtErrorAttribute != null) {
            return jwtErrorAttribute.toString();
        }
        return authException.getMessage();
    }
}
