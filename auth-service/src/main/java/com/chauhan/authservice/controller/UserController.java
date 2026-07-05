package com.chauhan.authservice.controller;

import com.chauhan.authservice.dto.UserDto;

import com.chauhan.authservice.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RESPONSIBILITY:
 * Provides endpoints for administrative user management tasks (CRUD: Create, Read, Update, Delete).
 *
 * ISSUES / SECURITY CONCERNS:
 * 1. Missing Endpoint Authorization: Currently, role-based security configurations are commented out
 *    in SecurityConfig.java. This means any authenticated user (even without any roles) can call
 *    endpoints like GET /api/v1/users (retrieve all users) or DELETE /api/v1/users/{id}.
 * 2. Missing Input Validation: None of the input models are validated with @Valid.
 *
 * TODO:
 * - Uncomment role-based authorization rules in SecurityConfig.java.
 * - Secure individual endpoints using @PreAuthorize (e.g., @PreAuthorize("hasRole('ADMIN')")).
 * - Implement request body validation with @Valid.
 */
@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    // POST /api/v1/users - Create a new user
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        UserDto createdUser = userService.createUser(userDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    // GET /api/v1/users - Get all users
    @GetMapping
    public ResponseEntity<Iterable<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // GET /api/v1/users/{userId} - Get a single user by their ID
    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUserById(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    // GET /api/v1/users/email/{email} - Get a single user by their email
    @GetMapping("/email/{email}")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable("email") String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    // PUT /api/v1/users/{userId} - Update an existing user
    @PutMapping("/{userId}")
    public ResponseEntity<UserDto> updateUser(@RequestBody UserDto userDto, @PathVariable("userId") String userId) {
        UserDto updatedUser = userService.updateUser(userDto, userId);
        return ResponseEntity.ok(updatedUser);
    }

    // DELETE /api/v1/users/{userId} - Delete a user
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable("userId") String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build(); // Return HTTP 204 No Content on successful deletion
    }
}