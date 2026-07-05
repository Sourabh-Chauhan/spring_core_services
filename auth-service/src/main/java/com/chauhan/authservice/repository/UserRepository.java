package com.chauhan.authservice.repository;

import com.chauhan.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * RESPONSIBILITY:
 * Spring Data JPA repository for the {@link User} entity. It provides the database access layer
 * for querying user information (like loading by email or checking for existing email registrations)
 * which forms the foundation of the authentication flow.
 *
 * ISSUES / SECURITY CONCERNS:
 * - None.
 *
 * TODO:
 * - Implement soft-deletion query methods if soft-deletion is preferred over physical deletes in the future.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by their email address.
     * This is the primary method used by the {@link org.springframework.security.core.userdetails.UserDetailsService}
     * to load a user's details during the authentication process.
     *
     * @param email The email address to search for.
     * @return An {@link Optional} containing the user if found, or empty otherwise.
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a user with the given email address already exists.
     * This is an efficient query used during user registration to prevent duplicate emails.
     * It is more performant than fetching the entire User object.
     *
     * @param email The email address to check.
     * @return true if a user with the email exists, false otherwise.
     */
    boolean existsByEmail(String email);
}