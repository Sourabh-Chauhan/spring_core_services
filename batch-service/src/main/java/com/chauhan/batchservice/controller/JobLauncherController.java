package com.chauhan.batchservice.controller;

import com.chauhan.batchservice.dto.JobExecutionResponse;
import com.chauhan.batchservice.dto.JobListResponse;
import com.chauhan.batchservice.service.JobExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobLauncherController {

    private final JobExecutionService jobExecutionService;

    public JobLauncherController(JobExecutionService jobExecutionService) {
        this.jobExecutionService = jobExecutionService;
    }

    /**
     * List all registered batch jobs in the application.
     * GET /api/jobs
     */
    @GetMapping
    public ResponseEntity<JobListResponse> listAvailableJobs() {
        return ResponseEntity.ok(jobExecutionService.getAvailableJobs());
    }

    /**
     * Trigger a batch job by name on demand.
     * POST /api/jobs/{jobName}?param1=val1&param2=val2
     */
    @PostMapping("/{jobName}")
    public ResponseEntity<JobExecutionResponse> launchJob(
            @PathVariable String jobName,
            @RequestParam(required = false) Map<String, String> queryParams) {

        JobExecutionResponse response = jobExecutionService.launchJob(jobName, queryParams);

        if (response.errorMessage() != null && response.jobExecutionId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
