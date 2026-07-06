package com.chauhan.authservice.config;

/**
 * RESPONSIBILITY:
 * Defines global constant values for security roles and URL routing patterns (public, admin, guest) 
 * used to configure web security rules.
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Role Naming Mismatch: The role constants ADMIN_ROLE ("ADMIN") and GUEST_ROLE ("GUEST") do not contain 
 *    the standard "ROLE_" prefix. Spring Security's `hasRole()` method implicitly prepends "ROLE_" to the role name,
 *    which can cause authorization mismatches if the database roles are stored without the "ROLE_" prefix.
 *
 * TODO:
 * - Ensure database roles match the Spring Security authority model (either use hasAuthority or change role values to start with "ROLE_").
 */
public class AppConstants {
    public static final String[] AUTH_PUBLIC_URLS = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/login/oauth2/**",
            "/oauth2/**"
    };

    public static final String[] AUTH_ADMIN_URLS= {
            "/api/v1/users/**"
    };

    public static final String[] AUTH_GUEST_URLS= {

    };

    public static final String ADMIN_ROLE = "ADMIN";
    public static final String GUEST_ROLE = "GUEST";
    public static final String USER_ROLE = "USER";
}
