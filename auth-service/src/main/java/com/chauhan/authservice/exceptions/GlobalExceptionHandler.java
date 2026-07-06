package com.chauhan.authservice.exceptions;

import com.chauhan.authservice.dtos.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * RESPONSIBILITY:
 * Centralized exception interceptor. Converts standard and custom Java exceptions thrown in the application 
 * (like resource exceptions, validation errors, and authentication failures) into user-friendly JSON payloads (`ApiError`).
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Lack of Method Validation Execution: Although there's a handler for `MethodArgumentNotValidException`,
 *    neither `AuthController` nor `UserController` use `@Valid` in their mapping handlers, so this handler is never run.
 *
 * TODO:
 * - Ensure `@Valid` is added to request parameters/DTOs in controllers to make validation errors catchable.
 * - Add a specific handler for access-denied or authorization failures if they are not already managed by CustomAccessDeniedHandler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles specific authentication-related exceptions from Spring Security.
     * Returns a 401 Unauthorized status.
     */
    @ExceptionHandler({UsernameNotFoundException.class,
            BadCredentialsException.class,
            CredentialsExpiredException.class,
            DisabledException.class})
    public ResponseEntity<ApiError> handleAuthException(Exception ex, HttpServletRequest request) {
        logger.warn("Authentication failure: {}", ex.getMessage());
        ApiError apiError = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Authentication Failed", ex.getMessage(), request.getRequestURI());
        return new ResponseEntity<>(apiError, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles unverified email exceptions.
     * Returns a 403 Forbidden status.
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiError> handleEmailNotVerifiedException(EmailNotVerifiedException ex, HttpServletRequest request) {
        logger.warn("Email verification required: {}", ex.getMessage());
        ApiError apiError = ApiError.of(HttpStatus.FORBIDDEN.value(), "Email Not Verified", ex.getMessage(), request.getRequestURI());
        return new ResponseEntity<>(apiError, HttpStatus.FORBIDDEN);
    }

    /**
     * Handles exceptions for when a requested resource cannot be found.
     * Returns a 404 Not Found status.
     */
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex, HttpServletRequest request) {
        logger.info("Resource Already Exists : {}", ex.getMessage());
        ApiError apiError = ApiError.of(HttpStatus.BAD_REQUEST.value(), "Resource Already Exists", ex.getMessage(), request.getRequestURI());
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        logger.info("Resource not found: {}", ex.getMessage());
        ApiError apiError = ApiError.of(HttpStatus.NOT_FOUND.value(), "Resource Not Found", ex.getMessage(), request.getRequestURI());
        return new ResponseEntity<>(apiError, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles exceptions for illegal arguments, typically bad request data.
     * Returns a 400 Bad Request status.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        logger.info("Illegal argument: {}", ex.getMessage());
        ApiError apiError = ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI());
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles validation exceptions from @Valid annotation on request bodies.
     * Returns a 400 Bad Request status with a map of field-specific errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        logger.info("Validation failed: {}", errors);
        ApiError apiError = ApiError.of(HttpStatus.BAD_REQUEST.value(), "Validation Failed", "One or more fields have an error", request.getRequestURI(), errors);
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    /**
     * A catch-all handler for any other unexpected exceptions.
     * Logs the full error for debugging but returns a generic message to the client.
     * Returns a 500 Internal Server Error status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGlobalException(Exception ex, HttpServletRequest request) throws Exception {
        if (ex instanceof org.springframework.security.access.AccessDeniedException) {
            throw ex;
        }
        // Log the full stack trace for unexpected errors at the ERROR level
        logger.error("An unexpected error occurred", ex);
        ApiError apiError = ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "An unexpected error occurred. Please try again later.", request.getRequestURI());
        return new ResponseEntity<>(apiError, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
