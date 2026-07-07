package com.chauhan.authservice;

import com.chauhan.authservice.entity.*;
import com.chauhan.authservice.repository.*;
import com.chauhan.authservice.scheduler.CleanupScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CleanupSchedulerTests {

    @Autowired
    private CleanupScheduler cleanupScheduler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User testUser;

    private static final String TEST_EMAIL = "scheduler.test@example.com";

    @BeforeEach
    void setUp() {
        // Clean up database records
        Optional<User> existingUser = userRepository.findByEmail(TEST_EMAIL);
        existingUser.ifPresent(user -> {
            verificationTokenRepository.deleteByUser(user);
            passwordResetTokenRepository.deleteByUser(user);
            // Delete refresh tokens for the user
            refreshTokenRepository.deleteAll(refreshTokenRepository.findByUser_IdAndRevokedFalseAndExpiresAtAfter(user.getId(), Instant.MIN));
            userRepository.delete(user);
        });

        testUser = User.builder()
                .email(TEST_EMAIL)
                .name("Scheduler Test User")
                .password("Password123!")
                .enable(true)
                .emailVerified(true)
                .build();
        testUser = userRepository.save(testUser);

        verificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    void testCleanupSchedulerPurgesExpiredEntries() {
        Instant now = Instant.now();

        // 1. Verification Tokens
        // Expired verification token (1 hour in past)
        VerificationToken expiredVT = VerificationToken.builder()
                .token("expired-vt-token")
                .user(testUser)
                .expiryDate(now.minus(1, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.save(expiredVT);

        // Valid verification token (1 hour in future)
        VerificationToken validVT = VerificationToken.builder()
                .token("valid-vt-token")
                .user(testUser)
                .expiryDate(now.plus(1, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.save(validVT);

        // 2. Password Reset Tokens
        // Expired password reset token (1 hour in past)
        PasswordResetToken expiredPRT = PasswordResetToken.builder()
                .token("expired-prt-token")
                .user(testUser)
                .expiryDate(now.minus(1, ChronoUnit.HOURS))
                .build();
        passwordResetTokenRepository.save(expiredPRT);

        // Valid password reset token (1 hour in future)
        PasswordResetToken validPRT = PasswordResetToken.builder()
                .token("valid-prt-token")
                .user(testUser)
                .expiryDate(now.plus(1, ChronoUnit.HOURS))
                .build();
        passwordResetTokenRepository.save(validPRT);

        // 3. Refresh Tokens
        // Expired refresh token (1 hour in past)
        RefreshToken expiredRT = RefreshToken.builder()
                .jti("expired-rt-jti")
                .user(testUser)
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .expiresAt(now.minus(1, ChronoUnit.HOURS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(expiredRT);

        // Valid refresh token (1 hour in future)
        RefreshToken validRT = RefreshToken.builder()
                .jti("valid-rt-jti")
                .user(testUser)
                .createdAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(validRT);

        // 4. Audit Logs
        // Old audit log (31 days ago)
        AuditLog oldAudit = AuditLog.builder()
                .eventType("TEST_EVENT")
                .email("test@example.com")
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla")
                .details("Old log entry")
                .timestamp(now.minus(31, ChronoUnit.DAYS))
                .build();
        auditLogRepository.save(oldAudit);

        // Recent audit log (5 days ago)
        AuditLog recentAudit = AuditLog.builder()
                .eventType("TEST_EVENT")
                .email("test@example.com")
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla")
                .details("Recent log entry")
                .timestamp(now.minus(5, ChronoUnit.DAYS))
                .build();
        auditLogRepository.save(recentAudit);

        // Ensure everything is saved and in the database before running scheduler
        assertEquals(2, verificationTokenRepository.count());
        assertEquals(2, passwordResetTokenRepository.count());
        assertEquals(2, refreshTokenRepository.count());
        assertEquals(2, auditLogRepository.count());

        // Run the scheduler
        cleanupScheduler.cleanupExpiredEntries();

        // Assertions
        // 1. Verification Tokens
        assertEquals(1, verificationTokenRepository.count());
        assertTrue(verificationTokenRepository.findByToken("valid-vt-token").isPresent());
        assertFalse(verificationTokenRepository.findByToken("expired-vt-token").isPresent());

        // 2. Password Reset Tokens
        assertEquals(1, passwordResetTokenRepository.count());
        assertTrue(passwordResetTokenRepository.findByToken("valid-prt-token").isPresent());
        assertFalse(passwordResetTokenRepository.findByToken("expired-prt-token").isPresent());

        // 3. Refresh Tokens
        assertEquals(1, refreshTokenRepository.count());
        assertTrue(refreshTokenRepository.findByJti("valid-rt-jti").isPresent());
        assertFalse(refreshTokenRepository.findByJti("expired-rt-jti").isPresent());

        // 4. Audit Logs
        assertEquals(1, auditLogRepository.count());
        assertTrue(auditLogRepository.findAll().stream().anyMatch(log -> "Recent log entry".equals(log.getDetails())));
        assertFalse(auditLogRepository.findAll().stream().anyMatch(log -> "Old log entry".equals(log.getDetails())));
    }
}
