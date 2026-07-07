package com.chauhan.authservice;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.repository.VerificationTokenRepository;
import com.chauhan.authservice.security.JwtUtil;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserPatchIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEST_EMAIL = "patch.test@example.com";
    private static final String TEST_PASSWORD = "Password123!";

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up database records
        Optional<User> existingUser = userRepository.findByEmail(TEST_EMAIL);
        existingUser.ifPresent(user -> {
            verificationTokenRepository.deleteByUser(user);
            userRepository.delete(user);
        });

        // Register a test user
        String registerJson = "{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Patch Test User\",\"password\":\"" + TEST_PASSWORD + "\"}";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn();

        testUser = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        testUser.setEmailVerified(true);
        testUser.setImage("initial_image_url.png");
        testUser = userRepository.save(testUser);

        accessToken = jwtUtil.generateAccessToken(testUser, UUID.randomUUID().toString());
    }

    @Test
    void testPartialUpdateUser_UpdatesNameAndImage() throws Exception {
        // 1. Partially update name and image (non-null)
        Map<String, Object> updates = Map.of(
                "name", "Updated Name",
                "image", "new_image_url.png"
        );

        mockMvc.perform(patch("/api/v1/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk());

        // Verify database state
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals("Updated Name", updatedUser.getName());
        assertEquals("new_image_url.png", updatedUser.getImage());
    }

    @Test
    void testPartialUpdateUser_SetImageToNullExplicitly() throws Exception {
        // Create updates map with an explicit null for 'image'
        // Using a HashMap because Map.of doesn't allow null values
        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("image", null);
        updates.put("name", "Updated Name With Null Image");

        mockMvc.perform(patch("/api/v1/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk());

        // Verify database state: image should be null
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals("Updated Name With Null Image", updatedUser.getName());
        assertNull(updatedUser.getImage(), "Image should be set to null explicitly");
    }

    @Test
    void testPartialUpdateUser_NotChangingImage() throws Exception {
        // 3. Update name only, omitting 'image' from the map
        Map<String, Object> updates = Map.of(
                "name", "Only Name Updated"
        );

        mockMvc.perform(patch("/api/v1/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk());

        // Verify database state: image should remain unchanged
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals("Only Name Updated", updatedUser.getName());
        assertEquals("initial_image_url.png", updatedUser.getImage());
    }
}
