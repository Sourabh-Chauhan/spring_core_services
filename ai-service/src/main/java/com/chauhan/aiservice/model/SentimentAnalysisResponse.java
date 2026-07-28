package com.chauhan.aiservice.model;

/**
 * Structured DTO record for sentiment analysis response.
 */
public record SentimentAnalysisResponse(
        String sentiment,       // POSITIVE, NEGATIVE, NEUTRAL
        double confidenceScore, // 0.0 to 1.0
        String explanation
) {}
