package com.chauhan.aiservice.model;

/**
 * Result record containing prompt intent analysis, compressed optimized prompt, 
 * confidence score, and local execution capability flag.
 */
public record EnrichedPromptResult(
        String rawPrompt,
        String optimizedPrompt,
        IntentCategory primaryIntent,
        double confidenceScore,
        boolean canHandleLocally,
        int estimatedTokenSavings
) {}
