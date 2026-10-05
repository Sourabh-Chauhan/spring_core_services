package com.chauhan.batchservice.service;

import com.chauhan.batchservice.dto.JobExecutionResponse;
import com.chauhan.batchservice.dto.JobListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    public JobExecutionService(JobLauncher jobLauncher, ApplicationContext applicationContext) {
        this.jobLauncher = jobLauncher;
        this.applicationContext = applicationContext;
    }

    /**
     * Retrieve the list of all registered Spring Batch Job bean names.
     */
    public List<String> getAvailableJobNames() {
        return new ArrayList<>(applicationContext.getBeansOfType(Job.class).keySet());
    }

    /**
     * Wrap registered jobs into a JobListResponse DTO.
     */
    public JobListResponse getAvailableJobs() {
        List<String> jobNames = getAvailableJobNames();
        return new JobListResponse(jobNames.size(), jobNames);
    }

    /**
     * Execute a job by name on demand with given parameters.
     */
    public JobExecutionResponse launchJob(String jobName, Map<String, String> customParams) {
        Map<String, Job> availableJobs = applicationContext.getBeansOfType(Job.class);
        Job targetJob = availableJobs.get(jobName);

        if (targetJob == null) {
            String errorMsg = String.format("Job '%s' not found! Available jobs: %s", jobName, availableJobs.keySet());
            log.error(errorMsg);
            return JobExecutionResponse.failure(jobName, errorMsg);
        }

        try {
            JobParametersBuilder paramsBuilder = new JobParametersBuilder();
            // Default run timestamp to guarantee a unique JobExecution
            paramsBuilder.addLong("run.timestamp", System.currentTimeMillis());

            // Add custom parameters (non-identifying by default to allow reuse)
            if (customParams != null) {
                customParams.forEach((k, v) -> paramsBuilder.addString(k, v, false));
            }

            JobParameters parameters = paramsBuilder.toJobParameters();
            log.info("Starting Batch Job '{}' with parameters: {}", jobName, parameters);

            JobExecution execution = jobLauncher.run(targetJob, parameters);

            log.info("Job '{}' completed with Status: {}, Exit Code: {}",
                    jobName, execution.getStatus(), execution.getExitStatus().getExitCode());

            Map<String, Object> recordedParams = new LinkedHashMap<>();
            for (org.springframework.batch.core.job.parameters.JobParameter<?> p : parameters) {
                recordedParams.put(p.name(), p.value());
            }

            return JobExecutionResponse.success(
                    jobName,
                    execution.getId(),
                    execution.getJobInstance().getInstanceId(),
                    execution.getStatus().toString(),
                    execution.getExitStatus().getExitCode(),
                    execution.getStartTime(),
                    execution.getEndTime(),
                    recordedParams
            );
        } catch (Exception e) {
            log.error("Failed to execute Job '{}': {}", jobName, e.getMessage(), e);
            return JobExecutionResponse.failure(jobName, e.getMessage());
        }
    }
}
