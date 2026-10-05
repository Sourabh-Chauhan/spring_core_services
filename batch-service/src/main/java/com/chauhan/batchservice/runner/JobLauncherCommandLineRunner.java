package com.chauhan.batchservice.runner;

import com.chauhan.batchservice.dto.JobExecutionResponse;
import com.chauhan.batchservice.service.JobExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class JobLauncherCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JobLauncherCommandLineRunner.class);

    private final JobExecutionService jobExecutionService;

    public JobLauncherCommandLineRunner(JobExecutionService jobExecutionService) {
        this.jobExecutionService = jobExecutionService;
    }

    @Override
    public void run(String... args) {
        List<String> availableJobs = jobExecutionService.getAvailableJobNames();

        // Check if --job=<name> or job=<name> was passed in arguments
        String requestedJobName = Arrays.stream(args)
                .filter(arg -> arg.startsWith("--job=") || arg.startsWith("job="))
                .map(arg -> arg.substring(arg.indexOf('=') + 1).trim())
                .findFirst()
                .orElse(null);

        if (requestedJobName != null && !requestedJobName.isBlank()) {
            log.info("===============================================================");
            log.info(">>> Launching Job on Demand via CLI: '{}' <<<", requestedJobName);
            log.info("===============================================================");

            JobExecutionResponse response = jobExecutionService.launchJob(requestedJobName, Collections.emptyMap());

            if (response.errorMessage() != null) {
                log.error("Execution failed for job '{}': {}", requestedJobName, response.errorMessage());
            } else {
                log.info("Job '{}' finished with Status: {}, Exit Code: {}",
                        requestedJobName, response.status(), response.exitCode());
            }
        } else {
            log.info("===============================================================");
            log.info("BATCH SERVICE READY on Port 8086");
            log.info("Discovered Batch Jobs: {}", availableJobs);
            log.info("To trigger a job via CLI:");
            log.info("  ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"--job=<jobName>\"");
            log.info("To trigger a job via REST API:");
            log.info("  curl -X POST http://localhost:8086/api/jobs/<jobName>");
            log.info("===============================================================");
        }
    }
}
