package com.chauhan.batchservice.model;

public record Customer(
    Long customerId,
    String firstName,
    String lastName,
    String email,
    String status
) {}
