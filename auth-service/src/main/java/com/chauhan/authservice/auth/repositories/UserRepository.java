package com.chauhan.authservice.auth.repositories;

import com.chauhan.authservice.auth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findbyEmail(String email);
    boolean existsByEmail(String email);
}
