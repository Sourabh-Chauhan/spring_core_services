# Module 05: Flow Control (Sequential, Conditional, Deciders, Split Flows, Listeners & Validators)

---

## 1. Concept & "Why It Exists"

### The Concept (<= 150 words)
Real-world enterprise batch jobs rarely execute in a linear pipeline. Spring Batch provides rich flow control structures:
- **Sequential Flows**: Linear execution chaining steps (`start -> next`).
- **Conditional Transitions**: Pattern-matched transitions (`on("FAILED").to(...)`, `on("SPECIAL_*").to(...)`) based on step `ExitStatus`.
- **`JobExecutionDecider`**: Dedicated business decision beans that evaluate job parameters or promoted execution context without embedding routing logic into step tasklets.
- **Split (Parallel) Flows**: Concurrent execution of independent sub-flows using a `TaskExecutor` (`.split(taskExecutor).add(flow1, flow2)`).
- **Listeners & Context Promotion**: The Observer pattern applied to batch lifecycles (`JobExecutionListener`, `StepExecutionListener`). `ExecutionContextPromotionListener` automatically promotes step-level keys to job-level execution context.

### Why It Exists (The Problem Hand-Rolled Frameworks Face)
In custom home-grown bash scripts or procedural Java code:
1. Handling partial failures requires nested `try-catch-finally` blocks and spaghetti `if-else` branching.
2. Running independent tasks (like currency rate fetches and blacklist checks) concurrently requires manually managing thread pools, join barriers, and synchronized state.
3. Steps are tightly coupled: Step A must know what Step B needs, rather than publishing metrics to a managed `ExecutionContext`.
4. Spring Batch isolates concerns: Steps perform work, Deciders make routing choices, Split Flows handle concurrency, and Listeners handle cross-cutting auditing.

---

## 2. ASCII Architectural Diagrams

### A. Complete Master Flow Architecture

```
                  ┌─────────────────┐
                  │  initBatchStep  │
                  └────────┬────────┘
                           │
       ┌───────────────────┼───────────────────┐
       │ FAILED            │ SPECIAL_ROUTE     │ * (Normal)
       ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐    ┌───────────────────────────────────┐
│flowError-     │   │ specialRoute- │    │            splitFlow              │
│HandlerStep    │   │ Step          │    │ (Runs concurrently in background) │
└───────┬───────┘   └───────┬───────┘    │ ┌───────────────┐ ┌─────────────┐ │
        │                   │            │ │fetchExchange- │ │validate-    │ │
        │                   │            │ │RatesStep      │ │BlacklistStep│ │
        │                   │            │ └───────────────┘ └─────────────┘ │
        │                   │            └─────────────────┬─────────────────┘
        │                   │                              │
        │                   │                              ▼
        │                   │                    ┌───────────────────┐
        │                   │                    │PaymentRiskDecider │
        │                   │                    └─────────┬─────────┘
        │                   │                              │
        │                   │            ┌─────────────────┴─────────────────┐
        │                   │            │ AUDIT_REQUIRED                    │ AUTO_APPROVED
        │                   │            ▼                                   ▼
        │                   │    ┌───────────────┐                   ┌───────────────┐
        │                   │    │manualAuditStep│                   │autoApproveStep│
        │                   │    └───────┬───────┘                   └───────┬───────┘
        │                   │            └─────────────────┬─────────────────┘
        │                   │                              │
        │                   ▼                              ▼
        │             ┌──────────────────────────────────────────────┐
        │             │              generateReportStep              │
        │             └──────────────────────┬───────────────────────┘
        ▼                                    ▼
       END                                  END
```

---

### B. ExecutionContext Promotion Lifecycle

