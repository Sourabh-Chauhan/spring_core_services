package com.chauhan.authservice;

import com.chauhan.authservice.entity.Role;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilUnitTests {

    private JwtUtil jwtUtil;
    private static final String SECRET = "saiofalvkasfbehwqpofhjashofjaswfpojasfvbafophfifonfoshjfwefjjfslajfsahfoshffasnbashfsaiofalvkasfbehwqpofhjashofjaswfpojasfvbafophfifonfoshjfwefjjfslajfsahfoshffasnbashf";
    private static final long ACCESS_TTL = 3600; // 1 hour
    private static final long REFRESH_TTL = 7200; // 2 hours
    private static final String ISSUER = "test-issuer";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_TTL, REFRESH_TTL, ISSUER);
    }

    @Test
    void testConstructor_ThrowsExceptionOnShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> 
                new JwtUtil("short-secret", ACCESS_TTL, REFRESH_TTL, ISSUER)
        );
    }

    @Test
    void testGenerateAccessToken_ValidToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .roles(Set.of(Role.builder().name("ROLE_USER").build()))
                .build();
        String jti = UUID.randomUUID().toString();

        String token = jwtUtil.generateAccessToken(user, jti);

        assertNotNull(token);
        assertTrue(jwtUtil.isAccessToken(token));
        assertFalse(jwtUtil.isRefreshToken(token));
        assertEquals(user.getId(), jwtUtil.getUserId(token));
        assertEquals(user.getEmail(), jwtUtil.getEmail(token));
        assertEquals(jti, jwtUtil.getJti(token));
        assertEquals(List.of("ROLE_USER"), jwtUtil.getRoles(token));
    }

    @Test
    void testGenerateRefreshToken_ValidToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
        String jti = UUID.randomUUID().toString();

        String token = jwtUtil.generateRefreshToken(user, jti);

        assertNotNull(token);
        assertTrue(jwtUtil.isRefreshToken(token));
        assertFalse(jwtUtil.isAccessToken(token));
        assertEquals(user.getId(), jwtUtil.getUserId(token));
        assertEquals(jti, jwtUtil.getJti(token));
    }

    @Test
    void testValidateToken_Success() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
        String token = jwtUtil.generateAccessToken(user, UUID.randomUUID().toString());

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@example.com");

        assertTrue(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    void testValidateToken_Failure_MismatchEmail() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
        String token = jwtUtil.generateAccessToken(user, UUID.randomUUID().toString());

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("wrong@example.com");

        assertFalse(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    void testGetRemainingTtlSeconds_ValidToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
        String token = jwtUtil.generateAccessToken(user, UUID.randomUUID().toString());

        long ttl = jwtUtil.getRemainingTtlSeconds(token);
        assertTrue(ttl > 0 && ttl <= ACCESS_TTL);
    }
}
