package com.chauhan.authservice;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.dto.request.LoginRequest;
import com.chauhan.authservice.dto.response.TokenResponse;
import com.chauhan.authservice.entity.RefreshToken;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.entity.VerificationToken;
import com.chauhan.authservice.exceptions.EmailNotVerifiedException;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.security.JwtUtil;
import com.chauhan.authservice.service.EmailService;
import com.chauhan.authservice.service.PasswordResetTokenService;
import com.chauhan.authservice.service.TokenBlacklistService;
import com.chauhan.authservice.service.UserService;
import com.chauhan.authservice.service.VerificationTokenService;
import com.chauhan.authservice.service.impl.AuthServiceImpl;
import com.chauhan.authservice.service.impl.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTests {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenService tokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Spy
    private ModelMapper mapper = new ModelMapper();

    @Mock
    private PasswordResetTokenService passwordResetTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private UserDto testUserDto;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("user@example.com")
                .name("Auth Test User")
                .enable(true)
                .emailVerified(true)
                .build();

        testUserDto = UserDto.builder()
                .id(userId)
                .email("user@example.com")
                .name("Auth Test User")
                .build();
    }

    @Test
    void testRegisterUser_Success() {
        when(userService.createUser(any(UserDto.class))).thenReturn(testUserDto);
        when(userRepository.findByEmail(testUserDto.getEmail())).thenReturn(Optional.of(testUser));
        
        VerificationToken token = VerificationToken.builder()
                .token("verification-token")
                .user(testUser)
                .build();
        when(tokenService.createTokenForUser(testUser)).thenReturn(token);

        UserDto registeredDto = authService.registerUser(testUserDto);

        assertNotNull(registeredDto);
        assertEquals("user@example.com", registeredDto.getEmail());
        
        verify(emailService).sendVerificationEmail(testUser.getEmail(), "verification-token");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void testLogin_Success() {
        LoginRequest loginRequest = new LoginRequest("user@example.com", "password");
        
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(testUser);
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        RefreshToken refreshToken = RefreshToken.builder()
                .jti("test-jti")
                .user(testUser)
                .build();
        when(refreshTokenService.createRefreshToken(eq(testUser), any(), any())).thenReturn(refreshToken);
        
        when(jwtUtil.generateAccessToken(testUser, "test-jti")).thenReturn("mock-access-token");
        when(jwtUtil.generateRefreshToken(testUser, "test-jti")).thenReturn("mock-refresh-token");
        when(jwtUtil.getAccessTtlSeconds()).thenReturn(3600L);

        TokenResponse tokenResponse = authService.login(loginRequest, "127.0.0.1", "Mozilla");

        assertNotNull(tokenResponse);
        assertEquals("mock-access-token", tokenResponse.accessToken());
        assertEquals("mock-refresh-token", tokenResponse.refreshToken());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void testLogin_EmailNotVerified_ThrowsException() {
        LoginRequest loginRequest = new LoginRequest("user@example.com", "password");
        testUser.setEmailVerified(false); // Make unverified
        
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(testUser);
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        assertThrows(EmailNotVerifiedException.class, () -> 
                authService.login(loginRequest, "127.0.0.1", "Mozilla")
        );
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void testLogin_BadCredentials_ThrowsException() {
        LoginRequest loginRequest = new LoginRequest("user@example.com", "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> 
                authService.login(loginRequest, "127.0.0.1", "Mozilla")
        );
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void testVerifyEmail_Success() {
        testUser.setEmailVerified(false);
        VerificationToken token = VerificationToken.builder()
                .token("verification-token")
                .user(testUser)
                .build();
        
        when(tokenService.validateToken("verification-token")).thenReturn(token);

        authService.verifyEmail("verification-token");

        assertTrue(testUser.isEmailVerified());
        verify(userRepository).save(testUser);
        verify(tokenService).deleteToken(token);
    }
}
