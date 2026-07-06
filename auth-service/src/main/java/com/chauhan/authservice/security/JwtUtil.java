package com.chauhan.authservice.security;

import com.chauhan.authservice.entity.Role;
import com.chauhan.authservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for handling JWT (JSON Web Token) operations such as
 * generation, parsing, and validation.
 */
@Component
@Getter
public class JwtUtil {

    // Constants for claim names and token types to avoid magic strings
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final String issuer;

    /**
     * Constructs the JwtUtil with dependencies injected from application properties.
     *
     * @param secret             The secret key for signing the JWT, must be at least 64 characters.
     * @param accessTtlSeconds   The time-to-live for access tokens in seconds.
     * @param refreshTtlSeconds  The time-to-live for refresh tokens in seconds.
     * @param issuer             The issuer of the JWT.
     */
    public JwtUtil(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-ttl-seconds}") long accessTtlSeconds,
            @Value("${security.jwt.refresh-ttl-seconds}") long refreshTtlSeconds,
            @Value("${security.jwt.issuer}") String issuer
    ) {
        if (secret == null || secret.length() < 64) {
            throw new IllegalArgumentException("JWT secret must be at least 64 characters long.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.issuer = issuer;
    }

    /**
     * Generates a new access token for the given user.
     *
     * @param user The user for whom the token is being generated.
     * @return A signed JWT access token as a String.
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles() == null ? List.of() :
                user.getRoles().stream().map(Role::getName).toList();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString()) // Using User ID as the subject
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .claims(Map.of(
                        CLAIM_EMAIL, user.getEmail(),
                        CLAIM_ROLES, roles,
                        CLAIM_TYPE, TYPE_ACCESS
                ))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Generates a new refresh token for the given user.
     *
     * @param user The user for whom the token is being generated.
     * @param jti  The JWT ID (jti) of the access token this refresh token is associated with.
     * @return A signed JWT refresh token as a String.
     */
    public String generateRefreshToken(User user, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(jti)
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTtlSeconds)))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Parses the token to extract all claims. This method implicitly verifies the token's
     * signature, expiration, and issuer. Throws JwtException if validation fails.
     *
     * @param token The JWT token string.
     * @return The claims body of the token.
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * Checks if the given token is an access token.
     *
     * @param token The JWT token.
     * @return True if the token is an access token, false otherwise.
     */
    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(getAllClaimsFromToken(token).get(CLAIM_TYPE));
    }

    /**
     * Checks if the given token is a refresh token.
     *
     * @param token The JWT token.
     * @return True if the token is a refresh token, false otherwise.
     */
    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(getAllClaimsFromToken(token).get(CLAIM_TYPE));
    }

    /**
     * Extracts the user ID (from the subject claim) from the token.
     *
     * @param token The JWT token.
     * @return The user's UUID.
     */
    public UUID getUserId(String token) {
        return UUID.fromString(getAllClaimsFromToken(token).getSubject());
    }

    /**
     * Extracts the JWT ID (jti) from the token.
     *
     * @param token The JWT token.
     * @return The JWT ID as a String.
     */
    public String getJti(String token) {
        return getAllClaimsFromToken(token).getId();
    }

    /**
     * Extracts the user roles from the token.
     *
     * @param token The JWT token.
     * @return A list of role names.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return (List<String>) getAllClaimsFromToken(token).get(CLAIM_ROLES);
    }

    /**
     * Extracts the user email from the token.
     *
     * @param token The JWT token.
     * @return The user's email address.
     */
    public String getEmail(String token) {
        return (String) getAllClaimsFromToken(token).get(CLAIM_EMAIL);
    }

    /**
     * Validates a token by verifying its signature and expiration, and then checking
     * if the email from the token matches the one from the UserDetails object.
     *
     * @param token       The JWT token to validate.
     * @param userDetails The UserDetails object representing the user.
     * @return True if the token is valid for the given user, false otherwise.
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            // The parsing itself validates the signature and expiration.
            final String emailFromToken = getEmail(token);

            // We check if the email in the token matches the one in the UserDetails.
            // userDetails.getUsername() is configured to return the user's email.
            return emailFromToken.equals(userDetails.getUsername());

        } catch (JwtException | IllegalArgumentException e) {
            // Any exception during parsing means the token is invalid (expired, tampered, etc.)
            return false;
        }
    }

    /**
     * Calculates the remaining time-to-live (TTL) of the token in seconds.
     *
     * @param token The JWT token string.
     * @return The remaining TTL in seconds, or 0 if expired/invalid.
     */
    public long getRemainingTtlSeconds(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            if (expiration == null) {
                return 0;
            }
            long remainingMillis = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remainingMillis / 1000);
        } catch (Exception e) {
            return 0;
        }
    }
}