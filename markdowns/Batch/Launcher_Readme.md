# Spring Batch On-Demand Job Launcher Guide

This document explains the architecture, design, and practical usage of the on-demand Job Launcher built for the `batch-service`. It enables engineers and operators to launch batch jobs at will via **Command Line (CLI)** or **REST API**, without eager automatic execution on application startup.

---

## 1. Why an On-Demand Launcher?

In a multi-job Spring Batch application:
1. **Startup Conflict**: When multiple `Job` beans exist in the Spring `ApplicationContext`, Spring Boot's default eager runner (`JobLauncherApplicationRunner`) halts application boot with:
   ```text
   java.lang.IllegalStateException: Job name must be specified in case of multiple jobs
   ```
2. **Production Safety**: In production or development environments, we rarely want all jobs to execute indiscriminately whenever the container boots or restarts. We disable auto-execution in [`application.yml`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/resources/application.yml):
   ```yaml
   spring:
     batch:
       job:
         enabled: false
   ```
3. **Operational Flexibility**: With auto-execution disabled, we need first-class mechanisms to:
   - Run a single job for local testing or debugging via CLI (`--job=<jobName>`).
   - Run jobs via Kubernetes CronJobs or bash scripts.
   - Trigger jobs remotely via webhooks, microservice orchestrators, or admin consoles via REST.

---

## 2. Architecture & File Structure

The launcher is designed following standard layered enterprise architecture:

```text
batch-service/src/main/java/com/chauhan/batchservice/
├── BatchServiceApplication.java           # Standard Spring Boot application entry point
├── config/                                 # Job definition beans
│   ├── Module01HelloWorldJobConfig.java   # helloWorldJob
│   ├── Module02ChunkBasicsJobConfig.java  # chunkBasicsJob
│   └── Module03CsvToDatabaseJobConfig.java# csvToDatabaseJob
├── controller/                             # REST API web layer
│   └── JobLauncherController.java          # Endpoints for job catalog and triggering
├── dto/                                    # Immutable Java Records for HTTP payloads
│   ├── JobExecutionResponse.java           # Job execution metadata and telemetry
│   └── JobListResponse.java                # List of discovered batch jobs
├── model/                                  # Domain models
│   └── Customer.java                       # Customer record
├── runner/                                 # Command Line Runner
│   └── JobLauncherCommandLineRunner.java   # CLI argument parser (--job=<name>)
└── service/                                # Orchestration and business service
    └── JobExecutionService.java            # Job registry lookup, parameter assembly, launch
```

### Component Interaction Flow

```
[CLI: --job=xyz]               [HTTP: POST /api/jobs/xyz]
       │                                     │
       ▼                                     ▼
JobLauncherCommandLineRunner           JobLauncherController
       │                                     │
       └──────────────┬──────────────────────┘
                      │
                      ▼
             JobExecutionService
                      │
        ┌─────────────┴─────────────┐
        ▼                           ▼
ApplicationContext             JobParametersBuilder
(Find Job bean by name)        (Add run.timestamp + params)
        │                           │
        └─────────────┬─────────────┘
                      │
                      ▼
                 JobLauncher
                      │
                      ▼
                JobRepository ──────► PostgreSQL (BATCH_* Tables)
```

---

## 3. How to Launch Jobs via CLI

When running from the terminal, supply `--job=<jobName>` (or `job=<jobName>`) to trigger that job immediately on boot.

### Examples using Maven Wrapper:

```bash
# 1. Launch Module 1's Hello World Tasklet Job
./mvnw spring-boot:run -Dspring-boot.run.arguments="--job=helloWorldJob"

# 2. Launch Module 2's Chunk Processing Job
./mvnw spring-boot:run -Dspring-boot.run.arguments="--job=chunkBasicsJob"

# 3. Launch Module 3's CSV to PostgreSQL Job
./mvnw spring-boot:run -Dspring-boot.run.arguments="--job=csvToDatabaseJob"
```

### Examples using Packaged JAR:

```bash
# Build the JAR
./mvnw clean package -DskipTests

# Run any job directly
java -jar target/batch-service-0.0.1-SNAPSHOT.jar --job=csvToDatabaseJob
```

### Idle Mode (No Arguments):
If you start the service without `--job`, it starts up normally on port `8086`, logs the registered jobs, and waits for REST API calls:
```bash
./mvnw spring-boot:run
```
Console output:
```text
===============================================================
BATCH SERVICE READY on Port 8086
Discovered Batch Jobs: [helloWorldJob, chunkBasicsJob, csvToDatabaseJob]
To trigger a job via CLI:
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="--job=<jobName>"
To trigger a job via REST API:
  curl -X POST http://localhost:8086/api/jobs/<jobName>
===============================================================
```

