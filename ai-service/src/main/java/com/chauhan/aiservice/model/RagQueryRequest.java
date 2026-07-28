package com.chauhan.aiservice.model;

/**
 * DTO for performing RAG queries against PGvector Store.
 */
public record RagQueryRequest(
        String query,
        int topK,
        double similarityThreshold
) {}
