package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.config.AppConstants;
import com.chauhan.authservice.entity.Provider;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.entity.Role;
import com.chauhan.authservice.exceptions.ResourceNotFoundException;
import com.chauhan.authservice.exceptions.ResourceAlreadyExistsException;
import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.repository.RoleRepository;
import com.chauhan.authservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.chauhan.authservice.event.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RESPONSIBILITY:
 * Implements user management business logic (CRUD operations). It handles password encoding,
 * database interactions via UserRepository, and mapping between User entity and UserDto.
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Privilege Escalation: In `createUser()`, the user entity is mapped directly from UserDto. If a client
 *    passes roles in the DTO, they are saved directly, allowing users to register themselves as ADMIN.
 * 2. Unsecure Password Update: In `updateUser()`, user password is updated directly from the input DTO
 *    without verifying the user's current password.
 * 3. General Update Design Flaw: Null fields in `updateUser()` are skipped, which prevents explicitly setting 
 *    fields to null.
 * 4. Commented-out Email Update: The logic to update emails is commented out and needs proper validation / re-verification.
 *
 * TODO:
 * - Modify `createUser()` to explicitly assign a default role (e.g. "ROLE_USER") and strip external roles.
 * - Require old password verification when changing password.
 * - Refactor update logic using PATCH mapping and Map or distinct DTOs to distinguish null fields.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates a new user. This method includes validation, password hashing, and setting default values.
     * This is a secure and robust implementation for user creation.
     */
    @Override
    public UserDto createUser(UserDto userDto) {
        if (userDto.getEmail() == null || userDto.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new ResourceAlreadyExistsException("A user with email '" + userDto.getEmail() + "' already exists.");
        }

        User user = modelMapper.map(userDto, User.class);
        // Always hash the password before saving it to the database.
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setProvider(userDto.getProvider() != null ? userDto.getProvider() : Provider.LOCAL);
        
        // Handle the nullable Boolean for 'enable'. Default to true if not provided in the request.
        if (userDto.getEnable() == null) {
            user.setEnable(true);
        } else {
            user.setEnable(userDto.getEnable());
        }
        
        user.setEmailVerified(false);

        // Strip any external/provided roles to prevent privilege escalation
        user.setRoles(new java.util.HashSet<>());

        // Assign the default role
        Role defaultRole = roleRepository.findByName("ROLE_" + AppConstants.USER_ROLE)
                .orElseGet(() -> roleRepository.save(Role.builder().id(UUID.randomUUID()).name("ROLE_" + AppConstants.USER_ROLE).build()));

        user.getRoles().add(defaultRole);

        User savedUser = userRepository.save(user);
        return modelMapper.map(savedUser, UserDto.class);
    }

    @Override
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return modelMapper.map(user, UserDto.class);
    }

    /**
     * Updates an existing user.
     *
     * // TODO: [DESIGN FLAW] This method's logic is problematic for a general-purpose update.
     * // It only updates non-null fields, which makes it impossible to explicitly set a field (like 'image') to null.
     * // A better approach is to use a PATCH method with a DTO that uses Optional fields to distinguish
     * // between "not provided" and "explicitly set to null".
     *
     * // TODO: [SECURITY] This method allows password changes without verifying the user's current password.
     * // This is a security risk if the endpoint is not strictly limited to admins. Password changes should
     * // be handled by a separate, dedicated endpoint (e.g., /users/me/change-password) that requires
     * // the current password.
     *
     * // TODO: [INCOMPLETE] The logic for updating a user's email is commented out. If enabled, this
     * // action should trigger a re-verification process for the new email address to ensure the user owns it.
     */
    @Override
    public UserDto updateUser(UserDto userDto, String userId) {
        if (userDto == null) {
            throw new IllegalArgumentException("User data for update cannot be null");
        }

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format. It must be a valid UUID.");
        }

        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 1. Update Name (Only if provided and not empty)
        if (userDto.getName() != null && !userDto.getName().trim().isEmpty()) {
            user.setName(userDto.getName());
        }

        // 2. Update Email (Only if provided, not empty, and different from current)
