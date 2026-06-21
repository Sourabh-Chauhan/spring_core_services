package com.chauhan.authservice.auth.services.impl;

import com.chauhan.authservice.auth.entities.Provider;
import com.chauhan.authservice.auth.entities.User;
import com.chauhan.authservice.exceptions.ResourceNotFoundException;
import com.chauhan.authservice.exceptions.ResourceAlreadyExistsException;
import com.chauhan.authservice.auth.payload.UserDto;
import com.chauhan.authservice.auth.repositories.UserRepository;
import com.chauhan.authservice.auth.services.UserService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDto createUser(UserDto userDto) {
        if (userDto.getEmail() == null || userDto.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new ResourceAlreadyExistsException("A user with email '" + userDto.getEmail() + "' already exists.");
        }

        User user = modelMapper.map(userDto, User.class);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setProvider(userDto.getProvider() != null ? userDto.getProvider() : Provider.LOCAL);
        User savedUser = userRepository.save(user);
        return modelMapper.map(savedUser, UserDto.class);
    }

    @Override
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return modelMapper.map(user, UserDto.class);
    }

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
        }

        if (userDto.getProvider() != null) user.setProvider(userDto.getProvider());
        user.setEnable(userDto.isEnable());
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
}
