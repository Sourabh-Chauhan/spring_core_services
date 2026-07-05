package com.chauhan.authservice.exceptions;

/**
 * RESPONSIBILITY:
 * Custom exception representing an HTTP 404 (Not Found) state when a database lookup
 * for a user or role returns empty.
 *
 * ISSUES / SECURITY CONCERNS:
 * - None.
 *
 * TODO:
 * - Make the error message more detailed or dynamic if required.
 */
public class ResourceNotFoundException extends  RuntimeException{

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(){
        super("Resource not found !!");
    }



}