package com.chauhan.batchservice.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * MODULE 5: StepExecutionListener
 * Demonstrates overriding step ExitStatus dynamically to control downstream job branching.
 */
@Component
public class StepExitStatusOverrideListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(StepExitStatusOverrideListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("[StepExecutionListener] Step '{}' starting...", stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String overrideRoute = stepExecution.getJobParameters().getString("override.route");

        if (overrideRoute != null && !overrideRoute.isBlank()) {
            log.info("[StepExecutionListener] Overriding Step '{}' ExitStatus from '{}' to '{}'",
                    stepExecution.getStepName(), stepExecution.getExitStatus().getExitCode(), overrideRoute);
            return new ExitStatus(overrideRoute);
        }

        return stepExecution.getExitStatus();
    }
}
