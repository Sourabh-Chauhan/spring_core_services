i have to learn spring batch in spring boot
give a full structured prompt and plan to learn it from zero


Two parts: a prompt you can paste into any AI tutor, and the plan itself.

Part 1: The structured prompt
ROLE
You are a senior Spring Batch engineer and patient technical mentor.

MY BACKGROUND
I'm an experienced Java developer who has worked with enterprise batch
jobs in a non-Spring framework. I know Java, SQL, and basic Spring Boot
(DI, REST, JPA). I'm new to Spring Batch.

GOAL
Take me from zero to production-ready with Spring Batch on Spring Boot
in 4 weeks, at ~1.5 hours/day.

VERSION
Use the latest stable Spring Boot and Spring Batch. Flag any API that
changed between Batch 4 / 5 / 6 so I don't copy outdated code.

TEACHING FORMAT (for every topic)
1. Concept in plain language (max 150 words), with a "why it exists" line
2. Diagram or mental model (ASCII is fine)
3. Minimal runnable code (Maven, Java 17+, H2 or PostgreSQL)
4. Common mistakes and gotchas
5. 3 hands-on exercises, increasing difficulty
6. 3 interview-style questions with answers

CURRICULUM
1. Core model: Job, Step, JobInstance, JobExecution, StepExecution,
   JobRepository, JobLauncher, metadata tables, ExecutionContext
2. Tasklet vs chunk-oriented steps
3. ItemReader / ItemProcessor / ItemWriter (flat file, JDBC cursor vs
   paging, JPA, JSON, composite writers)
4. Job parameters, incrementers, restartability, identity
5. Flow control: sequential, conditional, decisions, split flows,
   listeners, validators
6. Fault tolerance: skip, retry, rollback rules, skip listeners,
   restart from failure
7. Scaling: multi-threaded step, parallel steps, async processor,
   partitioning, remote chunking
8. Production: launching (REST, scheduler, K8s CronJob), monitoring
   with Micrometer, testing with @SpringBatchTest, tuning chunk size,
   idempotency, cleanup of metadata tables

RULES
- Teach one module at a time. After each, quiz me with 5 questions and
  wait for my answers before moving on.
- Don't dump the whole curriculum at once.
- Prefer code over theory. No pseudo-code.
- When I get something wrong, explain why, then re-quiz me on it.
- Where it applies, compare Spring Batch with hand-rolled batch
  frameworks so I can map existing knowledge.

CAPSTONE
At the end, guide me through building an invoice-processing batch app:
CSV in -> validate -> enrich from DB -> write to DB -> aggregate ->
export report, with skip/retry, restartability, partitioning, and tests.

Start with Module 1.
Part 2: The 4-week plan
Week 0 (1-2 days): Prerequisites check
Spring Boot auto-configuration, @Configuration, @Bean
JdbcTemplate or JPA basics
@Transactional, propagation, rollback rules (batch leans heavily on these)
Week 1: Core model and first jobs
Concepts: Job → Step → chunk (Reader → Processor → Writer). Understand JobInstance vs JobExecution (identity = job name + identifying parameters).
Metadata tables: BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, BATCH_STEP_EXECUTION, and the context tables. Run a job, then inspect them.
Build:
A Hello World tasklet job
A CSV → H2 chunk job
The same job with job parameters, run twice, to see "instance already complete"
Key idea: the commit interval is your transaction boundary.
Week 2: Readers, writers, flow, and restart
FlatFileItemReader, JdbcCursorItemReader vs JdbcPagingItemReader, JpaPagingItemReader
JdbcBatchItemWriter, FlatFileItemWriter, CompositeItemWriter
Processors: transform, validate, filter (returning null filters the item)
Flow control: on("FAILED").to(...), JobExecutionDecider, split flows
Listeners: job, step, chunk, item
Restart: kill a job mid-run and resume. Understand saveState and ExecutionContext.
Build: a 3-step job (load → validate → report) with conditional flow.
Week 3: Fault tolerance and scaling
faultTolerant(), skip(), skipLimit(), retry(), retryLimit(), noRollback()
What happens inside a chunk when one item fails (the chunk is retried item by item)
Scaling options, in order of complexity:
Multi-threaded step (watch thread safety of readers)
Parallel steps
AsyncItemProcessor/AsyncItemWriter
Partitioning (the most important one to master)
Remote chunking (understand the concept only)
Build: add skip/retry to your Week 2 job, then partition by ID range.
Week 4: Production readiness and capstone
Launching: disable spring.batch.job.enabled auto-run, trigger via REST, @Scheduled, Quartz, or K8s CronJob
Testing: @SpringBatchTest, JobLauncherTestUtils, step-level tests
Monitoring: Micrometer metrics, listener-based logging
Operations: idempotent writes, metadata table cleanup, preventing duplicate runs, tuning chunk size and fetch size
Capstone: the invoice-processing app from the prompt. Include Testcontainers with PostgreSQL if you want production realism.
Daily rhythm (~1.5 hrs)
20 min: read the concept
50 min: code it yourself (don't copy-paste)
20 min: break it on purpose (kill the process, throw exceptions, bad data) and observe the metadata tables
Gotchas
Spring Batch 5 (Boot 3) changed the builder APIs: JobBuilder/StepBuilder now take JobRepository and a transaction manager, and JobBuilderFactory is gone. Most old blog posts and tutorials use the outdated style.
In Boot 3, using @EnableBatchProcessing turns off Boot's batch auto-configuration. Usually you shouldn't add it.
Batch 6 arrived with Boot 4, so check your Initializr version and read the migration notes before following older tutorials.
Cursor readers aren't thread-safe. Use paging readers with multi-threaded steps.
Resources
Official reference docs at spring.io/projects/spring-batch (the best source, and it's readable)
The spring-batch-samples module in the Spring Batch GitHub repo
Spring Batch Javadoc for the item readers/writers (the list of built-in ones is long, so skim it once)
I can also turn this into a downloadable doc, or write the Week 1 hands-on starter project (pom.xml plus the first job) if you'd like.