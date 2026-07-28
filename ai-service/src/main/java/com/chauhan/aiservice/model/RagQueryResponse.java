package com.chauhan.aiservice.model;

import java.util.List;

/**
 * DTO for RAG query response containing answer and retrieved context sources.
 */
public record RagQueryResponse(
        String query,
        String answer,
        List<String> sourceDocuments,
        long executionTimeMs
) {}