```
[initBatchStep ExecutionContext]
  riskScore = 25
  batchName = "PAYMENT-BATCH-V1"
        │
        ▼ (initBatchStep finishes with COMPLETED)
[ExecutionContextPromotionListener.afterStep]
  Promotes specified keys ("riskScore", "batchName")
        │
        ▼
[JobExecution ExecutionContext]
  riskScore = 25
  batchName = "PAYMENT-BATCH-V1"
        │
        ├──────────────────────────────────────┐
        ▼                                      ▼
[PaymentRiskDecider]                   [generateReportStep]
  Reads: jobExecution.getContext()       Reads: chunkContext.getStepContext()
  Evaluates: riskScore >= 50 ?                  .getJobExecutionContext()
```

---

## 3. Minimal Runnable Code

All components are implemented and verified in the repository:

### 3.1. Job & Flow Configuration
Defined in [`Module05FlowControlAndListenersJobConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/config/Module05FlowControlAndListenersJobConfig.java):

```java
@Configuration
public class Module05FlowControlAndListenersJobConfig {

    @Bean
    public Job flowControlMasterJob(JobRepository jobRepository,
                                   AuditLoggingJobListener auditLoggingJobListener,
                                   Step initBatchStep,
                                   Flow splitFlow,
                                   PaymentRiskDecider paymentRiskDecider,
                                   Step manualAuditStep,
                                   Step autoApproveStep,
                                   Step generateReportStep,
                                   Step flowErrorHandlerStep,
                                   Step specialRouteStep) {

        return new JobBuilder("flowControlMasterJob", jobRepository)
                .validator(flowControlJobParametersValidator)
                .listener(auditLoggingJobListener)
                .start(initBatchStep)
                    .on("FAILED").to(flowErrorHandlerStep)
                .from(initBatchStep)
                    .on("SPECIAL_ROUTE").to(specialRouteStep)
                .from(specialRouteStep)
                    .on("*").to(generateReportStep)
                .from(initBatchStep)
                    .on("*").to(splitFlow)
                .from(splitFlow)
                    .on("*").to(paymentRiskDecider)
                    .on(PaymentRiskDecider.STATUS_AUDIT_REQUIRED).to(manualAuditStep)
                .from(paymentRiskDecider)
                    .on(PaymentRiskDecider.STATUS_AUTO_APPROVED).to(autoApproveStep)
                .from(manualAuditStep).on("*").to(generateReportStep)
                .from(autoApproveStep).on("*").to(generateReportStep)
                .from(generateReportStep).end()
                .build();
    }

    @Bean
    public Flow splitFlow(Step fetchExchangeRatesStep, Step validateBlacklistStep) {
        Flow parallelFlow1 = new FlowBuilder<SimpleFlow>("parallelFlow1")
                .start(fetchExchangeRatesStep)
                .build();

        Flow parallelFlow2 = new FlowBuilder<SimpleFlow>("parallelFlow2")
                .start(validateBlacklistStep)
                .build();

        return new FlowBuilder<SimpleFlow>("splitFlow")
                .split(new SimpleAsyncTaskExecutor("batch-split-"))
                .add(parallelFlow1, parallelFlow2)
                .build();
    }

    @Bean
    public ExecutionContextPromotionListener executionContextPromotionListener() {
        ExecutionContextPromotionListener listener = new ExecutionContextPromotionListener();
        listener.setKeys(new String[]{"riskScore", "batchName"});
        listener.setStrict(false);
        return listener;
    }
}
```

---

### 3.2. JobExecutionDecider
Defined in [`PaymentRiskDecider.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/decider/PaymentRiskDecider.java):

```java
@Component
public class PaymentRiskDecider implements JobExecutionDecider {

    public static final String STATUS_AUDIT_REQUIRED = "AUDIT_REQUIRED";
    public static final String STATUS_AUTO_APPROVED = "AUTO_APPROVED";

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        Object riskScoreObj = jobExecution.getExecutionContext().get("riskScore");
        int riskScore = (riskScoreObj instanceof Integer) ? (Integer) riskScoreObj : 0;
        String forceAudit = jobExecution.getJobParameters().getString("force.audit", "false");

        if ("true".equalsIgnoreCase(forceAudit) || riskScore >= 50) {
            return new FlowExecutionStatus(STATUS_AUDIT_REQUIRED);
        }

        return new FlowExecutionStatus(STATUS_AUTO_APPROVED);
    }
}
```

