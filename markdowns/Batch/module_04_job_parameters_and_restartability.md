# Module 04: Job Parameters, Incrementers, Restartability & Identity

---

## 1. Concept & "Why It Exists"

### The Concept (<= 150 words)
In Spring Batch, a **`JobInstance`** represents the logical definition of a run, uniquely identified by the combination of its **`Job` name** and its **identifying `JobParameters`**:
$$\text{JobInstance} = \text{Job Name} + \text{Identifying JobParameters}$$

Each attempt to run a `JobInstance` produces a **`JobExecution`**. If an execution finishes with `COMPLETED`, that `JobInstance` is permanently closed; attempting to run it again throws `JobInstanceAlreadyCompleteException`. 

However, if an execution fails (`FAILED`), Spring Batch allows you to **restart** the job using the **exact same identifying parameters**. During restart, Spring Batch creates a *new* `JobExecution` linked to the *same* `JobInstance`. Components implementing **`ItemStream`** (like `FlatFileItemReader`) restore their progress from the saved `ExecutionContext` in `BATCH_STEP_EXECUTION_CONTEXT` and skip already-committed records, resuming execution mid-stream without duplicate processing.

### Why It Exists (The Problem Hand-Rolled Frameworks Face)
In custom home-grown batch scripts:
1. If a 1,000,000-record batch crashes at record 850,000, developers face a dilemma:
   - Re-running from record 0 either crashes on unique constraint violations or duplicates customer charges.
   - Manually editing input files or writing one-off cleanup queries is error-prone and dangerous.
2. Hand-rolled scripts lack formal identity rules, making accidental duplicate execution of daily billing batches a constant risk.
3. Spring Batch solves this out of the box: transactions commit in chunks, state is checkpointed in database metadata tables, duplicate completed runs are rejected, and failed jobs resume cleanly from the exact chunk offset.

---

## 2. ASCII Architectural Diagrams

### A. Job Identity Formula & Lifecycle

```
Job Launcher: run("billingJob", {date: "2026-10-05", run.id: 1})
                          │
                          ▼
            Does JobInstance Exist in DB?
           (BATCH_JOB_INSTANCE: name + key)
                 │                   │
         NO      │                   │  YES
     ┌───────────┘                   └───────────┐
     ▼                                           ▼
Create New JobInstance               What is the Last Status?
     │                                (BATCH_JOB_EXECUTION)
     │                                      │           │
     │                            COMPLETED │           │ FAILED / STOPPED
     │                                      ▼           ▼
     │                           THROW EXCEPTION!   ALLOW RESTART!
     │                     (JobInstanceAlready     (New JobExecution
     │                      CompleteException)      for SAME JobInstance)
     ▼                                                  │
Create New JobExecution ◄───────────────────────────────┘
```

---

### B. Mid-Stream Chunk Failure & ItemStream Restart Mechanism

```
Input File: [Record 1, 2, 3, 4, 5, 6, 7, 8]  | Chunk Size = 2

EXECUTION 1 (Fails at Record 6):
  Chunk 1: [Rec 1, Rec 2] ──► Write ──► COMMIT 1 ──► DB Context: read.count = 2
  Chunk 2: [Rec 3, Rec 4] ──► Write ──► COMMIT 2 ──► DB Context: read.count = 4
  Chunk 3: [Rec 5, Rec 6] ──► Processor throws Exception at Rec 6!
                              ROLLBACK! (Rec 5 & 6 NOT in DB)
                              DB Context remains: read.count = 4
                              Status: FAILED

----------------------------------------------------------------------------------

EXECUTION 2 (Restart with SAME identifying parameters):
  ItemStream.open(ExecutionContext):
    Restores: read.count = 4
    Reader automatically SKIPS lines 1, 2, 3, 4!

  Chunk 3: [Rec 5, Rec 6] ──► Processed ──► Write ──► COMMIT 3 ──► read.count = 6
  Chunk 4: [Rec 7, Rec 8] ──► Processed ──► Write ──► COMMIT 4 ──► read.count = 8
  Status: COMPLETED (All 8 records processed, 0 duplicates, 0 missed!)
```

---

## 3. Minimal Runnable Code

All components are implemented and verified in the repository:

### 3.1. Job Configuration with Validation, Incrementer & Step-Scoped Reader
Defined in [`Module04JobParametersAndRestartConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/config/Module04JobParametersAndRestartConfig.java):

```java
@Configuration
public class Module04JobParametersAndRestartConfig {

    public static final int CHUNK_SIZE = 2;

    @Bean
    public Job restartableTransactionJob(JobRepository jobRepository,
                                         Step processTransactionsStep) {
        // Enforce required and optional parameters
        DefaultJobParametersValidator validator = new DefaultJobParametersValidator(
                new String[]{"run.date"}, // Required
                new String[]{"file.name", "fail.at.transaction.id", "run.id", "run.timestamp", "operator.name"} // Optional
        );

        return new JobBuilder("restartableTransactionJob", jobRepository)
                .validator(validator)
                .incrementer(new RunIdIncrementer())
                .start(processTransactionsStep)
                .build();
    }

    @Bean
    public Step processTransactionsStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        FlatFileItemReader<Transaction> transactionItemReader,
                                        ItemProcessor<Transaction, Transaction> transactionItemProcessor,
                                        JdbcBatchItemWriter<Transaction> transactionJdbcWriter) {
        return new StepBuilder("processTransactionsStep", jobRepository)
                .<Transaction, Transaction>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(transactionItemReader)
                .processor(transactionItemProcessor)
                .writer(transactionJdbcWriter)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Transaction> transactionItemReader(
            @Value("#{jobParameters['file.name']}") String fileName) {

        String path = (fileName != null && !fileName.isBlank()) ? fileName : "data/transactions.csv";

        return new FlatFileItemReaderBuilder<Transaction>()
                .name("transactionItemReader") // Used as prefix in ExecutionContext!
                .resource(new ClassPathResource(path))
                .linesToSkip(1)
                .delimited()
                .names("transactionId", "accountNumber", "amount")
                .fieldSetMapper(fs -> new Transaction(
                        fs.readLong("transactionId"),
                        fs.readString("accountNumber"),
                        fs.readBigDecimal("amount"),
                        "PENDING"
                ))
                .saveState(true) // Writes read.count to ExecutionContext at each commit
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<Transaction, Transaction> transactionItemProcessor(
            @Value("#{jobParameters['fail.at.transaction.id']}") Long failAtTransactionId) {
        return item -> {
            if (failAtTransactionId != null && failAtTransactionId.equals(item.transactionId())) {
                throw new IllegalStateException("Simulated failure at Transaction ID: " + failAtTransactionId);
            }
            return new Transaction(item.transactionId(), item.accountNumber(), item.amount(), "COMPLETED");
        };
    }

    @Bean
    public JdbcBatchItemWriter<Transaction> transactionJdbcWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Transaction>()
                .dataSource(dataSource)
                .sql("INSERT INTO processed_transactions (transaction_id, account_number, amount, status) " +
                     "VALUES (:transactionId, :accountNumber, :amount, :status) " +
                     "ON CONFLICT (transaction_id) DO UPDATE SET status = EXCLUDED.status")
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }
}
```

---

## 4. Critical Gotchas (Spring Batch 4 vs 5 vs 6)

| Feature / Class | Spring Batch 4 | Spring Batch 5 (Boot 3) | Spring Batch 6 (Boot 4) |
| :--- | :--- | :--- | :--- |
| **`JobParameters` Internal Model** | `Map<String, JobParameter>` | `Map<String, JobParameter<?>>` | **Java `Record`** holding `Set<JobParameter<?>>`. Implements `Iterable<JobParameter<?>>`. Access via `p.name()`, `p.value()`. |
| **`JobParameter<T>`** | Supported only `String`, `Long`, `Double`, `Date`. Class with getters. | Generic `JobParameter<T>`, added `LocalDate`, `LocalTime`, `LocalDateTime`. | **Java `Record`**. Accessors are `p.name()`, `p.value()`, `p.type()`, `p.identifying()`. |
| **Parameter Validation Exception** | `org.springframework.batch.core.JobParametersInvalidException` | `org.springframework.batch.core.JobParametersInvalidException` | Renamed to **`org.springframework.batch.core.job.parameters.InvalidJobParametersException`**. |
| **`RunIdIncrementer` Package** | `org.springframework.batch.core.launch.support.RunIdIncrementer` | `org.springframework.batch.core.launch.support.RunIdIncrementer` | Moved to **`org.springframework.batch.core.job.parameters.RunIdIncrementer`**. |
| **`DefaultJobParametersValidator` Package** | `org.springframework.batch.core.job.DefaultJobParametersValidator` | `org.springframework.batch.core.job.DefaultJobParametersValidator` | Moved to **`org.springframework.batch.core.job.parameters.DefaultJobParametersValidator`**. |
| **Identifying vs Non-Identifying** | Non-identifying parameters required prefix syntax `-name=value` in CLI. | Explicit boolean flag in `JobParametersBuilder.addString(key, val, identifying)`. | Explicit boolean flag preserved on record: `JobParameter(name, val, type, identifying)`. |
| **`ListItemReader` vs `FlatFileItemReader`** | `ListItemReader` does NOT implement `ItemStream`. Restarting reads from 0! | Same. | Same. In-memory readers do not save state. Use `ItemStream` readers for mid-stream recovery. |

> [!WARNING]
> **The `ItemStream` Name Gotcha**: When configuring a reader (such as `FlatFileItemReaderBuilder`), calling `.name("myReader")` is **mandatory** for restartability. `ItemStream` uses this name as the key prefix in `BATCH_STEP_EXECUTION_CONTEXT` (`myReader.read.count`). If omitted, Spring Batch cannot namespace the offset and restart state recovery will fail silently.

---

## 5. Graduated Hands-On Exercises

The test suite in [`Module04JobParametersAndRestartTests.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/test/java/com/chauhan/batchservice/Module04JobParametersAndRestartTests.java) implements all 3 exercises against PostgreSQL.

