package com.chauhan.authservice;

import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.repository.VerificationTokenRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEST_EMAIL = "session.test@example.com";
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
    void testSessionLifecycleFlow() throws Exception {
        // 1. Register a user
        String registerJson = "{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Session Test User\",\"password\":\"" + TEST_PASSWORD + "\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        // 2. Automatically verify email to allow login
        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        // 3. Log in twice to simulate two different devices/sessions
        String loginJson = "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + TEST_PASSWORD + "\"}";

        // Device 1: Chrome on Windows
        String userAgentChrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        MvcResult loginResult1 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("User-Agent", userAgentChrome)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        // Device 2: Mobile Safari on iOS (Current Device for subsequent requests)
        String userAgentSafari = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
        MvcResult loginResult2 = mockMvc.perform(post("/api/v1/auth/login")
                        .header("User-Agent", userAgentSafari)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        // Extract active access token for Device 2
        String responseContent2 = loginResult2.getResponse().getContentAsString();
        Map<?, ?> responseMap2 = objectMapper.readValue(responseContent2, Map.class);
        String accessToken2 = (String) responseMap2.get("accessToken");
        assertNotNull(accessToken2);

        // 4. View active sessions using Device 2's token
        MvcResult getSessionsResult = mockMvc.perform(get("/api/v1/sessions")
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andReturn();

        String sessionsJson = getSessionsResult.getResponse().getContentAsString();
        List<?> sessions = objectMapper.readValue(sessionsJson, List.class);

        // Assert 2 active sessions are returned
        assertEquals(2, sessions.size());

        // Extract session items
        Map<?, ?> session1 = (Map<?, ?>) sessions.get(0);
        Map<?, ?> session2 = (Map<?, ?>) sessions.get(1);

        // Assert Device details were correctly parsed
        assertTrue(
            session1.get("deviceInfo").toString().contains("Chrome on Windows") || 
            session2.get("deviceInfo").toString().contains("Chrome on Windows")
        );
        assertTrue(
            session1.get("deviceInfo").toString().contains("Safari on iOS") || 
            session2.get("deviceInfo").toString().contains("Safari on iOS")
        );

        // Determine current session vs other session IDs
        UUID otherSessionId = null;
        UUID currentSessionId = null;
        for (Object sObj : sessions) {
            Map<?, ?> s = (Map<?, ?>) sObj;
            if ((Boolean) s.get("currentSession")) {
                currentSessionId = UUID.fromString((String) s.get("sessionId"));
            } else {
                otherSessionId = UUID.fromString((String) s.get("sessionId"));
            }
        }

        assertNotNull(currentSessionId);
        assertNotNull(otherSessionId);

        // 5. Revoke the "other" session (Device 1)
        mockMvc.perform(delete("/api/v1/sessions/" + otherSessionId)
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isNoContent());

        // 6. Verify only the current session remains active
        MvcResult getSessionsResultAfterRevoke = mockMvc.perform(get("/api/v1/sessions")
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andReturn();

        List<?> sessionsAfterRevoke = objectMapper.readValue(getSessionsResultAfterRevoke.getResponse().getContentAsString(), List.class);
        assertEquals(1, sessionsAfterRevoke.size());
        Map<?, ?> remainingSession = (Map<?, ?>) sessionsAfterRevoke.getFirst();
        assertEquals(currentSessionId.toString(), remainingSession.get("sessionId").toString());
        assertTrue((Boolean) remainingSession.get("currentSession"));

        // 7. Test log out of all other sessions (revoking all except current)
        // Login again to create a new session
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("User-Agent", userAgentChrome)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk());

        // Verify we have 2 sessions again
        MvcResult preRevokeAllOtherResult = mockMvc.perform(get("/api/v1/sessions")
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andReturn();
        List<?> preRevokeAllOtherSessions = objectMapper.readValue(preRevokeAllOtherResult.getResponse().getContentAsString(), List.class);
        assertEquals(2, preRevokeAllOtherSessions.size());

        // Call "revoke other sessions" endpoint
        mockMvc.perform(delete("/api/v1/sessions/other")
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isNoContent());

        // Verify only 1 session (the current one) is left
        MvcResult postRevokeAllOtherResult = mockMvc.perform(get("/api/v1/sessions")
                        .header("Authorization", "Bearer " + accessToken2))
                .andExpect(status().isOk())
                .andReturn();
        List<?> postRevokeAllOtherSessions = objectMapper.readValue(postRevokeAllOtherResult.getResponse().getContentAsString(), List.class);
        assertEquals(1, postRevokeAllOtherSessions.size());
        Map<?, ?> finalSession = (Map<?, ?>) postRevokeAllOtherSessions.getFirst();
        assertEquals(currentSessionId.toString(), finalSession.get("sessionId").toString());
    }
}
