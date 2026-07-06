package com.chauhan.authservice.repository;

import com.chauhan.authservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * RESPONSIBILITY:
 * Spring Data JPA repository for the {@link RefreshToken} entity. Allows retrieving refresh tokens
 * by their JTI to enable token validation, revocation, and rotation.
 *
 * ISSUES / SECURITY CONCERNS:
 * - None.
 *
 * TODO:
 * - Define a custom query or scheduling mechanism to clean up expired or revoked tokens periodically.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByJti(String jti);

    // Add this query to invalidate all active tokens of a breached user
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    void revokeAllUserTokens(@Param("userId") UUID userId);

    java.util.List<RefreshToken> findByUser_IdAndRevokedFalseAndExpiresAtAfter(UUID userId, java.time.Instant now);
}