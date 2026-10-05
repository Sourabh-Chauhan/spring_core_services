# Module 3: Enterprise ItemReaders & ItemWriters

> **Spring Boot**: `4.1.0` | **Spring Batch**: `6.0.5` | **Java**: `17+ / 21 / 25`  
> **Database**: PostgreSQL (Port `5001`, Database `batch_db`, User `admin_user`)  
> **Service Location**: [`batch-service`](../../batch-service)  
> **Curriculum Track**: Week 2 / Module 3 of [`markdowns/Batch/batch_service_implementation_plan.md`](./batch_service_implementation_plan.md)

---

## 1. Concept in Plain Language

An **ItemReader** abstracts input streaming, retrieving one record at a time via `read()` until `null` signals EOF (End Of File). Spring Batch provides out-of-the-box readers for flat files (`FlatFileItemReader`), relational databases (`JdbcCursorItemReader`, `JdbcPagingItemReader`, `JpaPagingItemReader`), Kafka, and JSON.

An **ItemWriter** abstracts output batching, flushing a `Chunk<T>` of records in a single database transaction or file write operation via `write(Chunk<? extends T> chunk)`. The `CompositeItemWriter` chains multiple writers, allowing one chunk to write to a database and an audit file simultaneously.

For databases, a **Cursor Reader** holds open a single streaming database connection with low memory overhead, whereas a **Paging Reader** executes page-by-page queries (`LIMIT / OFFSET` or keyset pagination) and is thread-safe for parallel scaling.

> **Why it exists**: Custom batch scripts often write bespoke file parsers and database connection loops that leak connections, buffer entire datasets into RAM, and lose offsets when failing midway. Built-in readers and writers provide streaming resource management, restartable cursor offsets, and batch SQL execution out of the box.

---

## 2. Diagram & Mental Models

### The FlatFile to JDBC Batch Insert Architecture

```
[customers.csv]
       |
       v (reads line)
+---------------------------------------------------------------------------------+
| FlatFileItemReader<Customer>                                                    |
|   1. LineTokenizer: splits "101,John,Doe,john@example.com,ACTIVE" by delimiter  |
|   2. FieldSetMapper / RecordFieldSetMapper: maps columns to Customer record     |
+---------------------------------------------------------------------------------+
       | returns Customer(101, "john", "doe", ...)
       v
+---------------------------------------------------------------------------------+
| ItemProcessor<Customer, Customer>                                               |
|   - If email missing or lacks '@': return null (FILTER / DROP RECORD)           |
|   - If valid: Customer(101, "JOHN", "DOE", "john@example.com", "ACTIVE")       |
+---------------------------------------------------------------------------------+
       | (Buffers until chunk size = 2)
       v
+---------------------------------------------------------------------------------+
| JdbcBatchItemWriter<Customer> (PostgreSQL :5001)                                |
|   - Executes single SQL Batch PreparedStatement:                                |
|     INSERT INTO customers VALUES (?, ?, ?, ?, ?)                                |
|   - HikariCP commits transaction for all items in the chunk                     |
+---------------------------------------------------------------------------------+
```

### Database Streaming: Cursor vs. Paging Readers

```
1. JdbcCursorItemReader (Single Connection, Streaming Cursor):
   App <================= [Open Cursor / Single JDBC Connection] =================> DB
   Row 1 -> Row 2 -> Row 3 -> ... (Fast, minimal memory, NOT thread-safe)

2. JdbcPagingItemReader (Independent Page Queries):
   App --- SELECT ... WHERE customer_id > 0 ORDER BY customer_id ASC LIMIT 2 ---> DB (Page 1)
   App --- SELECT ... WHERE customer_id > 102 ORDER BY customer_id ASC LIMIT 2 -> DB (Page 2)
   (Releases connection between pages, THREAD-SAFE for concurrent steps)
```

---

## 3. Minimal Runnable Code (Spring Batch 6 / Boot 4)

### Domain Model: [`Customer.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/model/Customer.java)
```java
package com.chauhan.batchservice.model;

public record Customer(
    Long customerId,
    String firstName,
    String lastName,
    String email,
    String status
) {}
```