---

### 3.3. Dynamic ExitStatus Override Listener
Defined in [`StepExitStatusOverrideListener.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/listener/StepExitStatusOverrideListener.java):

```java
@Component
public class StepExitStatusOverrideListener implements StepExecutionListener {

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String overrideRoute = stepExecution.getJobParameters().getString("override.route");
        if (overrideRoute != null && !overrideRoute.isBlank()) {
            return new ExitStatus(overrideRoute); // Overrides step exit status!
        }
        return stepExecution.getExitStatus();
    }
}
```

### 3.5. Business Parameters Validator & Composite Chaining
Defined in [`PaymentRiskParametersValidator.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/validator/PaymentRiskParametersValidator.java):

```java
@Component
public class PaymentRiskParametersValidator implements JobParametersValidator {

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        if (parameters == null) {
            return;
        }

        String riskStr = parameters.getString("initial.risk");
        if (riskStr != null && !riskStr.isBlank()) {
            try {
                int risk = Integer.parseInt(riskStr);
                if (risk < 0 || risk > 100) {
                    throw new InvalidJobParametersException(
                            "Job parameter 'initial.risk' must be between 0 and 100, but received: " + risk);
                }
            } catch (NumberFormatException e) {
                throw new InvalidJobParametersException(
                        "Job parameter 'initial.risk' must be an integer, but received: " + riskStr);
            }
        }
    }
}
```

