package com.chauhan.aiservice.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class UserServiceTool {

    public record UserProfile(
            String userId,
            String name,
            String email,
            String role,
            String status,
            String registeredAt
    ) {}

    public record SystemUserStats(
            long totalUsers,
            long activeUsers,
            long adminUsers,
            String lastRegistrationTime
    ) {}

    private final Map<String, UserProfile> mockUserDatabase = Map.of(
            "user@example.com", new UserProfile("usr-101", "Jane Doe", "user@example.com", "ROLE_USER", "ACTIVE", "2026-01-15T10:30:00Z"),
            "admin@example.com", new UserProfile("usr-100", "Admin User", "admin@example.com", "ROLE_ADMIN", "ACTIVE", "2025-11-01T08:00:00Z"),
            "sourabh@example.com", new UserProfile("usr-102", "Sourabh Chauhan", "sourabh@example.com", "ROLE_ADMIN", "ACTIVE", "2026-02-20T14:15:00Z")
    );

    @Tool(name = "getUserDetailsByEmail", description = "Lookup and fetch detailed user profile information by email address.")
    public UserProfile getUserDetailsByEmail(
            @ToolParam(description = "The email address of the user to look up", required = true)
            String email
    ) {
        if (email == null || email.isBlank()) {
            return new UserProfile("N/A", "Unknown", "N/A", "NONE", "NOT_FOUND", "N/A");
        }
        return mockUserDatabase.getOrDefault(email.toLowerCase().trim(),
                new UserProfile("N/A", "Not Found", email, "NONE", "NOT_FOUND", "N/A"));
    }

    @Tool(name = "getSystemUserStatistics", description = "Get aggregate user metrics such as total registered users, active users, and admin count.")
    public SystemUserStats getSystemUserStatistics() {
        return new SystemUserStats(
                1250L,
                1180L,
                12L,
                Instant.now().toString()
        );
    }
}
