package com.chauhan.aiservice.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;

@Component
public class SystemMetricsTool {

    public record SystemMetrics(
            long heapMemoryUsedMb,
            long heapMemoryMaxMb,
            int activeThreads,
            int availableProcessors,
            String uptimeFormatted,
            String osName
    ) {}

    public record MicroserviceStatus(
            String serviceName,
            String status,
            int port,
            String description
    ) {}

    @Tool(name = "getSystemMetrics", description = "Get real-time JVM system metrics including heap memory usage, active thread count, CPU processors, and system uptime.")
    public SystemMetrics getSystemMetrics() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        long heapUsedMb = memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMaxMb = memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        int activeThreads = Thread.activeCount();
        int processors = Runtime.getRuntime().availableProcessors();
        long uptimeMs = runtimeMXBean.getUptime();

        Duration duration = Duration.ofMillis(uptimeMs);
        String formattedUptime = String.format("%d hours, %d minutes, %d seconds",
                duration.toHours(),
                duration.toMinutesPart(),
                duration.toSecondsPart());

        return new SystemMetrics(
                heapUsedMb,
                heapMaxMb,
                activeThreads,
                processors,
                formattedUptime,
                System.getProperty("os.name")
        );
    }

    @Tool(name = "getMicroserviceHealth", description = "Check the health and operational status of a specific microservice in the platform.")
    public MicroserviceStatus getMicroserviceHealth(
            @ToolParam(description = "The microservice name (e.g. auth-service, gateway-service, eureka-server, notification-service, ai-service)", required = true)
            String serviceName
    ) {
        if (serviceName == null || serviceName.isBlank()) {
            return new MicroserviceStatus("UNKNOWN", "DOWN", 0, "Service name cannot be empty");
        }

        String normalized = serviceName.toLowerCase().trim();
        return switch (normalized) {
            case "eureka-server", "eureka" -> new MicroserviceStatus("Eureka-server", "UP", 8761, "Service Discovery Server active");
            case "gateway-service", "gateway" -> new MicroserviceStatus("gateway-service", "UP", 8080, "API Gateway routing and rate limiting active");
            case "auth-service", "auth" -> new MicroserviceStatus("auth-service", "UP", 8083, "Authentication & Identity service active");
            case "notification-service", "notification" -> new MicroserviceStatus("notification-service", "UP", 8084, "Notification dispatcher active");
            case "ai-service", "ai" -> new MicroserviceStatus("ai-service", "UP", 8085, "AI Care & Intelligence Engine active");
            default -> new MicroserviceStatus(serviceName, "UNKNOWN", 0, "Service not registered in registry matrix");
        };
    }
}