//        if (userDto.getEmail() != null && !userDto.getEmail().trim().isEmpty() && !user.getEmail().equals(userDto.getEmail())) {
//            // Check if the new email is already taken by another user
//            if (userRepository.existsByEmail(userDto.getEmail())) {
//                throw new ResourceAlreadyExistsException("A user with email '" + userDto.getEmail() + "' already exists.");
//            }
//            user.setEmail(userDto.getEmail());
//        }

        // 3. Update Image (Only if provided)
        if (userDto.getImage() != null && !userDto.getImage().trim().isEmpty()) {
            user.setImage(userDto.getImage());
        }

        // 4. Update Password (Only if provided and not empty)
        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
            
            // Publish PASSWORD_CHANGE event
            String[] clientInfo = getRequestIpAndUserAgent();
            eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_PASSWORD_CHANGE, user.getEmail(), clientInfo[0], clientInfo[1], "Password updated via user profile update"));
        }

        if (userDto.getProvider() != null) user.setProvider(userDto.getProvider());
        
        // Handle the nullable Boolean for 'enable' on update.
        if (userDto.getEnable() != null) {
             user.setEnable(userDto.getEnable());
        }
        if (userDto.getEmailVerified() != null) {
            user.setEmailVerified(userDto.getEmailVerified());
        }
//        user.setUpdatedAt(Instant.now());

        User updatedUser = userRepository.save(user);
        return modelMapper.map(updatedUser, UserDto.class);
    }

    @Override
    public void deleteUser(String userId) {
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format. It must be a valid UUID.");
        }

        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        userRepository.delete(user);
    }

    @Override
    public UserDto getUserById(String userId) {
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format. It must be a valid UUID.");
        }

        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return modelMapper.map(user, UserDto.class);
    }

    @Override
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> modelMapper.map(user, UserDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public UserDto patchUser(String userId, java.util.Map<String, Object> updates) {
        if (updates == null) {
            throw new IllegalArgumentException("Updates map cannot be null");
        }

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format. It must be a valid UUID.");
        }

        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (updates.containsKey("name")) {
            String name = (String) updates.get("name");
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Name cannot be null or empty");
            }
            user.setName(name);
        }

        if (updates.containsKey("image")) {
            String image = (String) updates.get("image");
            user.setImage(image); // Can be set to null explicitly
        }

        if (updates.containsKey("password")) {
            String password = (String) updates.get("password");
            if (password == null || password.trim().isEmpty()) {
                throw new IllegalArgumentException("Password cannot be null or empty");
            }
            user.setPassword(passwordEncoder.encode(password));
            
            // Publish PASSWORD_CHANGE event
            String[] clientInfo = getRequestIpAndUserAgent();
            eventPublisher.publishEvent(new AuditEvent(this, AppConstants.AUDIT_EVENT_PASSWORD_CHANGE, user.getEmail(), clientInfo[0], clientInfo[1], "Password updated via PATCH request"));
        }

        if (updates.containsKey("enable")) {
            Object enableObj = updates.get("enable");
            if (enableObj instanceof Boolean enableVal) {
                user.setEnable(enableVal);
            } else if (enableObj != null) {
                user.setEnable(Boolean.parseBoolean(enableObj.toString()));
            }
        }

        if (updates.containsKey("emailVerified")) {
            Object emailVerifiedObj = updates.get("emailVerified");
            if (emailVerifiedObj instanceof Boolean emailVerifiedVal) {
                user.setEmailVerified(emailVerifiedVal);
            } else if (emailVerifiedObj != null) {
                user.setEmailVerified(Boolean.parseBoolean(emailVerifiedObj.toString()));
            }
        }

        User updatedUser = userRepository.save(user);
        return modelMapper.map(updatedUser, UserDto.class);
    }

    private String[] getRequestIpAndUserAgent() {
        String ipAddress = "UNKNOWN";
        String userAgent = "UNKNOWN";
        try {
            org.springframework.web.context.request.RequestAttributes attributes = 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = servletRequestAttributes.getRequest();
                ipAddress = request.getRemoteAddr();
                userAgent = request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            // Fallback
        }
        return new String[]{ipAddress, userAgent};
    }
}