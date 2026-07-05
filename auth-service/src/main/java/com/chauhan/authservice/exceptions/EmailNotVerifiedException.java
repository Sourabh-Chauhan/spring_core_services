package com.chauhan.authservice.exceptions;

import org.springframework.security.core.AuthenticationException;

public class EmailNotVerifiedException extends AuthenticationException {
    
    public EmailNotVerifiedException(String msg) {
        super(msg);
    }
}
