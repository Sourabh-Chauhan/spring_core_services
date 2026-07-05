package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.entity.VerificationToken;
import com.chauhan.authservice.repository.VerificationTokenRepository;
import com.chauhan.authservice.service.VerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationTokenServiceImpl implements VerificationTokenService {

    private final VerificationTokenRepository tokenRepository;

    @Value("${security.verification.token-ttl-seconds:86400}")
    private long tokenTtlSeconds;

    @Override
    @Transactional
    public VerificationToken createTokenForUser(User user) {
        // Delete any existing token for this user first
        tokenRepository.deleteByUser(user);
        tokenRepository.flush();

        String token = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusSeconds(tokenTtlSeconds);

        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .build();

        return tokenRepository.save(verificationToken);
    }

    @Override
    public VerificationToken validateToken(String tokenString) {
        VerificationToken token = tokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        return token;
    }

    @Override
    @Transactional
    public void deleteToken(VerificationToken token) {
        tokenRepository.delete(token);
    }

    @Override
    @Transactional
    public void deleteTokenByUser(User user) {
        tokenRepository.deleteByUser(user);
    }
}
