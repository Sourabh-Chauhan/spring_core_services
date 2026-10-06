package com.chauhan.batchservice.decider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * MODULE 5: JobExecutionDecider
 * Decouples conditional workflow branching logic from steps.
 * Evaluates execution context promoted from earlier steps or job parameters.
 */
@Component
public class PaymentRiskDecider implements JobExecutionDecider {

    private static final Logger log = LoggerFactory.getLogger(PaymentRiskDecider.class);

    public static final String STATUS_AUDIT_REQUIRED = "AUDIT_REQUIRED";
    public static final String STATUS_AUTO_APPROVED = "AUTO_APPROVED";

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        // Inspect promoted ExecutionContext
        Object riskScoreObj = jobExecution.getExecutionContext().get("riskScore");
        int riskScore = (riskScoreObj instanceof Integer) ? (Integer) riskScoreObj : 0;

        // Check for manual override in JobParameters
        String forceAudit = jobExecution.getJobParameters().getString("force.audit", "false");

        log.info("[Decider] Evaluating Risk: riskScore={}, forceAudit={}", riskScore, forceAudit);

        if ("true".equalsIgnoreCase(forceAudit) || riskScore >= 50) {
            log.info("[Decider] Decision outcome: -> {}", STATUS_AUDIT_REQUIRED);
            return new FlowExecutionStatus(STATUS_AUDIT_REQUIRED);
        }

        log.info("[Decider] Decision outcome: -> {}", STATUS_AUTO_APPROVED);
        return new FlowExecutionStatus(STATUS_AUTO_APPROVED);
    }
}
