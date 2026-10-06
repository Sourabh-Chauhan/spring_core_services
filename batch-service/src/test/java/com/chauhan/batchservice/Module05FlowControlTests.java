package com.chauhan.batchservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class Module05FlowControlTests {

    private static final Logger log = LoggerFactory.getLogger(Module05FlowControlTests.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("flowControlMasterJob")
    private Job flowControlMasterJob;

    @Test
    @DisplayName("Exercise 1: Standard Auto-Approval Path with Split Flows and Context Promotion")
    void testAutoApprovedPathWithSplitFlowAndContextPromotion() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("initial.risk", "25")
                .addString("force.audit", "false")
                .addLong("run.timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(flowControlMasterJob, params);

        assertThat(execution.getStatus().toString()).isEqualTo("COMPLETED");

        List<String> executedSteps = execution.getStepExecutions().stream()
                .map(StepExecution::getStepName)
                .toList();

        log.info("Path 1 (Auto-Approved) Executed Steps: {}", executedSteps);

        // Verify correct branching
        assertThat(executedSteps).contains(
                "initBatchStep",
                "fetchExchangeRatesStep",
                "validateBlacklistStep",
                "autoApproveStep",
                "generateReportStep"
        );
        assertThat(executedSteps).doesNotContain("manualAuditStep", "flowErrorHandlerStep", "specialRouteStep");

        // Verify ExecutionContextPromotionListener promoted step data to Job ExecutionContext
        assertThat(execution.getExecutionContext().getInt("riskScore")).isEqualTo(25);
        assertThat(execution.getExecutionContext().getString("batchName")).isEqualTo("PAYMENT-BATCH-V1");

        // Verify AuditLoggingJobListener recorded metrics
        assertThat(execution.getExecutionContext().containsKey("jobAuditStartTimestamp")).isTrue();
        assertThat(execution.getExecutionContext().containsKey("jobAuditDurationMs")).isTrue();
    }

    @Test
    @DisplayName("Exercise 2: High-Risk Branch via PaymentRiskDecider -> manualAuditStep")
    void testAuditRequiredBranchViaDecider() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("initial.risk", "85") // High risk score triggers AUDIT_REQUIRED
                .addLong("run.timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(flowControlMasterJob, params);

        assertThat(execution.getStatus().toString()).isEqualTo("COMPLETED");

        List<String> executedSteps = execution.getStepExecutions().stream()
                .map(StepExecution::getStepName)
                .toList();

        log.info("Path 2 (Audit-Required) Executed Steps: {}", executedSteps);

        // Verify routed to manualAuditStep instead of autoApproveStep
        assertThat(executedSteps).contains(
                "initBatchStep",
                "fetchExchangeRatesStep",
                "validateBlacklistStep",
                "manualAuditStep",
                "generateReportStep"
        );
        assertThat(executedSteps).doesNotContain("autoApproveStep", "flowErrorHandlerStep", "specialRouteStep");
    }

    @Test
    @DisplayName("Exercise 3: StepExitStatusOverrideListener dynamically reroutes to specialRouteStep")
    void testDynamicExitStatusOverrideReroute() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("override.route", "SPECIAL_ROUTE")
                .addLong("run.timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(flowControlMasterJob, params);

        assertThat(execution.getStatus().toString()).isEqualTo("COMPLETED");

        List<String> executedSteps = execution.getStepExecutions().stream()
                .map(StepExecution::getStepName)
                .toList();

        log.info("Path 3 (Special Route Override) Executed Steps: {}", executedSteps);

        // StepExitStatusOverrideListener intercepted afterStep and overrode exit code to SPECIAL_ROUTE
        // specialRouteStep converges back into generateReportStep before job completion
        assertThat(executedSteps).containsExactly("initBatchStep", "specialRouteStep", "generateReportStep");
        assertThat(executedSteps).doesNotContain(
                "fetchExchangeRatesStep", "validateBlacklistStep", "manualAuditStep", "autoApproveStep", "flowErrorHandlerStep");
    }

    @Test
    @DisplayName("Exercise 4: Error handling transition routes on('FAILED') to flowErrorHandlerStep")
    void testErrorHandlerTransition() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("fail.init", "true") // Injects failure in initBatchStep
                .addLong("run.timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(flowControlMasterJob, params);

        List<String> executedSteps = execution.getStepExecutions().stream()
                .map(StepExecution::getStepName)
                .toList();

        log.info("Path 4 (Error Handling) Executed Steps: {}", executedSteps);

        // Verify failure was caught and routed to flowErrorHandlerStep
        assertThat(executedSteps).containsExactly("initBatchStep", "flowErrorHandlerStep");
    }

    @Test
    @DisplayName("Exercise 5: Composite JobParameters Validator rejects invalid parameters before launch")
    void testInvalidJobParametersValidationRejection() {
        JobParameters invalidParams = new JobParametersBuilder()
                .addString("initial.risk", "150") // Invalid risk score > 100
                .addLong("run.timestamp", System.currentTimeMillis())
                .toJobParameters();

        assertThatThrownBy(() -> jobLauncher.run(flowControlMasterJob, invalidParams))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining("Job parameter 'initial.risk' must be between 0 and 100");
    }
}
