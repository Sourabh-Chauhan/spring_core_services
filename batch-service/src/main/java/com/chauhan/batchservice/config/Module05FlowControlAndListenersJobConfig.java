package com.chauhan.batchservice.config;

import com.chauhan.batchservice.decider.PaymentRiskDecider;
import com.chauhan.batchservice.listener.AuditLoggingJobListener;
import com.chauhan.batchservice.listener.StepExitStatusOverrideListener;
import com.chauhan.batchservice.validator.PaymentRiskParametersValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.parameters.CompositeJobParametersValidator;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * MODULE 5: Flow Control (Sequential, Conditional, Deciders, Split Flows, Listeners & Validators)
 *
 * KEY CONCEPTS DEMONSTRATED:
 * 1. Sequential Transitions: stepA -> stepB
 * 2. Split (Parallel) Flows: Running independent flows concurrently on SimpleAsyncTaskExecutor.
 * 3. JobExecutionDecider: Programmatic branching returning custom FlowExecutionStatus.
 * 4. Conditional Transitions: on("PATTERN").to(targetStep)
 * 5. Dynamic ExitStatus Override: StepExecutionListener altering downstream routing.
 * 6. ExecutionContextPromotionListener: Automatically promoting step context keys to job context.
 * 7. JobExecutionListener: Intercepting beforeJob and afterJob for enterprise auditing.
 * 8. CompositeJobParametersValidator: Multi-tier parameter validation prior to step launch.
 */
@Configuration
public class Module05FlowControlAndListenersJobConfig {

    private static final Logger log = LoggerFactory.getLogger(Module05FlowControlAndListenersJobConfig.class);

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

    @Bean
    public Job flowControlMasterJob(JobRepository jobRepository,
                                   AuditLoggingJobListener auditLoggingJobListener,
                                   CompositeJobParametersValidator flowControlJobParametersValidator,
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

    /**
     * Step 1: Initial Tasklet that computes metrics and places values in Step ExecutionContext.
     */
    @Bean
    public Step initBatchStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             ExecutionContextPromotionListener executionContextPromotionListener,
                             StepExitStatusOverrideListener stepExitStatusOverrideListener) {
        return new StepBuilder("initBatchStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    var stepExecution = contribution.getStepExecution();
                    var jobParams = stepExecution.getJobParameters();

                    // Check for failure simulation
                    String failInit = jobParams.getString("fail.init", "false");
                    if ("true".equalsIgnoreCase(failInit)) {
                        log.warn("[initBatchStep] Simulating failure in initialization step!");
                        throw new RuntimeException("Simulated failure in initBatchStep");
                    }

                    // Compute risk score (from param or default 20)
                    String initialRiskStr = jobParams.getString("initial.risk", "20");
                    int riskScore = Integer.parseInt(initialRiskStr);

                    log.info("[initBatchStep] Initializing batch. Risk Score set to: {}", riskScore);

                    // Put in Step ExecutionContext (will be promoted to Job ExecutionContext by listener)
                    stepExecution.getExecutionContext().put("riskScore", riskScore);
                    stepExecution.getExecutionContext().put("batchName", "PAYMENT-BATCH-V1");

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .listener(executionContextPromotionListener)
                .listener(stepExitStatusOverrideListener)
                .build();
    }

    /**
     * ExecutionContextPromotionListener:
     * Promotes 'riskScore' and 'batchName' from Step ExecutionContext to Job ExecutionContext.
     */
    @Bean
    public ExecutionContextPromotionListener executionContextPromotionListener() {
        ExecutionContextPromotionListener listener = new ExecutionContextPromotionListener();
        listener.setKeys(new String[]{"riskScore", "batchName"});
        listener.setStrict(false);
        return listener;
    }

    /**
     * Split Flow: Runs two independent flows concurrently in parallel threads.
     */
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
    public Step fetchExchangeRatesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("fetchExchangeRatesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Parallel Flow 1] Fetching live currency exchange rates on thread: {}",
                            Thread.currentThread().getName());
                    Thread.sleep(50); // Simulate network latency
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step validateBlacklistStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("validateBlacklistStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Parallel Flow 2] Validating blacklist accounts on thread: {}",
                            Thread.currentThread().getName());
                    Thread.sleep(50); // Simulate network latency
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step manualAuditStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("manualAuditStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.warn("[Audit Branch] >>> Routing to MANUAL AUDIT queue <<<");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step autoApproveStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("autoApproveStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Auto-Approve Branch] >>> Payments AUTO-APPROVED without audit <<<");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step generateReportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("generateReportStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    var jobContext = chunkContext.getStepContext().getJobExecutionContext();
                    log.info("[Final Step] Generating final summary report for batch '{}'",
                            jobContext.get("batchName"));
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step flowErrorHandlerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("flowErrorHandlerStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.error("[Error Handler Step] Executing compensation / rollback cleanup routine!");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step specialRouteStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("specialRouteStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Special Route Step] Executed special fast-track route via listener override!");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
