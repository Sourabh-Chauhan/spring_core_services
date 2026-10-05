# Module 1: The Core Domain Model & Metadata Tables

> **Spring Boot**: `4.1.0` | **Spring Batch**: `6.0.5` | **Java**: `17+ / 21 / 25`  
> **Database**: PostgreSQL (Port `5001`, Database `batch_db`, User `admin_user`)  
> **Service Location**: [`batch-service`](../../batch-service)  
> **Curriculum Track**: Week 1 / Module 1 of [`batch_service_implementation_plan.md`](batch_service_implementation_plan.md)

---

## 1. Concept in Plain Language

A **Job** is an end-to-end batch process consisting of one or more sequential or parallel **Steps**.

A **JobInstance** defines the *logical* run of a job (for instance: *"End-of-day invoice job for 2026-10-05"*). Its identity is defined by:
$$\text{JobInstance} = \text{Job Name} + \text{Identifying JobParameters}$$

Each *physical attempt* to execute that `JobInstance` is a **JobExecution**. If run 1 fails at 02:00 AM, that attempt is Execution #1 (`FAILED`). If restarted at 02:30 AM, that attempt is Execution #2 (`COMPLETED`), attached to the **same** `JobInstance`.

The **JobRepository** is the central mechanism that persists every state transition into 6 relational database tables (`BATCH_*`). The **ExecutionContext** is a persistent key-value store saved into the database at step and chunk commit points, enabling jobs to resume from the exact record of failure.

> **Why it exists**: Hand-rolled batch scripts rely on custom tables with ad-hoc flag columns (`status = 'IN_PROGRESS'`) that frequently get stuck when JVM processes crash. Spring Batch formalizes an immutable, ACID-compliant state machine directly in your database.

---

## 2. Mental Model & Database Architecture

```
                                    +------------------------------------+
                                    |            JobLauncher             |
                                    +-----------------+------------------+
                                                      | launches with JobParameters
                                                      v
+-------------------------------+                     +----------------------------------+
|          JobInstance          |<--------------------|           JobExecution           |
| (Logical run: Name + Params)  |    1 : N attempts   | (Attempt: START_TIME, STATUS)    |
+-------------------------------+                     +-----------------+----------------+
                                                                        | executes
                                                                        v
                                                      +----------------------------------+
                                                      |          StepExecution           |
                                                      | (READ_COUNT, COMMIT_COUNT, etc.) |
                                                      +-----------------+----------------+
                                                                        | persists state
                                                                        v
                                                      +----------------------------------+
                                                      |         ExecutionContext         |
                                                      |  (Map<String, Object> key-value) |
                                                      +----------------------------------+
```

### PostgreSQL Table Mapping in `batch_db`

```
  +--------------------+             +----------------------+
  | BATCH_JOB_INSTANCE |<------------| BATCH_JOB_EXECUTION  |
  +--------------------+  1       N  +----------------------+
  | *job_instance_id   |             | *job_execution_id    |
  |  job_name          |             |  job_instance_id(FK) |
  |  job_key (MD5 hash)|             |  status (COMPLETED)  |
  +--------------------+             |  exit_code           |
                                     +----------+-----------+
                                                | 1
                                                |
                                                | N
                                     +----------v-----------+
                                     | BATCH_STEP_EXECUTION |
                                     +----------------------+
                                     | *step_execution_id   |
                                     |  job_execution_id(FK)|
                                     |  step_name           |
                                     |  status              |
                                     |  commit_count        |
                                     |  read_count          |
                                     +----------------------+
```

### Purpose of the 6 Metadata Tables

