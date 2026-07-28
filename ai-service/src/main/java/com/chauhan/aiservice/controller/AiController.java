package com.chauhan.aiservice.controller;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import com.chauhan.aiservice.model.EnrichedPromptResult;
import com.chauhan.aiservice.router.AiModelRouter;
import com.chauhan.aiservice.service.PromptEnricherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiModelRouter aiModelRouter;
    private final PromptEnricherService promptEnricherService;

    /**
     * Synchronous text generation endpoint via AiModelRouter.
     *
     * @param request AiPromptRequest containing taskId, prompt, systemInstruction, and preferredProvider
     * @return ResponseEntity containing AiPromptResponse
     */
    @PostMapping("/generate")
    public ResponseEntity<AiPromptResponse> generate(@RequestBody AiPromptRequest request) {
        log.info("Received AI prompt generation request for taskId: {}", request.getTaskId());
        AiPromptResponse response = aiModelRouter.routeAndGenerate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Pre-processes & enriches user prompt locally via LM Studio ($0 cost) 
     * to extract intent and compress tokens.
     */
    @PostMapping("/enrich-prompt")
    public ResponseEntity<EnrichedPromptResult> enrichPrompt(@RequestBody Map<String, String> request) {
        String rawPrompt = request.get("prompt");
        log.info("Received prompt enrichment request");
        EnrichedPromptResult result = promptEnricherService.enrichAndClassify(rawPrompt);
        return ResponseEntity.ok(result);
    }

    /**
     * Smart generation pipeline:
     * 1. Pre-processes & compresses raw prompt via local LM Studio.
     * 2. Replaces raw prompt with token-dense optimized prompt.
     * 3. Routes through AiModelRouter to target model.
     */
    @PostMapping("/generate-smart")
    public ResponseEntity<AiPromptResponse> generateSmart(@RequestBody AiPromptRequest request) {
        log.info("Received smart generation request for taskId: {}", request.getTaskId());
        log.info("Enrichment process Started");
        // Step 1: Pre-process & enrich prompt locally
        EnrichedPromptResult enriched = promptEnricherService.enrichAndClassify(request.getPrompt());
        log.info("Enrichment progress done");

        log.info("Enriched Msg ::  {} " , enriched.optimizedPrompt());
        // Step 2: Use optimized prompt for execution
        request.setPrompt(enriched.optimizedPrompt());

        // Step 3: Route via AiModelRouter
        AiPromptResponse response = aiModelRouter.routeAndGenerate(request);
        return ResponseEntity.ok(response);
    }
}