### Sample CSV: [`customers.csv`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/resources/data/customers.csv)
```csv
customerId,firstName,lastName,email,status
101,john,doe,john.doe@example.com,ACTIVE
102,jane,smith,jane.smith@example.com,ACTIVE
103,bob,johnson,invalid-email-format,INACTIVE
104,alice,williams,alice.williams@example.com,ACTIVE
105,charlie,brown,,PENDING
106,emma,davis,emma.davis@example.com,ACTIVE
```

### Job Configuration: [`Module03CsvToDatabaseJobConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/config/Module03CsvToDatabaseJobConfig.java)
```java
package com.chauhan.batchservice.config;

import com.chauhan.batchservice.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.RecordFieldSetMapper;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class Module03CsvToDatabaseJobConfig {

    private static final Logger log = LoggerFactory.getLogger(Module03CsvToDatabaseJobConfig.class);
    public static final int CHUNK_SIZE = 2;

    @Bean
    public Job csvToDatabaseJob(JobRepository jobRepository,
                                Step truncateCustomerTableStep,
                                Step loadCustomersChunkStep) {
        return new JobBuilder("csvToDatabaseJob", jobRepository)
                .start(truncateCustomerTableStep)
                .next(loadCustomersChunkStep)
                .build();
    }

    @Bean
    public Step truncateCustomerTableStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          JdbcTemplate jdbcTemplate) {
        return new StepBuilder("truncateCustomerTableStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Tasklet] Truncating 'customers' table in PostgreSQL...");
                    jdbcTemplate.execute("TRUNCATE TABLE customers");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step loadCustomersChunkStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       FlatFileItemReader<Customer> customerCsvReader,
                                       ItemProcessor<Customer, Customer> customerProcessor,
                                       JdbcBatchItemWriter<Customer> customerJdbcWriter) {
        return new StepBuilder("loadCustomersChunkStep", jobRepository)
                .<Customer, Customer>chunk(CHUNK_SIZE, transactionManager)
                .reader(customerCsvReader)
                .processor(customerProcessor)
                .writer(customerJdbcWriter)
                .build();
    }

    @Bean
    public FlatFileItemReader<Customer> customerCsvReader() {
        return new FlatFileItemReaderBuilder<Customer>()
                .name("customerCsvReader")
                .resource(new ClassPathResource("data/customers.csv"))
                .linesToSkip(1) // Skip CSV header
                .delimited()
                .names("customerId", "firstName", "lastName", "email", "status")
                .fieldSetMapper(new RecordFieldSetMapper<>(Customer.class))
                .build();
    }

    @Bean
    public ItemProcessor<Customer, Customer> customerProcessor() {
        return customer -> {
            if (customer.email() == null || customer.email().isBlank() || !customer.email().contains("@")) {
                log.warn("[Processor] Dropping invalid customer ID {}: invalid email '{}'",
                        customer.customerId(), customer.email());
                return null; // returning null filters out invalid records
            }

            return new Customer(
                    customer.customerId(),
                    customer.firstName().toUpperCase(),
                    customer.lastName().toUpperCase(),
                    customer.email().toLowerCase().trim(),
                    customer.status()
            );
        };
    }

    @Bean
    public JdbcBatchItemWriter<Customer> customerJdbcWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Customer>()
                .dataSource(dataSource)
                .sql("INSERT INTO customers (customer_id, first_name, last_name, email, status) " +
                     "VALUES (:customerId, :firstName, :lastName, :email, :status)")
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    @Bean
    public JdbcPagingItemReader<Customer> customerPagingReader(DataSource dataSource) throws Exception {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("SELECT customer_id, first_name, last_name, email, status");
        queryProvider.setFromClause("FROM customers");
        queryProvider.setSortKeys(Map.of("customer_id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<Customer>()
                .name("customerPagingReader")
                .dataSource(dataSource)
                .pageSize(CHUNK_SIZE)
                .queryProvider(queryProvider)
                .rowMapper((rs, rowNum) -> new Customer(
                        rs.getLong("customer_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("status")
                ))
                .build();
    }
}
```

### Verification Test: [`Module03EnterpriseReadersWritersTests.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/test/java/com/chauhan/batchservice/Module03EnterpriseReadersWritersTests.java)

Run all verification tests from the terminal:
```bash
./mvnw test -Dtest=Module03EnterpriseReadersWritersTests
```