| Table Name | Description & Key Columns |
| :--- | :--- |
| `BATCH_JOB_INSTANCE` | Immutable record of logical job instances. Stores `job_name` and `job_key` (MD5 hash of identifying parameters). |
| `BATCH_JOB_EXECUTION` | Every individual run attempt. Tracks `status` (`STARTING`, `STARTED`, `COMPLETED`, `FAILED`, `STOPPED`), `exit_code`, `start_time`, `end_time`. |
| `BATCH_JOB_EXECUTION_PARAMS`| Key-value parameters passed to the job run. Tracks parameter type (`STRING`, `LONG`, `DATE`, `DOUBLE`) and whether it was `identifying` (`Y`/`N`). |
| `BATCH_STEP_EXECUTION` | Step-level telemetry. Stores `step_name`, `commit_count`, `read_count`, `filter_count`, `write_count`, `read_skip_count`, `process_skip_count`, `rollback_count`. |
| `BATCH_STEP_EXECUTION_CONTEXT` | Base64-serialized key-value map for step state (e.g., current line number in reader for restart). |
| `BATCH_JOB_EXECUTION_CONTEXT` | Base64-serialized key-value map shared across all steps within the job execution. |

---

## 3. Minimal Runnable Code (Spring Batch 6 / Boot 4)

### Configuration: [`Module01HelloWorldJobConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/config/Module01HelloWorldJobConfig.java)

```java
package com.chauhan.batchservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class Module01HelloWorldJobConfig {

    private static final Logger log = LoggerFactory.getLogger(Module01HelloWorldJobConfig.class);

    @Bean
    public Job helloWorldJob(JobRepository jobRepository, Step helloWorldStep) {
        return new JobBuilder("helloWorldJob", jobRepository)
                .start(helloWorldStep)
                .build();
    }

    @Bean
    public Step helloWorldStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("helloWorldStep", jobRepository)
                .tasklet(helloWorldTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet helloWorldTasklet() {
        return (contribution, chunkContext) -> {
            log.info(">>> Executing Module 1: Hello World Tasklet! <<<");

            // 1. Step-level ExecutionContext: private to this step
            ExecutionContext stepContext = chunkContext.getStepContext()
                    .getStepExecution()
                    .getExecutionContext();
            stepContext.putString("mentorMessage", "Welcome to Spring Batch 6 on Spring Boot 4!");

            // 2. Job-level ExecutionContext: shared across all steps in the job
            ExecutionContext jobContext = chunkContext.getStepContext()
                    .getStepExecution()
                    .getJobExecution()
                    .getExecutionContext();
            jobContext.putString("pipelineStatus", "INITIALIZED");

            log.info("Saved data to Step ExecutionContext: mentorMessage={}", stepContext.get("mentorMessage"));
            log.info("Saved data to Job ExecutionContext: pipelineStatus={}", jobContext.get("pipelineStatus"));

            return RepeatStatus.FINISHED;
        };
    }
}
```

### Automated Test: [`Module01CoreModelTests.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/test/java/com/chauhan/batchservice/Module01CoreModelTests.java)

```java
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
        long timestamp = System.currentTimeMillis();
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("run.timestamp", timestamp)
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Inspect 1: Query BATCH_JOB_INSTANCE table directly from PostgreSQL
        List<Map<String, Object>> instances = jdbcTemplate.queryForList(
                "SELECT job_instance_id, job_name, job_key FROM batch_job_instance WHERE job_instance_id = ?",
                jobExecution.getJobInstance().getInstanceId()
        );
        assertThat(instances).hasSize(1);
        assertThat(instances.getFirst().get("job_name")).isEqualTo("helloWorldJob");

        // Inspect 2: Query BATCH_JOB_EXECUTION table directly from PostgreSQL
        List<Map<String, Object>> executions = jdbcTemplate.queryForList(
                "SELECT job_execution_id, status, exit_code FROM batch_job_execution WHERE job_execution_id = ?",
                jobExecution.getId()
        );
        assertThat(executions).hasSize(1);
        assertThat(executions.getFirst().get("status")).isEqualTo("COMPLETED");

        // Inspect 3: Query BATCH_STEP_EXECUTION table directly from PostgreSQL
        List<Map<String, Object>> steps = jdbcTemplate.queryForList(
                "SELECT step_name, status, commit_count FROM batch_step_execution WHERE job_execution_id = ?",
                jobExecution.getId()
        );
        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().get("step_name")).isEqualTo("helloWorldStep");

        // Inspect 4: Query ExecutionContext from BATCH_JOB_EXECUTION_CONTEXT
        List<Map<String, Object>> jobContexts = jdbcTemplate.queryForList(
                "SELECT short_context FROM batch_job_execution_context WHERE job_execution_id = ?",
                jobExecution.getId()
        );
        assertThat(jobContexts).hasSize(1);
        assertThat(jobExecution.getExecutionContext().getString("pipelineStatus")).isEqualTo("INITIALIZED");
    }
}
```

