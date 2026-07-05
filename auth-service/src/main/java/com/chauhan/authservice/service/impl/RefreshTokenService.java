package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.entity.RefreshToken;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.RefreshTokenRepository;
import com.chauhan.authservice.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * RESPONSIBILITY:
 * Manages the lifecycle of refresh tokens (creation, validation, rotation, and revocation).
 * Prevents unauthorized session access by using a cryptographically unique JTI (JWT ID) 
 * stored in the database for stateful validation of stateless refresh tokens.
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Incomplete Token Rotation Security: If a client attempts to reuse a revoked token (which could
 *    indicate a token theft breach), the service currently only throws an exception. A secure
 *    rotation implementation should revoke the entire token family (all active tokens for that user)
 *    to prevent further unauthorized access.
 *
 * TODO:
 * - Implement full token family revocation (automatic detection and response to token reuse/replay attacks).
 * - Periodically clean up expired refresh tokens from the database to prevent table bloat.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .jti(UUID.randomUUID().toString())
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(jwtUtil.getRefreshTtlSeconds()))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken validateAndRotateRefreshToken(String refreshTokenString) {
        if (!jwtUtil.isRefreshToken(refreshTokenString)) {
            throw new BadCredentialsException("Invalid Refresh Token Type");
        }

        String jti = jwtUtil.getJti(refreshTokenString);
        UUID userId = jwtUtil.getUserId(refreshTokenString);

        RefreshToken storedRefreshToken = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not recognized"));

        // 1. Detect Token Reuse (Replay Attack)
        if (storedRefreshToken.isRevoked()) {
            // Critical Breach Warning: A revoked token is being presented again!
            // Revoke all active tokens for this user to lock out the attacker.
            refreshTokenRepository.revokeAllUserTokens(userId);
            logger.warn("SECURITY ALERT: Reuse of revoked refresh token detected for user: {}. Revoked all user sessions.", userId);
            throw new BadCredentialsException("Suspicious activity detected. Session terminated.");
        }

        // 2. Check Expiry
        if (storedRefreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token has expired");
        }

//        if (storedRefreshToken.isRevoked() || storedRefreshToken.getExpiresAt().isBefore(Instant.now())) {
//            throw new BadCredentialsException("Refresh token expired or revoked");
//        }

        // 3. Check Owner Match
        if (!storedRefreshToken.getUser().getId().equals(userId)) {
            throw new BadCredentialsException("Refresh token does not belong to this user");
        }

        // 4. Safe Rotation: Revoke the current token and issue a new one
        storedRefreshToken.setRevoked(true);
        String newJti = UUID.randomUUID().toString();
        storedRefreshToken.setReplacedByToken(newJti);
        refreshTokenRepository.save(storedRefreshToken);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .jti(newJti)
                .user(storedRefreshToken.getUser())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(jwtUtil.getRefreshTtlSeconds()))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(newRefreshToken);
    }

    @Transactional
    public void revokeRefreshToken(String refreshTokenString) {
        try {
            if (jwtUtil.isRefreshToken(refreshTokenString)) {
                String jti = jwtUtil.getJti(refreshTokenString);
                refreshTokenRepository.findByJti(jti).ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
            }
        } catch (JwtException e) {
            logger.warn("Error processing refresh token during logout: {}", e.getMessage());
        }
    }
}