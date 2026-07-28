package com.chauhan.aiservice.service;

import com.chauhan.aiservice.model.EnrichedPromptResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptEnricherService {

    private final ChatClient chatClient;

    /**
     * Pre-processes raw user prompt via local SLM/LLM to detect intent 
     * and produce a token-optimized prompt.
     *
     * @param rawPrompt Raw, potentially verbose user prompt
     * @return EnrichedPromptResult structured intent & optimized prompt
     */
    public EnrichedPromptResult enrichAndClassify(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty");
        }

        log.info("Running local prompt enrichment & intent classification for prompt length: {}", rawPrompt.length());

        EnrichedPromptResult result = chatClient.prompt()
                .system("""
                    You are an AI Prompt Optimizer & Intent Classifier.
                    Analyze the raw user prompt and perform the following tasks:
                    1. Categorize intent into one of: GENERAL_CHAT, TEXT_TO_SQL, STRUCTURED_EXTRACTION, DOCUMENT_QA, SUMMARIZATION, OUT_OF_SCOPE.
                    2. Remove pleasantries, conversational fluff, and redundant words.
                    3. Rewrite the user's intent into a clean, concise, token-dense instruction (optimizedPrompt).
                    4. Set canHandleLocally to true if the query is basic or conversational, false if it requires complex reasoning or cloud APIs.
                    """)
                .user(rawPrompt)
                .call()
                .entity(EnrichedPromptResult.class);

        assert result != null;
        log.info("Prompt enrichment complete. Intent: {}, CanHandleLocally: {}",
                result.primaryIntent(), result.canHandleLocally());

        return result;
    }
}
