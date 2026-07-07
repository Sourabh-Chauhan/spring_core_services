package com.chauhan.authservice.repository;

import com.chauhan.authservice.entity.PasswordResetToken;
import com.chauhan.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    
    Optional<PasswordResetToken> findByToken(String token);
    
    Optional<PasswordResetToken> findByUser(User user);
    
    void deleteByUser(User user);

    @org.springframework.data.jpa.repository.Modifying
    void deleteByExpiryDateBefore(java.time.Instant now);
}
