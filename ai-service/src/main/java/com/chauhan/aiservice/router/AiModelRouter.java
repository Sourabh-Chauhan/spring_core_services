package com.chauhan.aiservice.router;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import com.chauhan.aiservice.provider.AiProviderStrategy;
import com.chauhan.aiservice.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelRouter {

    private final List<AiProviderStrategy> providerStrategies;

    /**
     * Routes request to the preferred AI Provider Strategy.
     * If the preferred provider is unavailable, falls back dynamically to any available strategy.
     */
    public AiPromptResponse routeAndGenerate(AiPromptRequest request) {
        ProviderType target = request.getPreferredProvider() != null 
                ? request.getPreferredProvider() 
                : ProviderType.LM_STUDIO;

        AiProviderStrategy strategy = providerStrategies.stream()
                .filter(s -> s.getProviderType() == target && s.isAvailable())
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Preferred AI provider {} is unavailable. Routing to available fallback strategy.", target);
                    return providerStrategies.stream()
                            .filter(AiProviderStrategy::isAvailable)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No available AI Provider Strategy found"));
                });

        return strategy.generate(request);
    }

    /**
     * Routes streaming request to the target or default AI Provider Strategy.
     */
    public Flux<String> routeAndStream(AiPromptRequest request) {
        ProviderType target = request.getPreferredProvider() != null 
                ? request.getPreferredProvider() 
                : ProviderType.LM_STUDIO;

        AiProviderStrategy strategy = providerStrategies.stream()
                .filter(s -> s.getProviderType() == target && s.isAvailable())
                .findFirst()
                .orElseGet(() -> providerStrategies.stream()
                        .filter(AiProviderStrategy::isAvailable)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No available AI Provider Strategy found")));

        return strategy.generateStream(request);
    }
}
