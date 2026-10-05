package com.chauhan.batchservice.dto;

import java.util.List;

public record JobListResponse(
        int totalJobs,
        List<String> availableJobs
) {}
