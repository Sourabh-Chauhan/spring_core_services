package com.chauhan.aiservice.controller;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import com.chauhan.aiservice.router.AiModelRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiModelRouter aiModelRouter;

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
}
