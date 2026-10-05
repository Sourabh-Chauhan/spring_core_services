# Spring Batch 6 & Spring Boot 4: Complete Learning & Implementation Plan

> **Target Version**: Spring Boot `4.1.1` | Spring Batch `6.0.5` | Java `17+` (Runtime: OpenJDK `25`)  
> **Target Module**: [`batch-service`](../../batch-service)  
> **Source Roadmap**: Derived from [`batch_plan.md`](batch_plan.md)  
> **Database**: PostgreSQL (Running on `localhost:5001`, user `admin_user`, password `password`)  
> **Configuration Format**: YAML (`application.yml`)  
> **Target Audience**: Experienced Java/Spring Boot developer mastering Spring Batch from scratch to production-ready enterprise grade.

---

## 1. Architectural Overview & Version Migration Baseline

### The Spring Batch 6 / Boot 4 Modern Paradigm

Spring Batch 5 & 6 represent a fundamental architectural redesign compared to legacy Spring Batch 4.x / Boot 2.x. All code developed in this service strictly conforms to modern standards.

```
+-----------------------------------------------------------------------------------+
|                                  Spring Boot 4                                    |
|                                                                                   |
|  +------------------------+                        +---------------------------+  |
|  | BatchAutoConfiguration |                        | PlatformTransactionManager|  |
|  +-----------+------------+                        +-------------+-------------+  |
|              | (provides)                                        | (provides)     |
|              v                                                   v                |
|  +-----------+------------+                        +-------------+-------------+  |
|  |     JobRepository      |                        |       StepBuilder         |  |
|  +-----------+------------+                        | (tasklet / chunk / txMgr) |  |
|              |                                     +-------------+-------------+  |
|              +--------------------+                              |                |
|                                   v                              v                |
|                        +----------+----------+        +----------+----------+     |
|                        |     JobBuilder      |        |        Step         |     |
|                        | (new JobBuilder(..))|=======>| (Tasklet or Chunk)  |     |
|                        +---------------------+        +---------------------+     |
+-----------------------------------------------------------------------------------+
```

### Critical API Differences: Legacy (Batch 4) vs Modern (Batch 5 & 6)

| Concept / Feature            | Legacy (Spring Batch 4 / Boot 2)                            | Modern (Spring Batch 5 & 6 / Boot 3 & 4)                                                                                                        |
|:-----------------------------|:------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------|
| **Builder Factories**        | `JobBuilderFactory`, `StepBuilderFactory` injected as beans | **Completely Removed**. Instantiate builders directly: `new JobBuilder("jobName", jobRepository)`, `new StepBuilder("stepName", jobRepository)` |
| **Configuration Annotation** | `@EnableBatchProcessing` required                           | **Do NOT use** `@EnableBatchProcessing`. Using it turns OFF Spring Boot's auto-configuration, schema setup, and transaction manager exposure.   |
| **Transaction Management**   | Inferred implicitly by `StepBuilderFactory`                 | Must be explicitly supplied to `.tasklet(tasklet, transactionManager)` or `.chunk(size, transactionManager)`                                    |
| **Java Baseline**            | Java 8+                                                     | Java 17+ baseline (Spring Framework 7 / Spring Boot 4)                                                                                          |
| **Job Parameters**           | Standard untyped map                                        | Typed parameters (`JobParameter<T>`), explicit non-identifying flags via `JobParametersBuilder`                                                 |
| **Observation & Metrics**    | Dropwizard / legacy Micrometer binders                      | Native `io.micrometer.observation.ObservationRegistry` integration                                                                              |

---

## 2. Infrastructure & `batch-service` Setup Plan

To inspect the 6 core metadata tables hands-on, `batch-service` connects directly to the local PostgreSQL instance configured across the microservices ecosystem (`auth-service` and `ai-service`).

### 2.1 Database Credentials (Extracted from [`auth-service`](../../auth-service/src/main/resources/application-dev.yml))

- **Host & Port**: `localhost:5001` (Docker container: `core-auth-service-db`)
- **Database**: `batch_db` (or `auth_db`)
- **Username**: `admin_user`
- **Password**: `password`
- **Driver**: `org.postgresql.Driver`

### 2.2 Dependencies Update (`../../batch-service/pom.xml`)

Add PostgreSQL driver, Spring Boot JDBC Starter, Web MVC (for REST triggering and actuator), and Lombok:

```xml
<dependencies>
    <!-- Core Spring Batch Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>

    <!-- JDBC & HikariCP Connection Pool -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <!-- PostgreSQL Driver (Shared with auth-service) -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Web Starter for REST Job Triggering & Endpoints -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Testing Utilities -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 2.3 Environment Configuration (`../../batch-service/src/main/resources/application.yml`)

```yaml
server:
  port: 8086

