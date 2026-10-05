package com.chauhan.batchservice;

import com.chauhan.batchservice.model.Customer;
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
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
class Module03EnterpriseReadersWritersTests {

    private static final Logger log = LoggerFactory.getLogger(Module03EnterpriseReadersWritersTests.class);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("csvToDatabaseJob")
    private Job csvToDatabaseJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcPagingItemReader<Customer> customerPagingReader;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(csvToDatabaseJob);
    }

    @Test
    @DisplayName("Module 3 Test: Stream CSV -> Filter & Normalize -> Batch Write to PostgreSQL -> Read with Paging")
    void testCsvToDatabasePipelineAndPagingReader() throws Exception {
        // Given: Unique parameter for clean execution
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("run.timestamp", System.currentTimeMillis())
                .toJobParameters();

        // When: Execute the pipeline
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: Job must complete successfully
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Verify Step Telemetry in PostgreSQL BATCH_STEP_EXECUTION
        List<Map<String, Object>> steps = jdbcTemplate.queryForList(
                "SELECT step_name, status, commit_count, read_count, filter_count, write_count " +
                "FROM batch_step_execution WHERE job_execution_id = ? AND step_name = 'loadCustomersChunkStep'",
                jobExecution.getId()
        );
        assertThat(steps).hasSize(1);
        Map<String, Object> chunkTelemetry = steps.getFirst();
        log.info("CSV CHUNK STEP Telemetry in PostgreSQL: {}", chunkTelemetry);

        long readCount = ((Number) chunkTelemetry.get("read_count")).longValue();
        long filterCount = ((Number) chunkTelemetry.get("filter_count")).longValue();
        long writeCount = ((Number) chunkTelemetry.get("write_count")).longValue();

        // 6 records in CSV: 4 valid, 2 invalid emails filtered
        assertThat(readCount).isEqualTo(6L);
        assertThat(filterCount).isEqualTo(2L);
        assertThat(writeCount).isEqualTo(4L);

        // Verify destination table 'customers' in PostgreSQL
        List<Map<String, Object>> dbRows = jdbcTemplate.queryForList(
                "SELECT customer_id, first_name, last_name, email, status FROM customers ORDER BY customer_id ASC"
        );
        assertThat(dbRows).hasSize(4);
        log.info("Persisted Customers in PostgreSQL: {}", dbRows);

        // Verify normalization: first names are uppercase
        assertThat(dbRows.getFirst().get("first_name")).isEqualTo("JOHN");
        assertThat(dbRows.getFirst().get("last_name")).isEqualTo("DOE");
        assertThat(dbRows.getFirst().get("email")).isEqualTo("john.doe@example.com");

        // Verify filtered records (103 and 105) do NOT exist in database
        List<Long> customerIds = dbRows.stream()
                .map(r -> ((Number) r.get("customer_id")).longValue())
                .toList();
        assertThat(customerIds).containsExactly(101L, 102L, 104L, 106L);
        assertThat(customerIds).doesNotContain(103L, 105L);

        // Verify JdbcPagingItemReader: Read back the persisted rows page-by-page
        customerPagingReader.open(new ExecutionContext());
        List<Customer> readBackCustomers = new ArrayList<>();
        Customer item;
        while ((item = customerPagingReader.read()) != null) {
            readBackCustomers.add(item);
        }
        customerPagingReader.close();

        assertThat(readBackCustomers).hasSize(4);
        log.info("Read back via JdbcPagingItemReader: {}", readBackCustomers);
        assertThat(readBackCustomers.getFirst().firstName()).isEqualTo("JOHN");
    }
}
