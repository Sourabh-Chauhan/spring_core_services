package com.chauhan.batchservice.model;

import java.math.BigDecimal;

public record Transaction(
        Long transactionId,
        String accountNumber,
        BigDecimal amount,
        String status
) {}
