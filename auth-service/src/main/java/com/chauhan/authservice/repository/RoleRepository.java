package com.chauhan.authservice.repository;

import com.chauhan.authservice.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


/**
 * RESPONSIBILITY:
 * Spring Data JPA repository for the {@link Role} entity. Allows retrieving role metadata by role name.
 *
 * ISSUES / SECURITY CONCERNS:
 * - None.
 *
 * TODO:
 * - Define a pre-population mechanism (like a schema/data SQL import) to guarantee standard roles (e.g. ROLE_USER, ROLE_ADMIN) exist upon startup.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);
}