### Running Verification Command
```bash
./mvnw test -Dtest=Module01CoreModelTests
```

---

## 4. Common Mistakes & Version Gotchas (Batch 4 vs 5 vs 6)

### Gotcha 1: Spring Batch 6 Package Reorganization
Older tutorials (Batch 4 and Batch 5) import root packages like `org.springframework.batch.core.Job`. In **Spring Batch 6**, classes are strictly organized into domain subpackages:
- `org.springframework.batch.core.job.Job`
- `org.springframework.batch.core.job.JobExecution`
- `org.springframework.batch.core.job.parameters.JobParameters`
- `org.springframework.batch.core.step.Step`
- `org.springframework.batch.core.step.StepExecution`
- `org.springframework.batch.infrastructure.item.ExecutionContext`
- `org.springframework.batch.infrastructure.repeat.RepeatStatus`

### Gotcha 2: Repository Persistence Mode in Spring Batch 6
In Batch 6, `DefaultBatchConfiguration` defaults to `ResourcelessJobRepository` (an in-memory repository). To enable real database persistence, you **must** supply both:
```java
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class BatchConfig { ... }
```
Without `@EnableJdbcJobRepository`, jobs run in-memory and nothing is written to the database!

### Gotcha 3: The `JobInstanceAlreadyCompleteException`
If an instance completed successfully, Spring Batch **rejects** running it again with the exact same identifying parameters.
- If you need a new run: change an identifying parameter (e.g. timestamp or incrementer).
- If you rerun after a `FAILED` execution: Spring Batch **permits** the rerun under the same `JobInstance`!

### Gotcha 4: Step Context vs Job Context Scope
- `stepExecution.getExecutionContext()` is private to that single step. Subsequent steps cannot see it unless promoted.
- `jobExecution.getExecutionContext()` is accessible across all steps throughout the entire job run.

---

## 5. Hands-on Exercises

### Exercise 1 (Basic — Verification & Direct SQL)
1. Run `./mvnw test -Dtest=Module01CoreModelTests`.
2. Connect to the PostgreSQL container and query the tables:
   ```bash
   docker exec -it core-auth-service-db psql -U admin_user -d batch_db -c "SELECT * FROM batch_job_instance;"
   docker exec -it core-auth-service-db psql -U admin_user -d batch_db -c "SELECT job_execution_id, status, start_time, end_time FROM batch_job_execution;"
   ```
3. Verify that `job_key` in `batch_job_instance` matches the MD5 hash generated from your job name and parameters.

### Exercise 2 (Intermediate — Non-identifying Parameters)
Modify the test's `JobParametersBuilder` to pass a non-identifying parameter:
```java
JobParameters jobParameters = new JobParametersBuilder()
    .addLong("run.timestamp", 1000L)              // identifying (default = true)
    .addString("triggeredBy", "sourabh", false)   // non-identifying (false)
    .toJobParameters();
```
Query `batch_job_execution_params` in PostgreSQL:
```sql
SELECT parameter_name, parameter_value, identifying FROM batch_job_execution_params;
```
Verify that `triggeredBy` has `identifying = 'N'`. Then change `triggeredBy` to `"admin"` and rerun with the same `run.timestamp=1000L`. Notice that it fails with `JobInstanceAlreadyCompleteException` because non-identifying parameters do NOT create a new `JobInstance`!

