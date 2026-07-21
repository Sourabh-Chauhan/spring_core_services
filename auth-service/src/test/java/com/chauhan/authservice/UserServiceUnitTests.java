package com.chauhan.authservice;

import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.entity.Provider;
import com.chauhan.authservice.entity.Role;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.exceptions.ResourceAlreadyExistsException;
import com.chauhan.authservice.exceptions.ResourceNotFoundException;
import com.chauhan.authservice.repository.RoleRepository;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private UserDto testUserDto;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("user@example.com")
                .name("Test User")
                .password("plain-password")
                .enable(true)
                .emailVerified(false)
                .provider(Provider.LOCAL)
                .roles(new HashSet<>())
                .build();

        testUserDto = UserDto.builder()
                .email("user@example.com")
                .name("Test User")
                .password("plain-password")
                .build();
    }

    @Test
    void testCreateUser_Success() {
        when(userRepository.existsByEmail(testUserDto.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(testUserDto.getPassword())).thenReturn("encoded-password");
        
        Role defaultRole = Role.builder().id(UUID.randomUUID()).name("ROLE_USER").build();
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(defaultRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });

        UserDto createdDto = userService.createUser(testUserDto);

        assertNotNull(createdDto);
        assertEquals(userId, createdDto.getId());
        assertEquals("user@example.com", createdDto.getEmail());
        assertFalse(createdDto.getEmailVerified());
        
        verify(passwordEncoder).encode("plain-password");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testCreateUser_EmailAlreadyExists() {
        when(userRepository.existsByEmail(testUserDto.getEmail())).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> userService.createUser(testUserDto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testGetUserById_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserDto foundDto = userService.getUserById(userId.toString());

        assertNotNull(foundDto);
        assertEquals(userId, foundDto.getId());
        assertEquals("user@example.com", foundDto.getEmail());
    }

    @Test
    void testGetUserById_NotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(userId.toString()));
    }

    @Test
    void testPatchUser_UpdatesNameAndExplicitNullImage() {
        testUser.setImage("existing_image.png");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", "Updated Patch Name");
        updates.put("image", null); // Set image to null explicitly

        UserDto updatedDto = userService.patchUser(userId.toString(), updates);

        assertNotNull(updatedDto);
        assertEquals("Updated Patch Name", updatedDto.getName());
        assertNull(updatedDto.getImage());
        
        verify(userRepository).save(testUser);
    }
}
