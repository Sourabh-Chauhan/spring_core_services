package com.chauhan.batchservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
class Module01CoreModelTests {

    private static final Logger log = LoggerFactory.getLogger(Module01CoreModelTests.class);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private Job helloWorldJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(helloWorldJob);
    }

    @Test
    @DisplayName("Module 1 Test: Execute Job and inspect PostgreSQL Metadata Tables")
    void testHelloWorldJobAndInspectMetadataTables() throws Exception {
        // Given: Distinct identifying parameter to guarantee a new JobInstance
        long timestamp = System.currentTimeMillis();
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("run.timestamp", timestamp)
                .toJobParameters();

        // When: Launch the job
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: Verify Execution Status
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        log.info("Job completed successfully with JobExecutionId: {}, JobInstanceId: {}",
                jobExecution.getId(), jobExecution.getJobInstance().getInstanceId());

        // Inspect 1: Query BATCH_JOB_INSTANCE table directly from PostgreSQL
        List<Map<String, Object>> instances = jdbcTemplate.queryForList(
                "SELECT job_instance_id, job_name, job_key FROM batch_job_instance WHERE job_instance_id = ?",
                jobExecution.getJobInstance().getInstanceId()
        );
        assertThat(instances).hasSize(1);
        log.info("DATABASE ROW from BATCH_JOB_INSTANCE: {}", instances.getFirst());
        assertThat(instances.getFirst().get("job_name")).isEqualTo("helloWorldJob");

        // Inspect 2: Query BATCH_JOB_EXECUTION table directly from PostgreSQL
        List<Map<String, Object>> executions = jdbcTemplate.queryForList(
                "SELECT job_execution_id, status, exit_code FROM batch_job_execution WHERE job_execution_id = ?",
                jobExecution.getId()
        );
        assertThat(executions).hasSize(1);
        log.info("DATABASE ROW from BATCH_JOB_EXECUTION: {}", executions.getFirst());
        assertThat(executions.getFirst().get("status")).isEqualTo("COMPLETED");

        // Inspect 3: Query BATCH_STEP_EXECUTION table directly from PostgreSQL
        List<Map<String, Object>> steps = jdbcTemplate.queryForList(
                "SELECT step_name, status, commit_count FROM batch_step_execution WHERE job_execution_id = ?",
                jobExecution.getId()
        );
        assertThat(steps).hasSize(1);
        log.info("DATABASE ROW from BATCH_STEP_EXECUTION: {}", steps.getFirst());
        assertThat(steps.getFirst().get("step_name")).isEqualTo("helloWorldStep");

        // Inspect 4: Query ExecutionContext from BATCH_JOB_EXECUTION_CONTEXT
        List<Map<String, Object>> jobContexts = jdbcTemplate.queryForList(
                "SELECT short_context FROM batch_job_execution_context WHERE job_execution_id = ?",
                jobExecution.getId()
        );
        assertThat(jobContexts).hasSize(1);
        log.info("DATABASE ROW from BATCH_JOB_EXECUTION_CONTEXT (Base64 serialized): {}", jobContexts.getFirst());

        // Verify deserialized ExecutionContext in memory from the JobExecution
        assertThat(jobExecution.getExecutionContext().getString("pipelineStatus")).isEqualTo("INITIALIZED");
        log.info("ExecutionContext value in memory: pipelineStatus={}",
                jobExecution.getExecutionContext().getString("pipelineStatus"));
    }
}