---

## 4. How to Launch Jobs via REST API

The application exposes endpoints on port `8086` under `/api/jobs`.

### 4.1. List Available Jobs
**Endpoint:** `GET /api/jobs`  
**Description:** Inspects the Spring `ApplicationContext` and returns all registered batch job bean names.

```bash
curl -X GET http://localhost:8086/api/jobs
```

**Response (HTTP 200 OK):**
```json
{
  "totalJobs": 3,
  "availableJobs": [
    "helloWorldJob",
    "chunkBasicsJob",
    "csvToDatabaseJob"
  ]
}
```

---

### 4.2. Trigger a Job (Default Parameters)
**Endpoint:** `POST /api/jobs/{jobName}`  
**Description:** Executes the specified job synchronously and returns complete execution telemetry.

```bash
curl -X POST http://localhost:8086/api/jobs/csvToDatabaseJob
```

**Response (HTTP 200 OK):**
```json
{
  "jobName": "csvToDatabaseJob",
  "jobExecutionId": 12,
  "jobInstanceId": 12,
  "status": "COMPLETED",
  "exitCode": "COMPLETED",
  "startTime": "2026-10-05T17:52:50.312",
  "endTime": "2026-10-05T17:52:50.408",
  "parameters": {
    "run.timestamp": 1791202970312
  },
  "errorMessage": null
}
```

---

### 4.3. Trigger a Job with Custom Parameters
Pass query parameters in the URL; they will be captured and passed into the job as non-identifying parameters.

```bash
curl -X POST "http://localhost:8086/api/jobs/csvToDatabaseJob?source=partnerA&batchId=42"
```

**Response (HTTP 200 OK):**
```json
{
  "jobName": "csvToDatabaseJob",
  "jobExecutionId": 13,
  "jobInstanceId": 13,
  "status": "COMPLETED",
  "exitCode": "COMPLETED",
  "startTime": "2026-10-05T17:55:01.100",
  "endTime": "2026-10-05T17:55:01.215",
  "parameters": {
    "run.timestamp": 1791203101100,
    "source": "partnerA",
    "batchId": "42"
  },
  "errorMessage": null
}
```

---

### 4.4. Error Handling: Job Not Found
If an unregistered job name is requested:

```bash
curl -X POST http://localhost:8086/api/jobs/unknownJob
```

**Response (HTTP 404 NOT FOUND):**
```json
{
  "jobName": "unknownJob",
  "jobExecutionId": null,
  "jobInstanceId": null,
  "status": "FAILED",
  "exitCode": "FAILED",
  "startTime": null,
  "endTime": null,
  "parameters": null,
  "errorMessage": "Job 'unknownJob' not found! Available jobs: [helloWorldJob, chunkBasicsJob, csvToDatabaseJob]"
}
```

---

## 5. Spring Batch 6 Architectural Details

Under the hood in Spring Batch 6 / Spring Boot 4:

1. **JobParameters is a Java Record**:
   In Spring Batch 6, `JobParameters` and `JobParameter<T>` are native Java Records:
   ```java
   public final class JobParameters extends Record implements Iterable<JobParameter<?>>
   public final class JobParameter<T> extends Record
   ```
   Values are retrieved via accessor methods `p.name()`, `p.value()`, and `p.identifying()`.

2. **Ensuring Unique Executions**:
   To prevent `JobInstanceAlreadyCompleteException` when running the same job multiple times on demand, `JobExecutionService` injects a unique timestamp parameter:
   ```java
   paramsBuilder.addLong("run.timestamp", System.currentTimeMillis());
   ```

3. **Identifying vs Non-Identifying Parameters**:
   Parameters passed from query strings are added with `identifying = false`:
   ```java
   paramsBuilder.addString(key, value, false);
   ```
   This ensures metadata parameters (like tracking tags or audit strings) do not unintentionally alter job identity in the `BATCH_JOB_INSTANCE` table.

---

## 6. Testing

The REST launcher is covered by automated integration tests in [`JobLauncherControllerTests.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/test/java/com/chauhan/batchservice/JobLauncherControllerTests.java):

```bash
./mvnw test -Dtest=JobLauncherControllerTests
```

Tested scenarios:
1. `GET /api/jobs`: Verifies HTTP 200 and checks that all registered job beans (`helloWorldJob`, `chunkBasicsJob`, `csvToDatabaseJob`) are reported.
2. `POST /api/jobs/helloWorldJob`: Verifies successful execution, returns status `COMPLETED`, and validates execution and instance IDs.
3. `POST /api/jobs/nonExistentJob`: Verifies HTTP 404 response with structured error details.
