package com.chauhan.batchservice.validator;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

/**
 * Validates enterprise business constraints on job parameters before any step executes.
 * In Spring Batch 6, throws InvalidJobParametersException.
 */
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
