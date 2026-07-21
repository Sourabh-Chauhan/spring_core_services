package com.chauhan.authservice;

import com.chauhan.authservice.entity.PasswordResetToken;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.PasswordResetTokenRepository;
import com.chauhan.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private static final String TEST_EMAIL = "reset.test@example.com";
    private static final String INITIAL_PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPassword123!";

    @BeforeEach
    void setUp() {
        // Clean up database records for the test email
        Optional<User> existingUser = userRepository.findByEmail(TEST_EMAIL);
        existingUser.ifPresent(user -> {
            resetTokenRepository.deleteByUser(user);
            userRepository.delete(user);
        });
    }

    @Test
    void testPasswordResetFlow() throws Exception {
        // 1. Register a test user
        String registerJson = "{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Reset Test User\",\"password\":\"" + INITIAL_PASSWORD + "\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        // 2. Initiate Forgot Password
        String forgotJson = "{\"email\":\"" + TEST_EMAIL + "\"}";
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the email is registered, a password reset link has been sent."));

        // 3. Verify token was saved in database
        User user = userRepository.findByEmail(TEST_EMAIL)
                .orElseThrow(() -> new AssertionError("User not found after registration"));
        PasswordResetToken resetToken = resetTokenRepository.findByUser(user)
                .orElseThrow(() -> new AssertionError("Password reset token not found in database"));
        
        assertNotNull(resetToken.getToken());
        assertFalse(resetToken.getToken().isBlank());

        // 4. Try to reset with invalid password (too short)
        String invalidResetJson = "{\"token\":\"" + resetToken.getToken() + "\",\"newPassword\":\"short\"}";
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidResetJson))
                .andExpect(status().isBadRequest());

        // 5. Reset with correct password
        String resetJson = "{\"token\":\"" + resetToken.getToken() + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}";
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully. You can now log in."));

        // 6. Verify token is deleted
        Optional<PasswordResetToken> deletedToken = resetTokenRepository.findByToken(resetToken.getToken());
        assertTrue(deletedToken.isEmpty());

        // 7. Verify user password is changed
        User updatedUser = userRepository.findByEmail(TEST_EMAIL)
                .orElseThrow(() -> new AssertionError("User not found after password reset"));
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, updatedUser.getPassword()));
        assertFalse(passwordEncoder.matches(INITIAL_PASSWORD, updatedUser.getPassword()));
    }
}
