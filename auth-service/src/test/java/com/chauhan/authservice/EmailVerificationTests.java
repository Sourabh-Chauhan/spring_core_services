package com.chauhan.authservice;

import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.entity.VerificationToken;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    private static final String REGISTER_EMAIL = "verify.test@example.com";
    private static final String REGISTER_PASSWORD = "Password123!";

    @BeforeEach
    void setUp() {
        // Clean up database records for the test email
        Optional<User> existingUser = userRepository.findByEmail(REGISTER_EMAIL);
        existingUser.ifPresent(user -> {
            tokenRepository.deleteByUser(user);
            userRepository.delete(user);
        });
    }

    @Test
    void testEmailVerificationFlow() throws Exception {
        // 1. Register a user
        String registerJson = "{\"email\":\"" + REGISTER_EMAIL + "\",\"name\":\"Verification Test User\",\"password\":\"" + REGISTER_PASSWORD + "\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(REGISTER_EMAIL))
                .andExpect(jsonPath("$.emailVerified").value(false));

        // Verify user is in DB but not verified
        User user = userRepository.findByEmail(REGISTER_EMAIL)
                .orElseThrow(() -> new AssertionError("User not saved in database"));
        assertFalse(user.isEmailVerified());

        // Verify verification token is created
        Optional<VerificationToken> tokenOpt = tokenRepository.findByUser(user);
        assertTrue(tokenOpt.isPresent());
        String tokenStr = tokenOpt.get().getToken();
        assertNotNull(tokenStr);

        // Verify mailSender was invoked
        verify(mailSender).send(any(SimpleMailMessage.class));

        // 2. Try logging in before verification (should fail with 403 Forbidden)
        String loginJson = "{\"email\":\"" + REGISTER_EMAIL + "\",\"password\":\"" + REGISTER_PASSWORD + "\"}";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Email Not Verified"));

        // 3. Verify email with the token
        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", tokenStr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));

        // Verify user is verified and token is deleted
        User verifiedUser = userRepository.findByEmail(REGISTER_EMAIL).orElseThrow();
        assertTrue(verifiedUser.isEmailVerified());
        assertFalse(tokenRepository.findByToken(tokenStr).isPresent());

        // 4. Log in successfully after verification
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.emailVerified").value(true));
    }

    @Test
    void testResendVerificationEmail() throws Exception {
        // Register user
        String registerJson = "{\"email\":\"" + REGISTER_EMAIL + "\",\"name\":\"Verification Test User\",\"password\":\"" + REGISTER_PASSWORD + "\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(REGISTER_EMAIL).orElseThrow();
        VerificationToken initialToken = tokenRepository.findByUser(user).orElseThrow();
        String initialTokenStr = initialToken.getToken();

        // Resend verification
        String resendJson = "{\"email\":\"" + REGISTER_EMAIL + "\"}";
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the email is registered, a new verification link has been sent."));

        // Verify token is regenerated/replaced
        Optional<VerificationToken> newTokenOpt = tokenRepository.findByUser(user);
        assertTrue(newTokenOpt.isPresent());
        assertNotEquals(initialTokenStr, newTokenOpt.get().getToken());
    }

    @Test
    void testDefaultRoleAssignedToNewUser() throws Exception {
        String registerJson = "{\"email\":\"" + REGISTER_EMAIL + "\",\"name\":\"Role Test User\",\"password\":\"" + REGISTER_PASSWORD + "\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(REGISTER_EMAIL)
                .orElseThrow(() -> new AssertionError("User not saved in database"));
        
        assertEquals(1, user.getRoles().size());
        assertEquals("ROLE_USER", user.getRoles().iterator().next().getName());
    }

    @Test
    void testPrivilegeEscalationPreventedOnRegistration() throws Exception {
        String registerJson = "{\"email\":\"" + REGISTER_EMAIL + "\",\"name\":\"Escalation Test User\",\"password\":\"" + REGISTER_PASSWORD + "\",\"roles\":[{\"name\":\"ROLE_ADMIN\"}]}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(REGISTER_EMAIL)
                .orElseThrow(() -> new AssertionError("User not saved in database"));

        assertEquals(1, user.getRoles().size());
        assertEquals("ROLE_USER", user.getRoles().iterator().next().getName());
    }
}
