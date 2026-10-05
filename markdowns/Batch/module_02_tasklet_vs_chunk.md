# Module 2: Tasklet vs. Chunk-Oriented Processing

> **Spring Boot**: `4.1.0` | **Spring Batch**: `6.0.5` | **Java**: `17+ / 21 / 25`  
> **Database**: PostgreSQL (Port `5001`, Database `batch_db`, User `admin_user`)  
> **Service Location**: [`batch-service`](../../batch-service)  
> **Curriculum Track**: Week 1 / Module 2 of [`markdowns/Batch/batch_service_implementation_plan.md`](./batch_service_implementation_plan.md)

---

## 1. Concept in Plain Language

A **Tasklet** is a single execute-and-return method (`RepeatStatus.FINISHED`). It runs within a single transaction and is ideal for one-off operations: clearing a staging table, moving an S3 file, sending an email alert, or invoking a stored procedure.

A **Chunk-Oriented Step** is a streaming pipeline composed of `ItemReader<I>`, `ItemProcessor<I, O>`, and `ItemWriter<O>`. Instead of loading a 10-million-row file into memory at once, it reads items one by one, transforms them individually, buffers them until reaching the **commit interval** (chunk size), and writes the entire chunk to the database within a single transaction commit. Returning `null` from an `ItemProcessor` filters out (drops) that item from the chunk.

> **Why it exists**: Processing millions of rows in a single monolithic transaction causes out-of-memory errors and exhausts database undo/redo logs. Chunk processing bounds memory consumption to `chunkSize` and establishes periodic transaction checkpoints.

---

## 2. Diagram & Mental Models

### Tasklet Execution Flow
```
[Start Step] ---> [Tasklet.execute()] ---> [Commit Transaction] ---> [Next Step / End]
                        |
                        +---> RepeatStatus.FINISHED (or CONTINUABLE in a loop)
```

### Chunk-Oriented Processing Pipeline
```
               +-------------------------------------------------------------+
               |                  Step (chunkSize = 3)                       |
               +-------------------------------------------------------------+
                                              |
      +---------------------------------------+---------------------------------------+
      | Loop until Chunk is full (3 items) or Reader returns null (EOF)               |
      |                                                                               |
      |   ItemReader.read()  -------> ItemProcessor.process(item)                     |
      |   (Reads 1 item)              (Transforms 1 item)                             |
      |                                      |                                        |
      |                                      +---> returns transformed item: BUFFERED |
      |                                      +---> returns null: FILTERED (DROPPED)   |
      +---------------------------------------+---------------------------------------+
                                              |
                                              v (When 3 items read)
                                  ItemWriter.write(Chunk<O>)
                                  (Writes all buffered items at once)
                                              |
                                              v
                                  [COMMIT TRANSACTION]
                                  (Persists chunk + updates BATCH_STEP_EXECUTION)
```

### Critical Discovery: Commit Boundary Telemetry in PostgreSQL

```
Items Read:    1 (odd)    2 (even)    3 (odd)  ===> 3 items read  ---> Writer writes [2]    ---> Commit 1
Items Read:    4 (even)   5 (odd)     6 (even) ===> 3 items read  ---> Writer writes [4, 6] ---> Commit 2
Items Read:    7 (odd)    8 (even)    9 (odd)  ===> 3 items read  ---> Writer writes [8]    ---> Commit 3
Items Read:   10 (even)   null (EOF)           ===> 1 item + EOF  ---> Writer writes [10]   ---> Commit 4
```
$$\text{Total Items Read} = 10 \quad|\quad \text{Filtered} = 5 \quad|\quad \text{Written} = 5 \quad|\quad \text{Total Commits} = 4$$

---

## 3. Minimal Runnable Code (Spring Batch 6 / Boot 4)

### Configuration: [`Module02ChunkBasicsJobConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/config/Module02ChunkBasicsJobConfig.java)

