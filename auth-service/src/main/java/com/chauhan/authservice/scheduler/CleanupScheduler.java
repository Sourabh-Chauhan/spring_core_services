package com.chauhan.authservice.scheduler;

import com.chauhan.authservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {

    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.scheduling.audit-log-retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "0 0 0 * * ?") // Run every day at midnight
    @Transactional
    public void cleanupExpiredEntries() {
        log.info("Starting scheduled cleanup of expired tokens and old audit logs...");
        Instant now = Instant.now();

        try {
            // 1. Clean expired verification tokens
            verificationTokenRepository.deleteByExpiryDateBefore(now);
            log.info("Cleaned up expired verification tokens.");

            // 2. Clean expired password reset tokens
            passwordResetTokenRepository.deleteByExpiryDateBefore(now);
            log.info("Cleaned up expired password reset tokens.");

            // 3. Clean expired refresh tokens
            refreshTokenRepository.deleteByExpiresAtBefore(now);
            log.info("Cleaned up expired refresh tokens.");

            // 4. Clean audit logs older than N days
            Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
            auditLogRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up audit logs older than {} days.", retentionDays);

        } catch (Exception e) {
            log.error("Error occurred during scheduled cleanup: ", e);
        }
    }
}