spring:
  application:
    name: batch-service

  # -------------------------------------------------------------
  # PostgreSQL Database Configuration (Port 5001)
  # -------------------------------------------------------------
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5001}/${POSTGRES_DB:batch_db}
    username: ${POSTGRES_USER:admin_user}
    password: ${POSTGRES_PASSWORD:password}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      pool-name: BatchHikariPool

  # -------------------------------------------------------------
  # Spring Batch Schema Auto-Initialization for PostgreSQL
  # -------------------------------------------------------------
  sql:
    init:
      mode: always
      schema-locations: classpath:org/springframework/batch/core/schema-postgresql.sql
      continue-on-error: true

  batch:
    job:
      # Disable eager auto-run on boot so jobs are triggered explicitly in tests/APIs
      enabled: false

logging:
  level:
    org.springframework.batch: INFO
    org.springframework.jdbc: DEBUG
```

> [!NOTE]
> In Spring Boot 4, `spring.sql.init` is used with `schema-locations: classpath:org/springframework/batch/core/schema-postgresql.sql` and `continue-on-error: true`. It executes the bundled DDL on application startup, creating all 6 `BATCH_*` metadata tables and their sequences (`BATCH_JOB_SEQ`, `BATCH_JOB_EXECUTION_SEQ`, `BATCH_STEP_EXECUTION_SEQ`) in PostgreSQL.

---

## 3. The 4-Week Step-by-Step Curriculum

Every topic follows the strict 6-point teaching format:
1. **Concept in Plain Language** (<= 150 words) with a "Why it exists" rationale.
2. **Mental Model & ASCII Diagram**.
3. **Minimal Runnable Code** (modern Spring Batch 6 / Boot 4).
4. **Common Mistakes & Version Gotchas**.
5. **3 Hands-on Exercises** (graduated difficulty: Basic $\rightarrow$ Intermediate $\rightarrow$ Advanced).
6. **3 Interview-Style Questions & Model Answers**.
7. **5-Question Interactive Quiz** (must score 100% before advancing to the next module).

---

### Week 1: Core Domain Model, Metadata Tables & Chunk Basics

```
                                      +------------------------------------+
                                      |          JobLauncher               |
                                      +-----------------+------------------+
                                                        | launches
                                                        v
+-------------------+                 +-----------------+------------------+
|    JobInstance    |<----------------|            JobExecution            |
| (Name + Ident. P) |  1 : N runs     |  (Status, ExitCode, Start/End Time)|
+-------------------+                 +-----------------+------------------+
                                                        | contains
                                                        v
                                      +-----------------+------------------+
                                      |            StepExecution           |
                                      |  (Read/Write/Commit/Skip counts)   |
                                      +-----------------+------------------+
                                                        | stores state in
                                                        v
                                      +-----------------+------------------+
                                      |          ExecutionContext          |
                                      |       (Persistent Key-Value)       |
                                      +------------------------------------+
