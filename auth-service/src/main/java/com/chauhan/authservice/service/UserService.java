package com.chauhan.authservice.service;

import com.chauhan.authservice.dto.UserDto;

/**
 * Service interface for managing users.
 *
 * This interface defines the contract for all user-related business logic, such as
 * creating, retrieving, updating, and deleting users. It acts as a boundary between the
 * web layer (controllers) and the data access layer (repositories).
 *
 * It uses Data Transfer Objects (DTOs) to ensure that the internal entity model is not
 * directly exposed to the API, which is a key principle of good API design.
 */
public interface UserService {
    /**
     * Creates a new user in the system.
     * @param userDto A DTO containing the details of the user to be created.
     * @return A DTO of the newly created user.
     */
    UserDto createUser(UserDto userDto);

    /**
     * Retrieves a user by their email address.
     * @param email The email of the user to retrieve.
     * @return A DTO of the found user.
     */
    UserDto getUserByEmail(String email);

    /**
     * Updates an existing user's information.
     * @param userDto A DTO containing the updated user details.
     * @param userId The ID of the user to update.
     * @return A DTO of the updated user.
     */
    UserDto updateUser(UserDto userDto, String userId);

    /**
     * Partially updates an existing user's information.
     * @param userId The ID of the user to update.
     * @param updates A map of the fields to update.
     * @return A DTO of the updated user.
     */
    UserDto patchUser(String userId, java.util.Map<String, Object> updates);

    /**
     * Deletes a user from the system.
     * @param userId The ID of the user to delete.
     */
    void deleteUser(String userId);

    /**
     * Retrieves a user by their unique ID.
     * @param userId The ID of the user to retrieve.
     * @return A DTO of the found user.
     */
    UserDto getUserById(String userId);

    /**
     * Retrieves a list of all users in the system.
     * @return An iterable collection of user DTOs.
     */
    Iterable<UserDto> getAllUsers();
}