### Exercise 1: Parameter Validation (Easy)
- **Objective**: Configure `DefaultJobParametersValidator` to mandate `run.date`.
- **Action**: Launch the job omitting `run.date`.
- **Verification**: Assert that `InvalidJobParametersException` is thrown before any step begins.

### Exercise 2: Mid-Stream Failure & ExecutionContext Inspection (Medium)
- **Objective**: Process 8 records with `chunkSize = 2`. Inject a failure on transaction #6.
- **Action**: Run execution 1.
- **Verification**:
  - PostgreSQL table `processed_transactions` contains exactly 4 records (transactions 1, 2, 3, 4).
  - Transactions 5 and 6 are rolled back.
  - Step's `ExecutionContext` checkpointed `transactionItemReader.read.count = 4`.

### Exercise 3: Seamless Restart Without Reprocessing (Hard)
- **Objective**: Re-run the job with the **exact same identifying parameter** (`run.date`), but with the error cleared (`fail.at.transaction.id = -1`).
- **Action**: Launch execution 2.
- **Verification**:
  - Assert that `JobExecution.getJobInstance().getInstanceId()` matches Execution 1 (same instance).
  - Assert that `StepExecution.getReadCount() == 4` and `writeCount == 4` (skipped records 1-4).
  - Assert that all 8 records are now in PostgreSQL with zero duplicates.
  - Attempt Execution 3 with the same parameters and verify `JobInstanceAlreadyCompleteException`.

---

## 6. Three Enterprise Interview Questions & Answers

### Q1: What is the exact difference between an identifying parameter and a non-identifying parameter?
**Answer**:
Identifying parameters are hashed together with the Job Name to generate the `JOB_KEY` in `BATCH_JOB_INSTANCE`. They define the business identity of a run (e.g. `billingDate=2026-10-05`). 
Non-identifying parameters (`identifying=false`) are persisted in `BATCH_JOB_EXECUTION_PARAMS` for audit or execution-specific behavior (e.g. `triggeredBy=sourabh`, `debugMode=true`, `run.timestamp=1791202970`), but they do **not** contribute to `JOB_KEY` and do not alter `JobInstance` identity.

### Q2: A chunk-oriented step crashed midway through a 500,000-row file. When restarted, does it start from row 0 or row 250,000? How does Spring Batch achieve this?
**Answer**:
It resumes from row 250,000. 
At each chunk commit, the `ItemStream` interface callback `update(ExecutionContext)` writes the current reader offset (`read.count`) into the step's `ExecutionContext`. The `JobRepository` commits this context into `BATCH_STEP_EXECUTION_CONTEXT` within the same transaction as the chunk data.
On restart, Spring Batch reloads the last execution context and calls `ItemStream.open(ExecutionContext)`. The reader reads `read.count` and skips that number of records before handing items to the step.

