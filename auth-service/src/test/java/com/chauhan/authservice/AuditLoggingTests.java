package com.chauhan.authservice;

import com.chauhan.authservice.entity.AuditLog;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.AuditLogRepository;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.config.AppConstants;
import com.chauhan.authservice.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLoggingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private static final String TEST_EMAIL = "audit.test@example.com";
    private static final String TEST_PASSWORD = "Password123!";

    @BeforeEach
    void setUp() {
        // Clean up database records
        Optional<User> existingUser = userRepository.findByEmail(TEST_EMAIL);
        existingUser.ifPresent(user -> {
            verificationTokenRepository.deleteByUser(user);
            userRepository.delete(user);
        });
        auditLogRepository.deleteAll();
    }

    @Test
    void testAuditLogOnRegistrationAndLogin() throws Exception {
        // 1. Register a test user
        String registerJson = "{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Audit Test User\",\"password\":\"" + TEST_PASSWORD + "\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        // Wait a short moment for the async audit listener to process the event
        Thread.sleep(800);

        // Verify registration event is logged
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty(), "Audit log should contain the registration event");
        
        AuditLog regLog = logs.stream()
                .filter(log -> AppConstants.AUDIT_EVENT_REGISTRATION.equals(log.getEventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Registration log not found"));
        
        assertEquals(TEST_EMAIL, regLog.getEmail());
        assertEquals("User registered successfully", regLog.getDetails());
        assertNotNull(regLog.getIpAddress());
        assertNotNull(regLog.getUserAgent());

        // 2. Perform a failed login (email not verified)
        String loginJson = "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + TEST_PASSWORD + "\"}";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isForbidden()); // Email is not verified throws EmailNotVerifiedException (maps to 403 Forbidden)

        // Wait a short moment for the async audit listener to process the event
        Thread.sleep(800);

        logs = auditLogRepository.findAll();
        AuditLog failLog = logs.stream()
                .filter(log -> AppConstants.AUDIT_EVENT_LOGIN_FAILURE.equals(log.getEventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Failed login log not found"));

        assertEquals(TEST_EMAIL, failLog.getEmail());
        assertTrue(failLog.getDetails().contains("Email") || failLog.getDetails().contains("verified"));
    }
}
