package com.chauhan.batchservice.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record JobExecutionResponse(
        String jobName,
        Long jobExecutionId,
        Long jobInstanceId,
        String status,
        String exitCode,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Map<String, Object> parameters,
        String errorMessage
) {
    public static JobExecutionResponse success(
            String jobName,
            Long executionId,
            Long instanceId,
            String status,
            String exitCode,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Map<String, Object> parameters) {
        return new JobExecutionResponse(
                jobName,
                executionId,
                instanceId,
                status,
                exitCode,
                startTime,
                endTime,
                parameters,
                null
        );
    }

    public static JobExecutionResponse failure(String jobName, String errorMessage) {
        return new JobExecutionResponse(
                jobName,
                null,
                null,
                "FAILED",
                "FAILED",
                null,
                null,
                null,
                errorMessage
        );
    }
}
