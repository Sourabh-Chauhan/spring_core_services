package com.chauhan.batchservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class Module04JobParametersAndRestartTests {

    private static final Logger log = LoggerFactory.getLogger(Module04JobParametersAndRestartTests.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("restartableTransactionJob")
    private Job restartableTransactionJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE processed_transactions");
    }

    @Test
    @DisplayName("Exercise 1: JobParametersValidator rejects execution if required parameter 'run.date' is missing")
    void testParameterValidation() {
        // Build parameters without the mandatory 'run.date' using unique operator tag
        JobParameters invalidParams = new JobParametersBuilder()
                .addString("operator.name", "sourabh-" + UUID.randomUUID())
                .toJobParameters();

        assertThatThrownBy(() -> jobLauncher.run(restartableTransactionJob, invalidParams))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("The JobParameters do not contain required keys: [run.date]");
    }

    @Test
    @DisplayName("Exercise 2 & 3: Mid-stream chunk failure, ItemStream offset checkpoint, and seamless resume without re-reading")
    void testMidStreamFailureAndRestartability() throws Exception {
        String uniqueBatchDate = "2026-10-05-" + UUID.randomUUID();

        // -----------------------------------------------------------------------------------------
        // RUN 1: Execute with failure injected at transaction #6
        // -----------------------------------------------------------------------------------------
        JobParameters run1Params = new JobParametersBuilder()
                .addString("run.date", uniqueBatchDate, true) // Identifying parameter
                .addLong("fail.at.transaction.id", 6L, false)  // Non-identifying failure trigger
                .toJobParameters();

        log.info(">>> Launching Run 1 (Simulating Failure at ID 6)...");
        JobExecution run1Execution = jobLauncher.run(restartableTransactionJob, run1Params);

        assertThat(run1Execution.getStatus().toString()).isEqualTo("FAILED");
        assertThat(run1Execution.getExitStatus().getExitCode()).isEqualTo("FAILED");

        Long instanceId = run1Execution.getJobInstance().getInstanceId();

        // Verify that Chunks 1 and 2 (IDs 1, 2, 3, 4) WERE COMMITTED, but Chunk 3 (IDs 5, 6) ROLLED BACK
        Integer processedCountAfterFailure = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_transactions", Integer.class);
        assertThat(processedCountAfterFailure).isEqualTo(4);

        List<Long> committedIds = jdbcTemplate.queryForList(
                "SELECT transaction_id FROM processed_transactions ORDER BY transaction_id ASC", Long.class);
        assertThat(committedIds).containsExactly(1L, 2L, 3L, 4L);
        log.info("PostgreSQL processed_transactions after failure contains: {}", committedIds);

        // Inspect StepExecution ExecutionContext directly:
        // ItemStream checkpointed 'transactionItemReader.read.count' = 4
        var failedStepExecution = run1Execution.getStepExecutions().iterator().next();
        int savedReadCount = failedStepExecution.getExecutionContext().getInt("transactionItemReader.read.count");
        log.info("StepExecution ExecutionContext restored read.count: {}", savedReadCount);
        assertThat(savedReadCount).isEqualTo(4);

        // -----------------------------------------------------------------------------------------
        // RUN 2: RESTART with the EXACT SAME identifying parameter (same JobInstance), but fix error!
        // -----------------------------------------------------------------------------------------
        JobParameters run2Params = new JobParametersBuilder()
                .addString("run.date", uniqueBatchDate, true) // EXACT SAME identifying parameter
                .addLong("fail.at.transaction.id", -1L, false) // Error cleared!
                .toJobParameters();

        log.info(">>> Launching Run 2 (Restarting same JobInstance {})...", instanceId);
        JobExecution run2Execution = jobLauncher.run(restartableTransactionJob, run2Params);

        assertThat(run2Execution.getStatus().toString()).isEqualTo("COMPLETED");
        assertThat(run2Execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        // KEY IDENTITY ASSERTION: Same JobInstance, but distinct JobExecution!
        assertThat(run2Execution.getJobInstance().getInstanceId()).isEqualTo(instanceId);
        assertThat(run2Execution.getId()).isNotEqualTo(run1Execution.getId());

        // Verify StepExecution Telemetry on restart:
        // Because items 1-4 were restored from ItemStream, the step only read items 5, 6, 7, 8 (read_count = 4)
        var stepExecution = run2Execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(4);
        assertThat(stepExecution.getWriteCount()).isEqualTo(4);

        // All 8 transactions are now present in PostgreSQL with ZERO duplicates!
        Integer finalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_transactions", Integer.class);
        assertThat(finalCount).isEqualTo(8);

        List<Long> allCommittedIds = jdbcTemplate.queryForList(
                "SELECT transaction_id FROM processed_transactions ORDER BY transaction_id ASC", Long.class);
        assertThat(allCommittedIds).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        log.info("PostgreSQL processed_transactions after restart: {}", allCommittedIds);

        // -----------------------------------------------------------------------------------------
        // RUN 3: Attempting to run a COMPLETED instance throws JobInstanceAlreadyCompleteException!
        // -----------------------------------------------------------------------------------------
        log.info(">>> Launching Run 3 (Attempting duplicate run on completed instance)...");
        assertThatThrownBy(() -> jobLauncher.run(restartableTransactionJob, run2Params))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class)
                .hasMessageContaining("A job instance already exists and is complete");
    }

    @Test
    @DisplayName("Exercise 4: RunIdIncrementer generates new JobInstance by incrementing run.id")
    void testRunIdIncrementer() throws Exception {
        RunIdIncrementer incrementer = new RunIdIncrementer();

        String batchDate = "2026-10-05-inc-" + UUID.randomUUID();
        JobParameters baseParams = new JobParametersBuilder()
                .addString("run.date", batchDate)
                .toJobParameters();

        // First increment -> run.id = 1
        JobParameters params1 = incrementer.getNext(baseParams);
        JobExecution exec1 = jobLauncher.run(restartableTransactionJob, params1);
        assertThat(exec1.getStatus().toString()).isEqualTo("COMPLETED");

        // Second increment -> run.id = 2
        JobParameters params2 = incrementer.getNext(params1);
        JobExecution exec2 = jobLauncher.run(restartableTransactionJob, params2);
        assertThat(exec2.getStatus().toString()).isEqualTo("COMPLETED");

        // Distinct JobInstances created because run.id is an identifying parameter!
        assertThat(exec2.getJobInstance().getInstanceId())
                .isNotEqualTo(exec1.getJobInstance().getInstanceId());
    }
}