```java
package com.chauhan.batchservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.stream.IntStream;

@Configuration
public class Module02ChunkBasicsJobConfig {

    private static final Logger log = LoggerFactory.getLogger(Module02ChunkBasicsJobConfig.class);

    public static final int CHUNK_SIZE = 3;

    @Bean
    public Job chunkBasicsJob(JobRepository jobRepository,
                             Step taskletSetupStep,
                             Step numberChunkStep) {
        return new JobBuilder("chunkBasicsJob", jobRepository)
                .start(taskletSetupStep)
                .next(numberChunkStep)
                .build();
    }

    /**
     * Phase 1 (Tasklet): Executes once. Ideal for pre-checks, schema truncation, DDL.
     */
    @Bean
    public Step taskletSetupStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("taskletSetupStep", jobRepository)
                .tasklet(setupTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet setupTasklet() {
        return (contribution, chunkContext) -> {
            log.info("--- [Tasklet Step] Initializing environment and verifying preconditions ---");
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * Phase 2 (Chunk Step): ItemReader -> ItemProcessor -> ItemWriter.
     * Commit interval = 3.
     */
    @Bean
    public Step numberChunkStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               ItemReader<Integer> numberReader,
                               ItemProcessor<Integer, String> numberProcessor,
                               ItemWriter<String> numberWriter) {
        return new StepBuilder("numberChunkStep", jobRepository)
                .<Integer, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(numberReader)
                .processor(numberProcessor)
                .writer(numberWriter)
                .build();
    }

    /**
     * ItemReader: Reads one item per call. Returning null signals EOF.
     */
    @Bean
    public ItemReader<Integer> numberReader() {
        List<Integer> numbers = IntStream.rangeClosed(1, 10).boxed().toList();
        return new ListItemReader<>(numbers);
    }

    /**
     * ItemProcessor: Transforms Input to Output. Returning null drops the item.
     */
    @Bean
    public ItemProcessor<Integer, String> numberProcessor() {
        return item -> {
            if (item % 2 != 0) {
                log.info("[Processor] Filtering out odd number: {}", item);
                return null; // returning null filters out the item
            }
            String transformed = "PROCESSED-ITEM-" + item;
            log.info("[Processor] Transformed item: {} -> {}", item, transformed);
            return transformed;
        };
    }

    /**
     * ItemWriter: Writes the entire chunk. Receives Chunk<T> in Spring Batch 5/6.
     */
    @Bean
    public ItemWriter<String> numberWriter() {
        return (Chunk<? extends String> chunk) -> {
            log.info(">>> [Writer] Committing chunk of size: {} with items: {} <<<",
                    chunk.size(), chunk.getItems());
        };
    }
}
```

### Automated Verification Test: [`Module02ChunkBasicsTests.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/test/java/com/chauhan/batchservice/Module02ChunkBasicsTests.java)

Execute the test:
```bash
./mvnw test -Dtest=Module02ChunkBasicsTests
```

---

## 4. Common Mistakes & Version Gotchas (Batch 4 vs 5 vs 6)

### Gotcha 1: Is Chunk Size based on Items Read or Items Written? (Huge Gotcha)
- **Misconception**: Developers often assume that if `chunkSize = 10` and their processor filters out 8 items, Spring Batch will keep reading until 10 items are ready to be written.
- **Reality**: **Chunk size is strictly based on the number of items READ from the `ItemReader`**. 
- If `chunkSize = 10` and all 10 items are filtered out by returning `null`, the `ItemWriter` is invoked with an empty chunk (or skipped), the transaction commits, and a commit count increment is recorded.

### Gotcha 2: `ItemWriter.write()` Signature in Spring Batch 5/6
- In **Spring Batch 4**:
  ```java
  public void write(List<? extends T> items) throws Exception;
  ```
- In **Spring Batch 5 & 6**:
  ```java
  public void write(Chunk<? extends T> chunk) throws Exception;
  ```
  `Chunk<T>` is an `Iterable<T>` that provides `.size()`, `.getItems()`, and `.isEmpty()`.

### Gotcha 3: The Danger of State in an `ItemProcessor`
- An `ItemProcessor` is typically invoked inside a singleton bean. If you store instance variables/state inside the processor without making it thread-safe or resetting it per chunk, concurrent steps or retried chunks will contaminate state. Processors should be **stateless and idempotent**.

### Gotcha 4: Forgetting that Returning `null` Drops the Item
- In standard Java streams, `map(x -> null)` results in a stream containing `null`. In Spring Batch, returning `null` from `ItemProcessor.process(item)` explicitly means: **"Do not write this item; silently filter it out"**, which increments `filter_count` in `BATCH_STEP_EXECUTION`.

---

## 5. Hands-on Exercises

### Exercise 1 (Basic — Telemetry Verification via `psql`)
1. Run the test:
   ```bash
   ./mvnw test -Dtest=Module02ChunkBasicsTests
   ```
2. Query `batch_step_execution` directly from PostgreSQL:
   ```bash
   docker exec -it core-auth-service-db psql -U admin_user -d batch_db -c \
   "SELECT step_name, status, commit_count, read_count, filter_count, write_count FROM batch_step_execution ORDER BY step_execution_id DESC LIMIT 2;"
   ```
3. Verify that for `numberChunkStep`:
   - `read_count = 10`
   - `filter_count = 5`
   - `write_count = 5`
   - `commit_count = 4`

