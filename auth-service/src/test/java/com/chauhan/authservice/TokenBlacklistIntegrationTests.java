package com.chauhan.authservice;

import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.repository.VerificationTokenRepository;
import com.chauhan.authservice.service.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenBlacklistIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEST_EMAIL = "blacklist.test@example.com";
    private static final String TEST_PASSWORD = "Password123!";

    @BeforeEach
    void setUp() {
        Optional<User> existingUser = userRepository.findByEmail(TEST_EMAIL);
        existingUser.ifPresent(user -> {
            tokenRepository.deleteByUser(user);
            userRepository.delete(user);
        });
    }

    @Test
    void testTokenBlacklistOnLogout() throws Exception {
        // 1. Register a user
        String registerJson = "{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Blacklist Test User\",\"password\":\"" + TEST_PASSWORD + "\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        // 2. Automatically verify email to allow login
        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        // 3. Log in to retrieve access token
        String loginJson = "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + TEST_PASSWORD + "\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseContent, Map.class);
        String accessToken = (String) responseMap.get("accessToken");
        assertNotNull(accessToken);

        // 4. Access a protected endpoint (GET /api/v1/users) -> should succeed
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 5. Perform Logout to blacklist the access token
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 6. Verify that the token is now blacklisted in Redis
        assertTrue(tokenBlacklistService.isTokenBlacklisted(accessToken));

        // 7. Access the same protected endpoint -> should fail (Unauthorized)
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }
}
