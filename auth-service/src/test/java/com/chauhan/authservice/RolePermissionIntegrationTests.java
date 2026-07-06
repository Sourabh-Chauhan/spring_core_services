package com.chauhan.authservice;

import com.chauhan.authservice.entity.Role;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.repository.RoleRepository;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RolePermissionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEST_EMAIL = "rbac.test@example.com";
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
    void testRbacAccessControlFlow() throws Exception {
        // 1. Register a standard user
        String registerJson = "{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Rbac Test User\",\"password\":\"" + TEST_PASSWORD + "\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        // 2. Verify email
        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        // 3. Log in as standard user
        String loginJson = "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + TEST_PASSWORD + "\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseContent, Map.class);
        String userToken = (String) responseMap.get("accessToken");
        assertNotNull(userToken);

        // 4. Access Admin endpoint (GET /api/v1/admin/roles) -> should be Forbidden (403)
        mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 5. Promote user to ADMIN in database
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().id(UUID.randomUUID()).name("ROLE_ADMIN").build()));
        
        user.getRoles().add(adminRole);
        userRepository.save(user);

        // 6. Log in again as ADMIN user
        MvcResult adminLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String adminResponseContent = adminLoginResult.getResponse().getContentAsString();
        Map<?, ?> adminResponseMap = objectMapper.readValue(adminResponseContent, Map.class);
        String adminToken = (String) adminResponseMap.get("accessToken");
        assertNotNull(adminToken);

        // 7. Access Admin endpoint (GET /api/v1/admin/roles) as ADMIN -> should succeed (200 OK)
        mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 8. Create a new permission (POST /api/v1/admin/permissions) as ADMIN -> should succeed (201 Created)
        mockMvc.perform(post("/api/v1/admin/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("name", "read:financials"))
                .andExpect(status().isCreated());
    }
}
