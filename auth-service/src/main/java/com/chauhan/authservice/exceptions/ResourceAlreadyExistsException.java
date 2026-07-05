package com.chauhan.authservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * RESPONSIBILITY:
 * Custom exception representing a conflict (HTTP 409 status if unhandled, mapped to 400 Bad Request by the GlobalExceptionHandler)
 * indicating that a resource (like a user email) already exists in the database.
 *
 * ISSUES / SECURITY CONCERNS:
 * - None. Mapped properly.
 *
 * TODO:
 * - Make the error message more detailed or dynamic if required.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ResourceAlreadyExistsException extends RuntimeException {

    public ResourceAlreadyExistsException(String message) {
        super(message);
    }
}