```

#### Module 1: The Core Model & Metadata Architecture
- **Topics**:
  - `Job`, `Step`, `JobInstance`, `JobExecution`, `StepExecution`.
  - Identity rule: `JobInstance = Job Name + Identifying JobParameters`.
  - The 6 metadata tables in PostgreSQL:
    - `BATCH_JOB_INSTANCE`: Immutable record of unique job runs.
    - `BATCH_JOB_EXECUTION`: Individual attempt of an instance (status: `STARTED`, `COMPLETED`, `FAILED`).
    - `BATCH_JOB_EXECUTION_PARAMS`: Key-value parameters passed to the run.
    - `BATCH_STEP_EXECUTION`: Per-step telemetry (commit count, read count, filter count, write count).
    - `BATCH_STEP_EXECUTION_CONTEXT`: Persistent execution context for the step.
    - `BATCH_JOB_EXECUTION_CONTEXT`: Persistent execution context for the entire job.
  - `JobRepository` & `JobLauncher`.
- **Deliverables in `batch-service`**:
  - `Module01HelloWorldJobConfig.java`: Tasklet step printing greeting and injecting state into `ExecutionContext`.
  - Direct SQL inspection script/test querying `BATCH_JOB_INSTANCE` and `BATCH_JOB_EXECUTION` in PostgreSQL.
- **Quiz**: 5-question checkpoint on identity, execution state, and table schemas.

#### Module 2: Tasklet vs. Chunk-Oriented Processing
- **Topics**:
  - **Tasklet**: Single execute-and-return method (`RepeatStatus.FINISHED`). Ideal for cleanup, table truncation, file moving, stored procedure calls.
  - **Chunk-Oriented**: Dedicated 3-phase pipeline (`ItemReader<I> -> ItemProcessor<I, O> -> ItemWriter<O>`).
  - **Commit Interval**: Transaction boundaries, how chunks batch writes and manage rollback boundaries.
- **Mental Model**:
  ```
  ItemReader --(reads 1 item at a time)--> List of items (chunkSize = 10)
  ItemProcessor --(processes 1 item at a time)--> List of transformed items
  ItemWriter --(writes entire chunk of 10 items in 1 transaction commit)--> DB / Output
  ```
- **Deliverables**:
  - `Module02ChunkBasicsConfig.java`: In-memory list reader, uppercase processor, logging writer with chunk size 5.
  - Comparative analysis of rollback behavior.
- **Quiz**: 5-question checkpoint on transaction boundaries, null filtering in processor, and chunk sizing.

---

### Week 2: Readers, Writers, Flow Control & Restartability

#### Module 3: Enterprise ItemReaders & ItemWriters
- **Topics**:
  - File reading: `FlatFileItemReader` (DelimitedLineTokenizer, FieldSetMapper).
  - Database streaming: `JdbcCursorItemReader` (low memory, single connection, NOT thread-safe) vs. `JdbcPagingItemReader` (page-by-page queries, thread-safe for multi-threading).
  - Writing: `JdbcBatchItemWriter` (`namedParameters`, `ItemSqlParameterSourceProvider`), `FlatFileItemWriter`.
  - Composite pattern: `CompositeItemWriter` to write to both DB and audit log in one step.
- **Deliverables**:
  - `Module03CsvToDatabaseJobConfig.java`: Parse CSV data and batch insert into PostgreSQL customer table.
- **Quiz**: 5-question checkpoint on cursor vs paging, thread-safety, and composite writers.

#### Module 4: Job Parameters, Incrementers & Restart Semantics
- **Topics**:
  - Identifying vs. Non-identifying parameters (`new JobParameter<>(val, Type.class, identifying)`).
  - `JobParametersIncrementer` (`RunIdIncrementer`).
  - Restartability rules:
    - If `JobExecution` ended in `COMPLETED`: Rerunning with the exact same identifying parameters throws `JobInstanceAlreadyCompleteException`.
    - If `JobExecution` ended in `FAILED`: Rerunning with the same parameters resumes the existing `JobInstance` from the failed step!
  - `ItemStream` interface: `open()`, `update()`, `close()` and how reader state is saved to `BATCH_STEP_EXECUTION_CONTEXT`.
- **Deliverables**:
  - Deliberately failed job at record 50 out of 100, restarted to verify that records 1-50 are skipped and execution resumes at record 51.
- **Quiz**: 5-question checkpoint on restartability, `ItemStream`, and parameter identity.

#### Module 5: Flow Control, Decisions & Listeners
- **Topics**:
  - Sequential flow: `.start(s1).next(s2).next(s3)`.
  - Conditional flow: `.from(s1).on("FAILED").to(alertStep).from(s1).on("*").to(s2).end()`.
  - Programmatic decisions: `JobExecutionDecider` returning custom `FlowExecutionStatus`.
  - Parallel flows: `FlowBuilder.split(taskExecutor).add(flow1, flow2)`.
  - Lifecycle Listeners: `JobExecutionListener`, `StepExecutionListener`, `ChunkListener`, `ItemReadListener`, `ItemWriteListener`.
- **Deliverables**:
  - Multi-step pipeline with dynamic conditional routing and audit listeners measuring chunk execution durations.
- **Quiz**: 5-question checkpoint on wildcards in `.on()`, deciders vs exit status, and listener error handling.

---

### Week 3: Fault Tolerance & High-Performance Scaling

#### Module 6: Fault Tolerance (Skip, Retry & Rollback Rules)
- **Topics**:
  - Enabling fault tolerance: `.faultTolerant()`.
  - Skip logic: `.skip(DataValidationException.class).skipLimit(10)`.
  - Retry logic: `.retry(TransientDataAccessException.class).retryLimit(3)`.
  - Rollback control: `.noRollback(IgnorableWarningException.class)`.
  - Listeners: `SkipListener` (logging corrupted rows to a dead-letter table in PostgreSQL or reject file).
  - Chunk retry mechanics: What happens when an item throws an exception in writer (the chunk rolls back and is retried item-by-item!).
- **Mental Model**:
  ```
  Chunk of 5 items -> Writer throws on item 3
     |
     v Rollback entire chunk
  Retry Phase:
     Chunk size temporarily reduces to 1:
     Item 1: Read -> Process -> Write (SUCCESS, committed)
     Item 2: Read -> Process -> Write (SUCCESS, committed)
     Item 3: Read -> Process -> Write (FAIL -> SkipListener invoked, recorded)
     Item 4: Read -> Process -> Write (SUCCESS, committed)
     Item 5: Read -> Process -> Write (SUCCESS, committed)
  ```
- **Deliverables**:
  - Corrupted data ingestion pipeline showing automated skip, dead-letter logging, and retry backoff.
- **Quiz**: 5-question checkpoint on chunk retry item-by-item degradation, skip limits, and transaction rollbacks.

#### Module 7: Scaling & Parallel Processing
- **Topics**:
  - Scaling patterns comparison:
    1. **Multi-Threaded Step**: Single step processed by worker thread pool (Reader MUST be thread-safe, e.g. `JdbcPagingItemReader`).
    2. **Parallel Steps**: Independent steps executed concurrently via `TaskExecutor`.
    3. **AsyncProcessor & AsyncWriter**: Decouples item processing to asynchronous futures.
    4. **Partitioning**: Master step divides dataset by partitioner (e.g., ID ranges or file shards); worker steps execute in parallel.
    5. **Remote Chunking**: Concept only (messaging queue distribution).
- **Deliverables**:
  - Local partitioned job splitting a 100,000-record dataset into 4 partitions executed across a thread pool.
- **Quiz**: 5-question checkpoint on reader thread safety, partitioning grid sizes, and execution contexts across partitions.

---

### Week 4: Production Readiness, Operations & Capstone Project

#### Module 8: Production Launching, Monitoring & Testing
- **Topics**:
  - Launching options:
    - REST controller with async `JobLauncher`.
    - Scheduled execution with `@Scheduled` / Quartz.
    - Kubernetes `CronJob` / CLI command line runner.
  - Testing with `@SpringBatchTest`:
    - `JobLauncherTestUtils.launchJob()`, `launchStep()`.
    - `JobRepositoryTestUtils` for metadata cleanup.
  - Production operations:
    - Idempotency & duplicate execution prevention.
    - Purging old batch metadata (`BATCH_*` cleanup SQL scripts in PostgreSQL).
    - Micrometer observations and metrics export.
- **Deliverables**:
  - REST API endpoint (`POST /api/v1/jobs/invoices/start`) with execution tracking, accompanied by full `@SpringBatchTest` suite.
- **Quiz**: 5-question checkpoint on async launchers, metadata cleanup, and unit testing steps in isolation.

---

## 4. The Capstone Project: Enterprise Invoice Processing Engine

At the conclusion of the 4 weeks, we build the production-realistic enterprise capstone using PostgreSQL:

```
[Invoices CSV File]
         |
         v
