package com.chauhan.aiservice.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolsTest {

    private SystemMetricsTool systemMetricsTool;
    private UserServiceTool userServiceTool;

    @BeforeEach
    void setUp() {
        systemMetricsTool = new SystemMetricsTool();
        userServiceTool = new UserServiceTool();
    }

    @Test
    void testGetSystemMetrics() {
        SystemMetricsTool.SystemMetrics metrics = systemMetricsTool.getSystemMetrics();

        assertNotNull(metrics);
        assertTrue(metrics.heapMemoryUsedMb() >= 0);
        assertTrue(metrics.heapMemoryMaxMb() > 0);
        assertTrue(metrics.activeThreads() > 0);
        assertTrue(metrics.availableProcessors() > 0);
        assertNotNull(metrics.uptimeFormatted());
        assertNotNull(metrics.osName());
    }

    @Test
    void testGetMicroserviceHealth() {
        SystemMetricsTool.MicroserviceStatus authStatus = systemMetricsTool.getMicroserviceHealth("auth-service");
        assertEquals("auth-service", authStatus.serviceName());
        assertEquals("UP", authStatus.status());
        assertEquals(8083, authStatus.port());

        SystemMetricsTool.MicroserviceStatus gatewayStatus = systemMetricsTool.getMicroserviceHealth("gateway");
        assertEquals("gateway-service", gatewayStatus.serviceName());
        assertEquals("UP", gatewayStatus.status());
        assertEquals(8080, gatewayStatus.port());

        SystemMetricsTool.MicroserviceStatus unknownStatus = systemMetricsTool.getMicroserviceHealth("unknown-service");
        assertEquals("unknown-service", unknownStatus.serviceName());
        assertEquals("UNKNOWN", unknownStatus.status());
    }

    @Test
    void testGetUserDetailsByEmail() {
        UserServiceTool.UserProfile user = userServiceTool.getUserDetailsByEmail("user@example.com");
        assertEquals("usr-101", user.userId());
        assertEquals("Jane Doe", user.name());
        assertEquals("user@example.com", user.email());
        assertEquals("ROLE_USER", user.role());
        assertEquals("ACTIVE", user.status());

        UserServiceTool.UserProfile notFound = userServiceTool.getUserDetailsByEmail("nonexistent@example.com");
        assertEquals("NOT_FOUND", notFound.status());
    }

    @Test
    void testGetSystemUserStatistics() {
        UserServiceTool.SystemUserStats stats = userServiceTool.getSystemUserStatistics();
        assertNotNull(stats);
        assertEquals(1250L, stats.totalUsers());
        assertEquals(1180L, stats.activeUsers());
        assertEquals(12L, stats.adminUsers());
        assertNotNull(stats.lastRegistrationTime());
    }
}
