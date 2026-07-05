package com.chauhan.authservice.repository;

import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    
    Optional<VerificationToken> findByToken(String token);
    
    Optional<VerificationToken> findByUser(User user);
    
    void deleteByUser(User user);
}
