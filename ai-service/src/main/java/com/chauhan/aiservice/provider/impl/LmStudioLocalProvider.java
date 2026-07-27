package com.chauhan.aiservice.provider.impl;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import com.chauhan.aiservice.provider.AiProviderStrategy;
import com.chauhan.aiservice.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class LmStudioLocalProvider implements AiProviderStrategy {

    private final OpenAiChatModel openAiChatModel;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.LM_STUDIO;
    }

    @Override
    public AiPromptResponse generate(AiPromptRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Executing AI prompt generation via LM Studio for taskId: {}", request.getTaskId());

        ChatResponse response = openAiChatModel.call(new Prompt(request.getPrompt()));
        String outputText = response.getResult().getOutput().getText();

        return AiPromptResponse.builder()
                .taskId(request.getTaskId())
                .output(outputText)
                .providerUsed(ProviderType.LM_STUDIO)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    @Override
    public Flux<String> generateStream(AiPromptRequest request) {
        log.info("Executing streaming AI prompt generation via LM Studio for taskId: {}", request.getTaskId());
        return openAiChatModel.stream(request.getPrompt());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
