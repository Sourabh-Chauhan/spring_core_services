package com.chauhan.authservice.service;

import com.chauhan.authservice.entity.Provider;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.exceptions.ResourceNotFoundException;
import com.chauhan.authservice.exceptions.ResourceAlreadyExistsException;
import com.chauhan.authservice.dto.UserDto;
import com.chauhan.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link UserService} interface.
 *
 * This class contains the business logic for user management. It interacts with the
 * {@link UserRepository} to perform database operations and uses a {@link ModelMapper}
 * to convert between entities and DTOs.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

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
        
        if (userDto.getEnable() == null) {
            user.setEnable(true);
        } else {
            user.setEnable(userDto.getEnable());
        }

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

        if (userDto.getName() != null && !userDto.getName().trim().isEmpty()) {
            user.setName(userDto.getName());
        }

//        if (userDto.getEmail() != null && !userDto.getEmail().trim().isEmpty() && !user.getEmail().equals(userDto.getEmail())) {
//            if (userRepository.existsByEmail(userDto.getEmail())) {
//                throw new ResourceAlreadyExistsException("A user with email '" + userDto.getEmail() + "' already exists.");
//            }
//            user.setEmail(userDto.getEmail());
//        }

        if (userDto.getImage() != null && !userDto.getImage().trim().isEmpty()) {
            user.setImage(userDto.getImage());
        }

        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }

        if (userDto.getProvider() != null) user.setProvider(userDto.getProvider());
        
        if (userDto.getEnable() != null) {
             user.setEnable(userDto.getEnable());
        }

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
}