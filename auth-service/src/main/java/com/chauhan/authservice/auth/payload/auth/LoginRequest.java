package com.chauhan.authservice.auth.payload.auth;

public record LoginRequest( String email,
                            String password) {
}