Chained via `CompositeJobParametersValidator` in [`Module05FlowControlAndListenersJobConfig.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/main/java/com/chauhan/batchservice/config/Module05FlowControlAndListenersJobConfig.java):

```java
@Bean
public CompositeJobParametersValidator flowControlJobParametersValidator(
        PaymentRiskParametersValidator riskValidator) throws Exception {
    DefaultJobParametersValidator defaultValidator = new DefaultJobParametersValidator();
    defaultValidator.setOptionalKeys(new String[]{"initial.risk", "force.audit", "override.route", "fail.init", "run.timestamp"});
    defaultValidator.afterPropertiesSet();

    CompositeJobParametersValidator composite = new CompositeJobParametersValidator();
    composite.setValidators(List.of(defaultValidator, riskValidator));
    composite.afterPropertiesSet();
    return composite;
}
```

---

## 4. Critical Gotchas (Spring Batch 4 vs 5 vs 6)

| Feature / Topic | Spring Batch 4 | Spring Batch 5 (Boot 3) | Spring Batch 6 (Boot 4) |
| :--- | :--- | :--- | :--- |
| **`JobExecutionDecider` Package** | `org.springframework.batch.core.job.flow.JobExecutionDecider` with `org.springframework.batch.core.JobExecution` | Same | Method parameters use reorganized packages: `org.springframework.batch.core.job.JobExecution` and `org.springframework.batch.core.step.StepExecution`. |
| **`JobExecutionListener` Package** | `org.springframework.batch.core.JobExecutionListener` | `org.springframework.batch.core.JobExecutionListener` | Moved to **`org.springframework.batch.core.listener.JobExecutionListener`**. |
| **`StepExecutionListener` Package** | `org.springframework.batch.core.StepExecutionListener` | `org.springframework.batch.core.StepExecutionListener` | Moved to **`org.springframework.batch.core.listener.StepExecutionListener`**. |
| **`JobParametersValidator` Exception** | Threw `JobParametersInvalidException` | Threw `JobParametersInvalidException` | Renamed to **`InvalidJobParametersException`**. |
| **`ChunkListener` Signatures** | `beforeChunk(ChunkContext)`, `afterChunk(ChunkContext)` | Same | Added typed chunk callbacks: `beforeChunk(Chunk<I>)`, `afterChunk(Chunk<O>)`, `onChunkError(Exception, Chunk<O>)`. |
| **PostgreSQL SSI / Concurrency in Split Flows** | Failed with `PSQLException: could not serialize access` if default `SERIALIZABLE` was active. | Same | Configure `@EnableJdbcJobRepository(isolationLevelForCreate = Isolation.READ_COMMITTED)` to allow parallel threads to update `BATCH_JOB_EXECUTION` without pivot rollback! |
| **Terminal Flow State** | Omitting terminal transition threw `FlowExecutionException`. | Same | Explicit terminal transition (`.from(step).end()`) is required on leaf steps, or `JobExecutionException: Flow execution ended unexpectedly` is thrown. |

> [!WARNING]
> **BatchStatus vs ExitStatus**: `BatchStatus` is an enum (`COMPLETED`, `FAILED`, `STOPPED`, `STARTED`) tracked by the framework for job control. `ExitStatus` is a user-definable string code (`COMPLETED`, `SPECIAL_ROUTE`, `CUSTOM_ERROR`) used exclusively by the state machine (`on("PATTERN")`). Conditional branching evaluates **`ExitStatus`**, NOT `BatchStatus`!

---

## 5. Graduated Hands-On Exercises

The test suite in [`Module05FlowControlTests.java`](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/batch-service/src/test/java/com/chauhan/batchservice/Module05FlowControlTests.java) verifies all flow control scenarios against PostgreSQL:

### Exercise 1: Standard Auto-Approval with Split Flows (Easy)
- **Objective**: Execute standard path with `initial.risk = 25`.
- **Verification**:
  - `splitFlow` executes `fetchExchangeRatesStep` and `validateBlacklistStep` concurrently.
  - `ExecutionContextPromotionListener` promotes `riskScore` to `JobExecution`.
  - `PaymentRiskDecider` selects `autoApproveStep`.
  - Final report generated. `manualAuditStep` never runs.

### Exercise 2: High Risk Branching via Decider (Medium)
- **Objective**: Execute with `initial.risk = 85`.
- **Verification**:
  - `PaymentRiskDecider` detects `riskScore >= 50` and returns `AUDIT_REQUIRED`.
  - Job branches to `manualAuditStep`.
  - `autoApproveStep` is completely bypassed.

### Exercise 3: Listener ExitStatus Dynamic Override & Error Routing (Hard)
- **Objective**:
  - Test 3A: Pass `override.route = SPECIAL_ROUTE` and verify `StepExitStatusOverrideListener` redirects the job straight to `specialRouteStep`, which then converges back into `generateReportStep` to finalize the run (`[initBatchStep, specialRouteStep, generateReportStep]`).
  - Test 3B: Pass `fail.init = true` and verify `on("FAILED")` captures the step error and routes execution to `flowErrorHandlerStep`.

### Exercise 4: Enterprise Parameter Boundary Validation (Bonus)
- **Objective**: Pass `initial.risk = 150` (outside valid range `[0, 100]`).
- **Verification**:
  - `CompositeJobParametersValidator` runs before step launch.
  - `PaymentRiskParametersValidator` immediately throws `InvalidJobParametersException`.
  - No database transactions or batch steps are started.

---

## 6. Three Enterprise Interview Questions & Answers

### Q1: What is the crucial architectural difference between `BatchStatus` and `ExitStatus`?
**Answer**:
`BatchStatus` is a closed Java enum (`STARTING`, `STARTED`, `STOPPING`, `STOPPED`, `FAILED`, `COMPLETED`, `ABANDONED`) used internally by Spring Batch and the `JobRepository` to track the technical lifecycle of a job or step.
`ExitStatus` is a flexible, user-extensible value object containing a String exit code (e.g. `COMPLETED`, `NOOP`, `AUDIT_REQUIRED`, `FAILED`) and an optional description. Spring Batch flow transitions (`.on("PATTERN").to(...)`) evaluate **`ExitStatus.getExitCode()`**, allowing developers to write custom routing rules without modifying framework status.

### Q2: How does `ExecutionContextPromotionListener` work, and why is it preferred over manually writing to `jobExecution.getExecutionContext()`?
**Answer**:
Inside a Step, a Tasklet or ItemWriter usually has direct access only to the `StepExecution`. If you write to `stepExecution.getJobExecution().getExecutionContext()`, you tightly couple the step code to a job execution environment (making step testing harder and risking concurrent modification in parallel steps).
`ExecutionContextPromotionListener` implements `StepExecutionListener`. Upon successful step completion (`afterStep`), it extracts pre-configured keys from the step's `ExecutionContext` and promotes them into the parent `JobExecution`'s context in a decoupled, declarative manner.

### Q3: Why can running a Split Flow (parallel steps) against PostgreSQL throw `PSQLException: could not serialize access due to read/write dependencies`? How do you solve it?
**Answer**:
By default, Spring Batch's `JobRepository` uses `ISOLATION_SERIALIZABLE` when creating job executions. In PostgreSQL, Serializable Snapshot Isolation (SSI) tracks read/write dependencies across transactions. When two parallel steps in a split flow finish concurrently, both attempt to update the parent `BATCH_JOB_EXECUTION` record, creating overlapping read/write dependencies that PostgreSQL detects as a "pivot" conflict, rolling back one transaction.
The solution is to configure the `JobRepository` with `ISOLATION_READ_COMMITTED` (via `@EnableJdbcJobRepository(isolationLevelForCreate = Isolation.READ_COMMITTED)` in Spring Batch 6), allowing concurrent thread commits without phantom conflict aborts.

---

## 7. Module 5 Comprehension Quiz & Answers

### Q1: What method on `StepExecutionListener` allows you to override a step's exit status for downstream routing?
**Answer**:
`ExitStatus afterStep(StepExecution stepExecution)`.
If this method returns a non-null `ExitStatus`, that value completely overrides the step's original exit status, allowing custom string codes like `SPECIAL_ROUTE` or `NOOP` to steer transition branching.

---

### Q2: What is the purpose of `JobExecutionDecider`, and why is it better than putting routing logic inside a Step Tasklet?
**Answer**:
`JobExecutionDecider` isolates workflow decision logic from business execution logic.
If routing logic is placed inside a step tasklet:
1. The step is polluted with workflow awareness, reducing its reusability.
2. The step must artificially exit with custom statuses just to steer the graph.
`JobExecutionDecider` receives both `JobExecution` and `StepExecution`, evaluates promoted context or parameters cleanly, and returns a `FlowExecutionStatus` without performing I/O or persisting unwanted step executions.

---

### Q3: In a conditional flow, what does wildcard pattern `*` match versus `?`?
**Answer**:
- `*` matches zero or more characters (e.g., `FAILED*` matches `FAILED`, `FAILED_VALIDATION`, `FAILED_IO`).
- `?` matches exactly one character (e.g., `CODE_?` matches `CODE_A` and `CODE_1`, but not `CODE_10`).
A transition rule with pattern `*` acts as the default catch-all fallback.

---

### Q4: If two independent flows can run concurrently to fetch rates and audit blacklists, what builder construct is used in Spring Batch?
**Answer**:
**`FlowBuilder.split(TaskExecutor)`** with **`.add(Flow... flows)`**.
For example:
```java
new FlowBuilder<SimpleFlow>("splitFlow")
    .split(new SimpleAsyncTaskExecutor("batch-split-"))
    .add(parallelFlow1, parallelFlow2)
    .build();
```
This spawns concurrent execution on separate threads and synchronizes when all parallel branches reach completion before moving to the next node.

---

### Q5: If a job flow leaf step finishes without an explicit terminal transition (`.end()` or `.to(...)`), what exception does Spring Batch throw?
**Answer**:
**`org.springframework.batch.core.job.JobExecutionException: Flow execution ended unexpectedly`** (wrapping an underlying `FlowExecutionException: Next state not found for state ... with status COMPLETED`).
Every leaf step in a conditional flow graph must have an explicit terminal transition defined (e.g. `.from(leafStep).end()`).
