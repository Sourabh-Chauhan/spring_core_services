package com.chauhan.authservice.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * A standardized error response object.
 * Using @JsonInclude(JsonInclude.Include.NON_NULL) ensures that fields with null values
 * (like validationErrors) are not included in the JSON output, keeping the response clean.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        OffsetDateTime timestamp,
        Map<String, String> validationErrors
) {
    // Factory method for standard errors without validation details
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, path, OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    // Factory method for validation errors
    public static ApiError of(int status, String error, String message, String path, Map<String, String> validationErrors) {
        return new ApiError(status, error, message, path, OffsetDateTime.now(ZoneOffset.UTC), validationErrors);
    }
    
    // Your existing factory method, kept for compatibility
    public static ApiError of(int status, String error, String message, String path, boolean notDateTime) {
        return new ApiError(status, error, message, path, null, null);
    }
}