+-----------------------+
|  Step 1: Ingest &     |  ---> Skip corrupted lines (logged to rejects.csv)
|  Format Validate      |  ---> Multi-threaded or Partitioned
+-----------+-----------+
            | writes to 'staging_invoices' table in PostgreSQL
            v
+-----------------------+
|  Step 2: Business     |  ---> Enrich with Customer Data from DB
|  Enrichment & Calc    |  ---> Calculate tax, discounts, overdue fees
+-----------+-----------+
            | writes to 'processed_invoices' table in PostgreSQL
            v
+-----------------------+
|  Step 3: Financial    |  ---> SQL aggregation by customer & country
|  Aggregation          |  ---> Generates ledger entries
+-----------+-----------+
            |
            v
+-----------------------+
|  Step 4: Report Export|  ---> Writes final Executive Summary CSV/JSON
+-----------------------+
```

---

## 5. Daily Execution Protocol & Learning Rhythm

Following the guidelines in [`batch_plan.md`](batch_plan.md), each daily ~1.5-hour session is structured as follows:

| Allocation     | Activity                   | Focus                                                                                                                                                                                 |
|:---------------|:---------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **20 minutes** | **Concept & Architecture** | Read concise explanation, study ASCII mental model, understand the "Why it exists" and Spring Batch 4 vs 5 vs 6 diffs.                                                                |
| **50 minutes** | **Hands-On Coding**        | Implement the minimal runnable job in `batch-service`, run the tests, and write the 3 graduated exercises.                                                                            |
| **20 minutes** | **Break & Observe**        | Deliberately cause failure (kill process, corrupt data, throw unhandled exceptions) and query PostgreSQL metadata tables via `psql` to observe Spring Batch's internal state machine. |
| **Conclusion** | **Assessment**             | Complete the 5-question comprehension quiz before moving to the subsequent module.                                                                                                    |

---

## 6. Immediate Next Steps

1. Update [`../../batch-service/pom.xml`](../../batch-service/pom.xml) with `postgresql` driver and `spring-boot-starter-jdbc`.
2. Update [`../../batch-service/src/main/resources/application.yml`](../../batch-service/src/main/resources/application.yml) with the PostgreSQL datasource credentials on port 5001.
3. Begin **Module 1: The Core Domain Model & Metadata Tables**.
