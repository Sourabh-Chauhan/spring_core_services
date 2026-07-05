package com.chauhan.authservice.config;


import com.chauhan.authservice.security.CustomAccessDeniedHandler;
import com.chauhan.authservice.security.CustomAuthenticationEntryPoint;
import com.chauhan.authservice.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * RESPONSIBILITY:
 * Core configuration class for Spring Security. Sets up stateless session management,
 * path-based authorization rules, CORS, password hashing using BCrypt, and hooks in custom filters (like JwtFilter).
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Commented-out Authorization Rules: The path rules restricting `/api/v1/users/**` to ADMIN are commented out.
 *    Any authenticated user has access to these endpoints, which is a major security flaw.
 * 2. Insecure CORS Configuration: `allowedOrigins("*")` is used, which is highly insecure for production environments.
 *
 * TODO:
 * - Uncomment and properly configure path-based role rules using `hasRole()` or `hasAuthority()`.
 * - Restrict CORS `allowedOrigins` to a specific whitelist.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity()
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Production-ready CORS configuration.
     * Defines exactly which origins, methods, and headers are allowed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow your frontend origins here (e.g., http://localhost:3000, https://myapp.com)
        configuration.setAllowedOrigins(List.of("*")); // WARNING: Change '*' to specific origins in production!
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization")); // If the frontend needs to read headers sent back
        configuration.setAllowCredentials(false); // Set to true if you are using cookies/session IDs (usually false for pure JWT)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this policy to all endpoints
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Core security configurations
            .csrf(AbstractHttpConfigurer::disable) // Disabled because we use stateless JWTs, not session cookies
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Explicit, strict CORS policy
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // No server-side sessions

            // 2. Exception Handling (Delegated to custom beans for clarity and testability)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint) // Handles 401 Unauthorized
                .accessDeniedHandler(accessDeniedHandler)           // Handles 403 Forbidden
            )

            // 3. Explicit Security Headers (Hardening the API)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'")) // Basic CSP: only load resources from the same origin
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny) // Prevent clickjacking (don't allow the app to be loaded in an iframe)
                .xssProtection(HeadersConfigurer.XXssConfig::disable) // Explicitly disable XSS protection header (recommended modern approach, rely on CSP instead)
            )

            // 4. Authorization Rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(AppConstants.AUTH_PUBLIC_URLS).permitAll()
//                .requestMatchers(AppConstants.AUTH_ADMIN_URLS).hasRole(AppConstants.ADMIN_ROLE)
//                .requestMatchers(AppConstants.AUTH_GUEST_URLS).hasRole(AppConstants.GUEST_ROLE)
                .anyRequest().authenticated()
            )

            // 5. Custom Filters
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