---

## 4. Common Mistakes & Version Gotchas (Batch 4 vs 5 vs 6)

### Gotcha 1: `JdbcCursorItemReader` is NOT Thread-Safe!
- `JdbcCursorItemReader` holds an open JDBC `ResultSet` tied to a single database connection.
- If you use `JdbcCursorItemReader` in a multi-threaded step (`taskExecutor`), multiple threads will read from the same `ResultSet` simultaneously, causing race conditions, skipped records, and SQL cursor errors.
- **Rule of Thumb**:
  - Single-threaded steps $\rightarrow$ `JdbcCursorItemReader` (fastest, lowest memory).
  - Multi-threaded or partitioned steps $\rightarrow$ `JdbcPagingItemReader` (mandatory).

### Gotcha 2: The Eager Multiple Job Startup Failure
- When you have more than one `Job` bean in the application context (`helloWorldJob`, `chunkBasicsJob`, `csvToDatabaseJob`), Spring Boot's `JobLauncherApplicationRunner` tries to run jobs on application startup.
- If multiple jobs exist and `spring.batch.job.name` is not set, Spring Boot throws:
  `IllegalStateException: Job name must be specified in case of multiple jobs`.
- **Solution**: Explicitly disable automatic eager execution in `application.yml`:
  ```yaml
  spring:
    batch:
      job:
        enabled: false
  ```

### Gotcha 3: The `ItemStream` Contract and Restartability
- Many readers (like `FlatFileItemReader` and `JdbcCursorItemReader`) implement the `ItemStream` interface:
  ```java
  public interface ItemStream {
      void open(ExecutionContext executionContext);
      void update(ExecutionContext executionContext);
      void close();
  }
  ```
- At every chunk commit, Spring Batch calls `update()` to save the reader's current record position into `BATCH_STEP_EXECUTION_CONTEXT`.
- **Trap**: If you define an `ItemReader` as an inline anonymous object or lambda inside your Step configuration without registering it as a Spring `@Bean` or with `.stream(myReader)`, Spring Batch will not recognize it as an `ItemStream`. If the job fails, it cannot resume from the saved offset and will restart from line 1!

### Gotcha 4: Package Relocation in Spring Batch 6
- In Batch 4 and 5, readers and writers were in `org.springframework.batch.item.*`.
- In **Spring Batch 6**, all item infrastructure is located in:
  - `org.springframework.batch.infrastructure.item.file.*`
  - `org.springframework.batch.infrastructure.item.database.*`
  - `org.springframework.batch.infrastructure.item.support.*`

---

## 5. Hands-on Exercises

### Exercise 1 (Basic — PostgreSQL Query Verification)
1. Run the test:
   ```bash
   ./mvnw test -Dtest=Module03EnterpriseReadersWritersTests
   ```
2. Query the PostgreSQL database to inspect the written customers and step telemetry:
   ```bash
   docker exec -it core-auth-service-db psql -U admin_user -d batch_db -c \
   "SELECT customer_id, first_name, last_name, email, status FROM customers ORDER BY customer_id;"
   
   docker exec -it core-auth-service-db psql -U admin_user -d batch_db -c \
   "SELECT step_name, read_count, filter_count, write_count, commit_count FROM batch_step_execution WHERE step_name = 'loadCustomersChunkStep' ORDER BY step_execution_id DESC LIMIT 1;"
   ```
3. Confirm that records 103 and 105 were filtered out, and the 4 remaining records have uppercase names.

### Exercise 2 (Intermediate — CompositeItemWriter)
1. In `Module03CsvToDatabaseJobConfig.java`, create a second `ItemWriter<Customer>` (e.g., `FlatFileItemWriter` or a logging audit writer):
   ```java
   ItemWriter<Customer> auditLogWriter = chunk -> {
       log.info("[AUDIT LOG] Written {} records to audit ledger", chunk.size());
   };
   ```
2. Chain the database writer and audit writer together using `CompositeItemWriterBuilder`:
   ```java
   @Bean
   public CompositeItemWriter<Customer> compositeCustomerWriter(
           JdbcBatchItemWriter<Customer> dbWriter,
           ItemWriter<Customer> auditLogWriter) {
       return new CompositeItemWriterBuilder<Customer>()
               .delegates(List.of(dbWriter, auditLogWriter))
               .build();
   }
   ```