### Exercise 3 (Advanced — Failure & Resuming under Same Instance)
1. In `Module01HelloWorldJobConfig.java`, throw a `RuntimeException("Simulated crash")` inside the Tasklet.
2. Run the test and observe that `batch_job_execution` records `status = 'FAILED'`.
3. Remove the exception and run the test with the **exact same** parameter.
4. Query `batch_job_execution`:
   ```sql
   SELECT job_execution_id, job_instance_id, status FROM batch_job_execution;
   ```
5. Observe that you now have **two** executions (one `FAILED`, one `COMPLETED`) sharing the **same** `job_instance_id`!

---

## 6. Interview Questions & Model Answers

### Q1: What is the fundamental difference between `JobInstance` and `JobExecution`?
> **Answer**: `JobInstance` represents the logical definition of a batch job run defined by its name and identifying parameters (e.g. `dailyReportJob` for `2026-10-05`). `JobExecution` is the physical attempt to execute that instance. One `JobInstance` can have multiple `JobExecution`s if earlier executions failed or were manually stopped.

### Q2: How does Spring Batch guarantee idempotency and prevent duplicate job executions?
> **Answer**: Before starting a job, `JobRepository` checks `BATCH_JOB_INSTANCE` for an existing record matching the job name and the MD5 hash (`job_key`) of all identifying parameters. If a matching record is found and its most recent `JobExecution` has `status = 'COMPLETED'`, Spring Batch aborts immediately and throws `JobInstanceAlreadyCompleteException`.

### Q3: Why does `ExecutionContext` exist if we already have Spring Beans and JVM memory?
> **Answer**: Spring beans are transient; if the server crashes or restarts, all in-memory variables are lost. `ExecutionContext` is serialized into the database at transaction commit boundaries (`BATCH_STEP_EXECUTION_CONTEXT` and `BATCH_JOB_EXECUTION_CONTEXT`). When a failed job is restarted, the reader loads the saved offset from `ExecutionContext` and resumes from the exact point of interruption without re-processing earlier items.

---

## 7. Comprehension Checkpoint Quiz

Test your understanding of Module 1:

1. **Identity**: If Job A runs today with identifying parameter `date=2026-10-05` and finishes with `COMPLETED`, what happens if an external cron triggers Job A with `date=2026-10-05` tomorrow?
2. **Parameters**: What is the difference in behavior between an *identifying* job parameter and a *non-identifying* job parameter?
3. **Execution Context**: If Step 1 writes a value to its `StepExecution.getExecutionContext()`, can Step 2 read that value directly from its own `StepExecution.getExecutionContext()`? Why or why not?
4. **Metadata Tables**: Which table tracks how many items were read, written, and committed during a chunk step?
5. **Spring Batch 6 API**: Where were `Job` and `Step` located in Spring Batch 4/5, and what are their package locations in Spring Batch 6?

---

### Quiz Answer Key (For Review)

<details>
<summary>Click to view answers after attempting the quiz</summary>

1. **Answer**: It throws a `JobInstanceAlreadyCompleteException` and refuses to run, because an instance for `(Job A, date=2026-10-05)` has already completed successfully.
2. **Answer**: An *identifying* parameter contributes to the unique identity (MD5 hash / `job_key`) of a `JobInstance`. A *non-identifying* parameter is passed into the job context for runtime consumption but does NOT affect `JobInstance` identity or collision checks.
3. **Answer**: No. `StepExecution.getExecutionContext()` is private and scoped only to that specific step execution. To share data across steps, you must put it in `JobExecution.getExecutionContext()` or use an `ExecutionContextPromotionListener`.
4. **Answer**: `BATCH_STEP_EXECUTION` (tracks `commit_count`, `read_count`, `write_count`, `filter_count`, `rollback_count`, etc.).
5. **Answer**: In Spring Batch 4/5, they were located in `org.springframework.batch.core.Job` and `org.springframework.batch.core.Step`. In Spring Batch 6, they are located in `org.springframework.batch.core.job.Job` and `org.springframework.batch.core.step.Step`.
</details>
