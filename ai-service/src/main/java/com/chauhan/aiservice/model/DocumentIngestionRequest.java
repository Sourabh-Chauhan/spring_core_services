package com.chauhan.aiservice.model;

import java.util.Map;

/**
 * DTO for ingesting raw document text into PGvector Store.
 */
public record DocumentIngestionRequest(
        String content,
        String source,
        String category,
        Map<String, Object> metadata
) {}
