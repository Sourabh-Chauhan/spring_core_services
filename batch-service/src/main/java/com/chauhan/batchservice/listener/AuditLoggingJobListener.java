package com.chauhan.batchservice.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * MODULE 5: JobExecutionListener
 * Demonstrates intercepting job lifecycle events before and after execution.
 */
@Component
public class AuditLoggingJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingJobListener.class);

    private long startTime;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        this.startTime = System.currentTimeMillis();
        log.info("=================================================================");
        log.info("[JobExecutionListener] BEFORE JOB: '{}', Execution ID: {}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId());
        log.info("=================================================================");

        jobExecution.getExecutionContext().put("jobAuditStartTimestamp", this.startTime);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long duration = System.currentTimeMillis() - this.startTime;
        jobExecution.getExecutionContext().put("jobAuditDurationMs", duration);

        log.info("=================================================================");
        log.info("[JobExecutionListener] AFTER JOB: '{}', Execution ID: {}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId());
        log.info("  Status    : {}", jobExecution.getStatus());
        log.info("  Exit Code : {}", jobExecution.getExitStatus().getExitCode());
        log.info("  Duration  : {} ms", duration);
        log.info("=================================================================");
    }
}
