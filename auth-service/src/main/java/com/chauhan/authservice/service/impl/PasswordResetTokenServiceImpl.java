package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.entity.PasswordResetToken;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.PasswordResetTokenRepository;
import com.chauhan.authservice.service.PasswordResetTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenServiceImpl implements PasswordResetTokenService {

    private final PasswordResetTokenRepository tokenRepository;

    @Value("${security.password-reset.token-ttl-seconds:900}")
    private long tokenTtlSeconds;

    @Override
    @Transactional
    public PasswordResetToken createTokenForUser(User user) {
        // Delete any existing token for this user first and flush to avoid unique constraint issues
        tokenRepository.deleteByUser(user);
        tokenRepository.flush();

        String token = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusSeconds(tokenTtlSeconds);

        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .build();

        return tokenRepository.save(passwordResetToken);
    }

    @Override
    public PasswordResetToken validateToken(String tokenString) {
        PasswordResetToken token = tokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Password reset token has expired");
        }

        return token;
    }

    @Override
    @Transactional
    public void deleteToken(PasswordResetToken token) {
        tokenRepository.delete(token);
    }

    @Override
    @Transactional
    public void deleteTokenByUser(User user) {
        tokenRepository.deleteByUser(user);
    }
}
