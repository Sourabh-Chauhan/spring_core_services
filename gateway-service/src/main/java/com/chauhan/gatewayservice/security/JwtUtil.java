package com.chauhan.gatewayservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final String issuer;

    public JwtUtil(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer
    ) {
        if (secret == null || secret.length() < 64) {
            throw new IllegalArgumentException("JWT secret must be at least 64 characters long.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isAccessToken(String token) {
        try {
            return TYPE_ACCESS.equals(getAllClaimsFromToken(token).get(CLAIM_TYPE));
        } catch (Exception e) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        return UUID.fromString(getAllClaimsFromToken(token).getSubject());
    }

    public String getJti(String token) {
        return getAllClaimsFromToken(token).getId();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return (List<String>) getAllClaimsFromToken(token).get(CLAIM_ROLES);
    }

    public String getEmail(String token) {
        return (String) getAllClaimsFromToken(token).get(CLAIM_EMAIL);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            // Check if issuer matches
            if (claims.getIssuer() == null || !claims.getIssuer().equals(issuer)) {
                return false;
            }
            // Check if token is expired
            if (claims.getExpiration().before(new Date())) {
                return false;
            }
            // Check if it's an access token
            return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

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