### Exercise 2 (Intermediate — Tuning Chunk Size and Watching Commits)
In [`Module02ChunkBasicsJobConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/config/Module02ChunkBasicsJobConfig.java):
1. Change `CHUNK_SIZE = 5`.
2. Calculate in your head: how many commits will occur for 10 items?
   - Items 1-5 read $\rightarrow$ Commit 1
   - Items 6-10 read $\rightarrow$ Commit 2
   - Reader returns null $\rightarrow$ Commit 3 (EOF check)
3. Run the test and verify in `batch_step_execution` that `commit_count` changes from `4` to `3`.

### Exercise 3 (Advanced — Rollback Boundary Observation)
1. In `Module02ChunkBasicsJobConfig.java`, modify `numberWriter` so that it throws an exception when writing item `"PROCESSED-ITEM-8"`:
   ```java
   if (chunk.getItems().contains("PROCESSED-ITEM-8")) {
       throw new RuntimeException("Simulated Database Deadlock on Chunk 3!");
   }
   ```
2. Run the test.
3. Query `batch_step_execution`:
   ```sql
   SELECT step_name, status, commit_count, rollback_count, exit_code FROM batch_step_execution;
   ```
4. Observe that:
   - Chunks 1 and 2 **already committed** to the database!
   - Chunk 3 rolled back (`rollback_count = 1`).
   - The job failed at Chunk 3, proving that transaction boundaries commit incrementally!

---

## 6. Interview Questions & Model Answers

### Q1: When should you use a Tasklet Step versus a Chunk-Oriented Step?
> **Answer**:
> - Use a **Tasklet** when the operation is naturally atomic, procedural, or does not involve streaming large datasets (e.g., executing a `TRUNCATE TABLE`, unzipping a file, validating file existence, calling a single REST API, or running a DB stored procedure).
> - Use a **Chunk-Oriented Step** whenever processing tabular, streaming, or record-based datasets (CSV, JSON, SQL rows) that exceed available RAM, or where incremental commits and transaction rollbacks are required.

### Q2: What happens if an exception is thrown inside an `ItemWriter` during chunk processing? Does the entire step roll back?
> **Answer**: No, only the **current chunk's transaction** rolls back. All prior chunks that were committed remain permanently committed in the database. Without fault-tolerance configured (`skip` or `retry`), the job halts with status `FAILED` at the failed chunk boundary. When restarted, Spring Batch resumes from that checkpoint.

### Q3: How does Spring Batch know that the `ItemReader` has finished reading? What is the EOF contract?
> **Answer**: By contract, an `ItemReader.read()` returns one item per invocation. Returning `null` signals **End Of Data (EOF)**. When `null` is returned, Spring Batch flushes any remaining buffered items in the current chunk to the `ItemWriter`, commits the final transaction, and terminates the step with status `COMPLETED`.

---

## 7. Module 2 Comprehension Quiz

Test your understanding of Module 2 before advancing:

1. **Transaction Boundary**: If you process a file of 1,000 records with a `chunkSize` of 100, how many transaction commits will occur if no items fail?
2. **Filtering**: If your `ItemProcessor` receives an invalid record and you return `null`, does Spring Batch throw an exception, write `null` to the database, or drop the item? What metric is incremented in `BATCH_STEP_EXECUTION`?
3. **Chunk Boundary vs Filter**: If `chunkSize = 10` and your `ItemProcessor` filters out 9 out of the first 10 items read, how many items are passed to `ItemWriter.write()` in that first chunk?
4. **Signature Change**: What is the parameter type of `ItemWriter.write()` in Spring Batch 6 versus Spring Batch 4?
5. **Architectural Choice**: You need to execute an SQL script that truncates an audit table before an ETL job runs. Would you implement this as a Tasklet or a Chunk-oriented step? Why?

---

### Quiz Answer Key (For Review)

<details>
<summary>Click to view answers after attempting the quiz</summary>

1. **Answer**: 11 commits (10 commits for the ten 100-item chunks, plus 1 final transaction commit during the EOF check).
2. **Answer**: It drops the item silently. It does not write to the database and does not throw an exception. The `filter_count` column in `BATCH_STEP_EXECUTION` is incremented.
3. **Answer**: Exactly 1 item (the single item that was not filtered). Chunk size is determined by items *read*, not items *written*.
4. **Answer**: In Spring Batch 6 it is `void write(Chunk<? extends T> chunk)`, whereas in Spring Batch 4 it was `void write(List<? extends T> items)`.
5. **Answer**: A **Tasklet Step**. Truncating a table is a single, atomic procedural task that executes once without streaming record-by-record.
</details>
