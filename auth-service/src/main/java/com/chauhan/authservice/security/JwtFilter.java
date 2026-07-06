package com.chauhan.authservice.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import com.chauhan.authservice.service.TokenBlacklistService;

/**
 * A filter that runs once per request to process the JWT for authentication,
 * using a guard clause style for improved readability.
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull  HttpServletResponse response, @NonNull  FilterChain filterChain) throws ServletException, IOException {

        logger.debug("Processing request to '{}'", request.getRequestURI());
        try {
        // Guard Clause 1: Check if a JWT is present
        String jwt = extractJwtFromRequest(request);
        if (jwt == null) {
            logger.debug("No JWT token found in request header. Continuing filter chain without authentication.");
            filterChain.doFilter(request, response);
            return;
        }

        logger.debug("JWT token found in request header.");

        // Guard Clause 2: Check if the user is already authenticated
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            logger.debug("SecurityContext already holds an authentication. Continuing filter chain.");
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtUtil.isAccessToken(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (tokenBlacklistService.isTokenBlacklisted(jwt)) {
            logger.debug("JWT token is blacklisted. Denying access.");
            request.setAttribute("jwt_error", "Token has been invalidated (logged out).");
            filterChain.doFilter(request, response);
            return;
        }


            // This is the main "happy path" logic
            UUID userId = jwtUtil.getUserId(jwt);
            logger.debug("Extracted User ID from JWT: {}", userId);
            String UserEmail = jwtUtil.getEmail(jwt);

//            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userId.toString());
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(UserEmail);
            logger.debug("Loaded UserDetails for username/id: {}", userDetails.getUsername());

            // Guard Clause 3: Validate the token against the user details
            if (!jwtUtil.validateToken(jwt, userDetails)) {
                logger.debug("JWT token validation failed for user: {}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            if (!userDetails.isEnabled()) {
                logger.debug("User is not Enables: {}", userDetails.isEnabled());
                filterChain.doFilter(request, response);
                return;
            }


            // If all guards are passed, set the authentication
            logger.debug("JWT token is valid. Setting security context for user: {}", userId);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // <<< THE CRITICAL DEBUG LOG FOR 403 ERRORS >>>
            logger.debug("Successfully authenticated user '{}' with authorities: {}. Security context is now set.",
                    userDetails.getUsername(), userDetails.getAuthorities());
            // <<< THE CRITICAL DEBUG LOG FOR 403 ERRORS >>>


        } catch (ExpiredJwtException ex) {
            logger.warn("JWT validation failed: Token is expired. Message: {}", ex.getMessage());
            // ADD THIS LINE
            request.setAttribute("jwt_error", "Access token has expired.");
        } catch (SignatureException ex) {
            logger.error("JWT validation failed: Token signature is invalid.", ex);
            // ADD THIS LINE
            request.setAttribute("jwt_error", "Invalid token signature.");
        } catch (MalformedJwtException ex) {
            logger.error("JWT validation failed: Token is malformed.", ex);
            // ADD THIS LINE
            request.setAttribute("jwt_error", "Token is malformed or structurally incorrect.");

        } catch (JwtException ex) {
            logger.error("An unexpected error occurred during JWT processing.", ex);
            // ADD THIS LINE
            request.setAttribute("jwt_error", "Invalid token.");
        } catch (Exception ex) {
            logger.error("An unexpected error occurred during JWT processing.", ex);
            throw new RuntimeException(ex);
        }


        // Continue the filter chain for all cases
        logger.debug("Continuing filter chain for request to '{}'", request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT from the "Authorization: Bearer token" header.
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return request.getRequestURI().startsWith("/api/v1/auth");
    }
}