3. Use the composite writer in your step and verify that both delegates receive the chunk inside the same transaction.

### Exercise 3 (Advanced — Why Sort Keys are Mandatory in Paging Readers)
1. In `Module03CsvToDatabaseJobConfig.java`, examine `customerPagingReader`.
2. Notice:
   ```java
   queryProvider.setSortKeys(Map.of("customer_id", Order.ASCENDING));
   ```
3. Why are sort keys strictly mandatory in `JdbcPagingItemReader`?
   - Try removing the sort keys or passing an empty map. Observe the initialization exception thrown by Spring Batch.
   - Paging readers generate deterministic keyset / `WHERE (id > ?)` clauses across page boundaries; without an unambiguous sort order, pages would overlap or skip rows during concurrency.

---

## 6. Interview Questions & Model Answers

### Q1: When should you use `JdbcCursorItemReader` vs `JdbcPagingItemReader`?
> **Answer**:
> - Use **`JdbcCursorItemReader`** for single-threaded steps with large datasets where you want maximum throughput and minimal overhead. It keeps one JDBC connection and streams the cursor forward. However, it is **not thread-safe**.
> - Use **`JdbcPagingItemReader`** whenever the step is multi-threaded or partitioned across multiple worker threads. Each thread can independently query a dedicated page of data without sharing state or holding open long-lived database cursors.

### Q2: What is the `ItemStream` interface, and what happens if a stateful reader doesn't implement it?
> **Answer**: `ItemStream` provides lifecycle hooks (`open`, `update`, `close`) that allow a reader or writer to store and restore execution state. At every chunk commit, Spring Batch calls `update()` to save the reader's current record index into `BATCH_STEP_EXECUTION_CONTEXT`. If a stateful reader does not implement `ItemStream` (or isn't registered via `.stream()`), Spring Batch cannot save its position; upon a restart after a crash, the job will re-read from record 1 instead of resuming from the failure point.

### Q3: How does `CompositeItemWriter` behave during a transaction rollback?
> **Answer**: `CompositeItemWriter` invokes its delegates sequentially (`writer1`, `writer2`, etc.) within the outer Spring Batch chunk transaction. If `writer2` throws an exception, the entire transaction rolls back. All operations performed by `writer1` within that chunk are rolled back (assuming both resources participate in the transaction or the database transaction is aborted), maintaining atomic consistency across the composite write.

---

## 7. Module 3 Comprehension Quiz

Test your understanding of Module 3:

1. **Reader EOF**: What return value from `ItemReader.read()` informs Spring Batch that the input source is exhausted?
2. **Cursor Thread-Safety**: Why is `JdbcCursorItemReader` dangerous to use in a multi-threaded step?
3. **Paging Requirement**: Why does `JdbcPagingItemReader` strictly require a unique sort key in its `PagingQueryProvider`?
4. **Composite Writer**: You want to write processed records into a PostgreSQL database table AND publish them to a Kafka topic in the same step. Which Spring Batch writer pattern facilitates this?
5. **Multiple Jobs Boot Error**: Why does Spring Boot fail on startup when multiple `Job` beans are present in the application context if `spring.batch.job.enabled=true`?

---

### Quiz Answer Key (For Review)

<details>
<summary>Click to view answers after attempting the quiz</summary>

1. **Answer**: Returning `null` signals End Of File (EOF).
2. **Answer**: It is not thread-safe because it shares a single stateful JDBC `ResultSet` and open database cursor across multiple threads, leading to race conditions and corrupted cursors.
3. **Answer**: Paging queries rely on deterministic pagination (`LIMIT / OFFSET` or keyset pagination `WHERE id > ?`). Without a unique sort order, the database engine cannot guarantee row order across independent page queries, resulting in duplicated or skipped rows.
4. **Answer**: `CompositeItemWriter` (chaining `JdbcBatchItemWriter` and `KafkaItemWriter`).
5. **Answer**: `JobLauncherApplicationRunner` tries to automatically run all discovered jobs on boot; when multiple jobs exist, it cannot determine which one to execute without `spring.batch.job.name` or setting `spring.batch.job.enabled=false`.
</details>
