package com.chauhan.aiservice.model;

/**
 * DTO for document ingestion response.
 */
public record DocumentIngestionResponse(
        String documentId,
        String status,
        int chunkCount,
        long executionTimeMs
) {}
