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
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
class Module02ChunkBasicsTests {

    private static final Logger log = LoggerFactory.getLogger(Module02ChunkBasicsTests.class);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("chunkBasicsJob")
    private Job chunkBasicsJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(chunkBasicsJob);
    }

    @Test
    @DisplayName("Module 2 Test: Verify Tasklet and Chunk telemetry in PostgreSQL BATCH_STEP_EXECUTION")
    void testChunkBasicsJobAndInspectStepExecution() throws Exception {
        // Given: Unique timestamp parameter
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("run.timestamp", System.currentTimeMillis())
                .toJobParameters();

        // When: Execute the 2-step job (Tasklet -> Chunk)
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: Job must complete successfully
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Inspect: Query Step 1 (Tasklet) telemetry from PostgreSQL
        List<Map<String, Object>> taskletSteps = jdbcTemplate.queryForList(
                "SELECT step_name, status, commit_count, read_count, filter_count, write_count " +
                "FROM batch_step_execution WHERE job_execution_id = ? AND step_name = 'taskletSetupStep'",
                jobExecution.getId()
        );
        assertThat(taskletSteps).hasSize(1);
        Map<String, Object> taskletRow = taskletSteps.getFirst();
        log.info("STEP 1 (Tasklet) in PostgreSQL: {}", taskletRow);
        assertThat(taskletRow.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) taskletRow.get("commit_count")).longValue()).isEqualTo(1L);
        assertThat(((Number) taskletRow.get("read_count")).longValue()).isEqualTo(0L);
        assertThat(((Number) taskletRow.get("write_count")).longValue()).isEqualTo(0L);

        // Inspect: Query Step 2 (Chunk-oriented) telemetry from PostgreSQL
        List<Map<String, Object>> chunkSteps = jdbcTemplate.queryForList(
                "SELECT step_name, status, commit_count, read_count, filter_count, write_count, rollback_count " +
                "FROM batch_step_execution WHERE job_execution_id = ? AND step_name = 'numberChunkStep'",
                jobExecution.getId()
        );
        assertThat(chunkSteps).hasSize(1);
        Map<String, Object> chunkRow = chunkSteps.getFirst();
        log.info("STEP 2 (Chunk) in PostgreSQL: {}", chunkRow);

        long readCount = ((Number) chunkRow.get("read_count")).longValue();
        long filterCount = ((Number) chunkRow.get("filter_count")).longValue();
        long writeCount = ((Number) chunkRow.get("write_count")).longValue();
        long commitCount = ((Number) chunkRow.get("commit_count")).longValue();
        long rollbackCount = ((Number) chunkRow.get("rollback_count")).longValue();

        // Core Assertions verifying Spring Batch chunk mechanics:
        // 1. All 10 numbers were read:
        assertThat(readCount).isEqualTo(10L);
        // 2. 5 odd numbers were filtered out (processor returned null):
        assertThat(filterCount).isEqualTo(5L);
        // 3. 5 even numbers were written:
        assertThat(writeCount).isEqualTo(5L);
        // 4. Mathematical integrity: read_count == write_count + filter_count
        assertThat(readCount).isEqualTo(writeCount + filterCount);
        // 5. Zero rollbacks
        assertThat(rollbackCount).isEqualTo(0L);
        // 6. Commits: Chunk size (3) applies to items READ, NOT items written!
        // Chunk 1: reads 1, 2, 3 -> writes [2] -> commit 1
        // Chunk 2: reads 4, 5, 6 -> writes [4, 6] -> commit 2
        // Chunk 3: reads 7, 8, 9 -> writes [8] -> commit 3
        // Chunk 4: reads 10 + EOF -> writes [10] -> commit 4
        // Total commits = ceil(10 / 3) = 4
        assertThat(commitCount).isEqualTo(4L);

        log.info("TELEMETRY VERIFIED: Read={}, Filtered={}, Written={}, Commits={}",
                readCount, filterCount, writeCount, commitCount);
    }
}
