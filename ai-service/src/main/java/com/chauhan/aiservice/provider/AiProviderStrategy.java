package com.chauhan.aiservice.provider;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import reactor.core.publisher.Flux;

/**
 * Strategy interface for abstracting AI Provider models (Local LM Studio, Gemini, Claude, OpenAI).
 */
public interface AiProviderStrategy {

    ProviderType getProviderType();

    AiPromptResponse generate(AiPromptRequest request);

    Flux<String> generateStream(AiPromptRequest request);

    boolean isAvailable();
}
