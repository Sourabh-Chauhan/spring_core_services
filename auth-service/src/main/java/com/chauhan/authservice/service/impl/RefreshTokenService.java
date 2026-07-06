package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.dto.response.SessionResponse;
import com.chauhan.authservice.entity.RefreshToken;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.exceptions.ResourceNotFoundException;
import com.chauhan.authservice.repository.RefreshTokenRepository;
import com.chauhan.authservice.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RESPONSIBILITY:
 * Manages the lifecycle of refresh tokens (creation, validation, rotation, and revocation).
 * Prevents unauthorized session access by using a cryptographically unique JTI (JWT ID) 
 * stored in the database for stateful validation of stateless refresh tokens.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        return createRefreshToken(user, null, null);
    }

    @Transactional
    public RefreshToken createRefreshToken(User user, String ipAddress, String userAgent) {
        String deviceInfo = parseDeviceInfo(userAgent);
        RefreshToken refreshToken = RefreshToken.builder()
                .jti(UUID.randomUUID().toString())
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(jwtUtil.getRefreshTtlSeconds()))
                .revoked(false)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceInfo(deviceInfo)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken validateAndRotateRefreshToken(String refreshTokenString) {
        return validateAndRotateRefreshToken(refreshTokenString, null, null);
    }

    @Transactional
    public RefreshToken validateAndRotateRefreshToken(String refreshTokenString, String ipAddress, String userAgent) {
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

        // 3. Check Owner Match
        if (!storedRefreshToken.getUser().getId().equals(userId)) {
            throw new BadCredentialsException("Refresh token does not belong to this user");
        }

        // 4. Safe Rotation: Revoke the current token and issue a new one
        storedRefreshToken.setRevoked(true);
        String newJti = UUID.randomUUID().toString();
        storedRefreshToken.setReplacedByToken(newJti);
        refreshTokenRepository.save(storedRefreshToken);

        String deviceInfo = parseDeviceInfo(userAgent != null ? userAgent : storedRefreshToken.getUserAgent());
        RefreshToken newRefreshToken = RefreshToken.builder()
                .jti(newJti)
                .user(storedRefreshToken.getUser())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(jwtUtil.getRefreshTtlSeconds()))
                .revoked(false)
                .ipAddress(ipAddress != null ? ipAddress : storedRefreshToken.getIpAddress())
                .userAgent(userAgent != null ? userAgent : storedRefreshToken.getUserAgent())
                .deviceInfo(deviceInfo)
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

    public List<SessionResponse> getActiveSessions(UUID userId, String currentJti) {
        return refreshTokenRepository.findByUser_IdAndRevokedFalseAndExpiresAtAfter(userId, Instant.now())
                .stream()
                .map(rt -> SessionResponse.builder()
                        .sessionId(rt.getId())
                        .ipAddress(rt.getIpAddress() != null ? rt.getIpAddress() : "Unknown IP")
                        .deviceInfo(rt.getDeviceInfo() != null ? rt.getDeviceInfo() : "Unknown Device")
                        .createdAt(rt.getCreatedAt())
                        .expiresAt(rt.getExpiresAt())
                        .currentSession(rt.getJti().equals(currentJti))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeSession(UUID sessionId, UUID userId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!token.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You are not authorized to revoke this session");
        }

        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    @Transactional
    public void revokeAllSessionsExcept(UUID userId, String currentJti) {
        List<RefreshToken> activeTokens = refreshTokenRepository
                .findByUser_IdAndRevokedFalseAndExpiresAtAfter(userId, Instant.now());

        for (RefreshToken rt : activeTokens) {
            if (!rt.getJti().equals(currentJti)) {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            }
        }
    }

    private String parseDeviceInfo(String userAgentString) {
        if (userAgentString == null || userAgentString.isBlank()) {
            return "Unknown Device";
        }
        try {
            ua_parser.Parser uaParser = new ua_parser.Parser();
            ua_parser.Client c = uaParser.parse(userAgentString);
            return String.format("%s on %s", c.userAgent.family, c.os.family);
        } catch (Exception e) {
            logger.warn("Failed to parse User-Agent header: {}", e.getMessage());
            return "Unknown Browser/OS";
        }
    }
}