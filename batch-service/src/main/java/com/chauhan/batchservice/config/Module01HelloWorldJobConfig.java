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
import org.springframework.transaction.annotation.Isolation;

@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository(isolationLevelForCreate = Isolation.READ_COMMITTED)
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

            // StepExecution provides access to the ExecutionContext
            ExecutionContext stepContext = chunkContext.getStepContext()
                    .getStepExecution()
                    .getExecutionContext();
            stepContext.putString("mentorMessage", "Welcome to Spring Batch 6 on Spring Boot 4!");

            // JobExecution context is shared across all steps in this job run
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