### Q3: If a Job completes successfully, can you force Spring Batch to rerun it with the same identifying parameters?
**Answer**:
No. Spring Batch's core philosophy guarantees that a `JobInstance` that reaches `COMPLETED` can never be run again, preventing duplicate business execution (e.g. double-billing customers). 
To rerun:
1. Either supply a new identifying parameter (e.g. via an Incrementer like `run.id=2`).
2. Or use `job.preventRestart()` if you want the job to never allow restarts even on failure.

---

## 7. Module 4 Comprehension Quiz & Answers

### Q1: What is the mathematical identity formula that determines a `JobInstance` in Spring Batch?
**Answer**:
$$\text{JobInstance} = \text{Job Name} + \text{Identifying JobParameters}$$

In PostgreSQL, the `JobRepository` extracts all parameters with `identifying = true`, serializes their key-value pairs, and generates an MD5 hash stored in the `JOB_KEY` column of `BATCH_JOB_INSTANCE`. Any parameter flagged with `identifying = false` (or added with `builder.addString(key, val, false)`) is excluded from this formula and stored only in `BATCH_JOB_EXECUTION_PARAMS`.

---

### Q2: If a job fails midway through execution, what happens if you run it again with the exact same identifying parameters vs different identifying parameters?
**Answer**:
- **With the EXACT SAME identifying parameters**: Spring Batch triggers a **native restart**. It detects that the corresponding `JobInstance` already exists in `BATCH_JOB_INSTANCE` and that its last execution ended in `FAILED` (or `STOPPED`). It creates a **new `JobExecution` linked to the SAME `JobInstance`**, reloads the persisted `ExecutionContext` from `BATCH_STEP_EXECUTION_CONTEXT`, and passes it to `ItemStream.open(...)` so readers skip already-committed chunks and resume processing from the failure boundary.
- **With DIFFERENT identifying parameters**: Spring Batch treats it as an entirely separate business event. It inserts a **new row into `BATCH_JOB_INSTANCE`** (new instance ID) and begins execution from the very beginning (record 0), without referencing or restoring any previous state.

---

### Q3: Why does `FlatFileItemReader` save its offset on failure, while `ListItemReader` does not? What interface makes the difference?
**Answer**:
The **`ItemStream`** interface (`org.springframework.batch.infrastructure.item.ItemStream`).
- `FlatFileItemReader` implements `ItemStreamReader`. At every chunk commit boundary, the framework invokes `update(ExecutionContext)`, saving reader telemetry (such as `transactionItemReader.read.count = 4`) into the step's `ExecutionContext`, which commits to the database alongside chunk writes.
- `ListItemReader` implements `ItemReader` only—it does **NOT** implement `ItemStream`. It has no lifecycle callbacks to inspect, serialize, or restore its position. If a job fails midway, `ListItemReader` has no state to restore and starts reading from element 0 upon restart.

---

### Q4: What exception is thrown if you try to launch a job with identifying parameters that have already completed with status `COMPLETED`?
**Answer**:
**`JobInstanceAlreadyCompleteException`** (`org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException`).

Spring Batch strictly prevents re-execution of completed instances to ensure enterprise safety (e.g., preventing duplicate salary disbursements or multiple credit card billings for the same billing cycle). To run again, you must supply at least one new identifying parameter (e.g., via `RunIdIncrementer`).

---

### Q5: In Spring Batch 6 (Spring Boot 4), how are `JobParameters` and `JobParameter<T>` modeled in Java code compared to Spring Batch 4?
**Answer**:
- **Spring Batch 4**:
  - `JobParameters` wrapped an internal `Map<String, JobParameter>`.
  - `JobParameter` was a legacy POJO class restricted to `String`, `Long`, `Double`, and `Date`.
  - Accessed via getters: `param.getValue()`, `param.getType()`.
- **Spring Batch 6**:
  - `JobParameters` is a native **Java `Record`** holding a `Set<JobParameter<?>>` and implements `Iterable<JobParameter<?>>`.
  - `JobParameter<T>` is a generic **Java `Record`** supporting modern `java.time` types (`LocalDate`, `LocalTime`, `LocalDateTime`).
  - Fields are accessed via canonical record accessor methods: `p.name()`, `p.value()`, `p.type()`, `p.identifying()`.
  - Iteration can be performed directly via enhanced for-loops: `for (JobParameter<?> p : jobParameters)` or via `jobParameters.parameters()`.

