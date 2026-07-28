package com.chauhan.aiservice.controller;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import com.chauhan.aiservice.model.EnrichedPromptResult;
import com.chauhan.aiservice.model.SentimentAnalysisResponse;
import com.chauhan.aiservice.router.AiModelRouter;
import com.chauhan.aiservice.service.PromptEnricherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiModelRouter aiModelRouter;
    private final PromptEnricherService promptEnricherService;
    private final ChatClient chatClient;

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
     * Real-time Streaming SSE Endpoint (used by ai-research-agent-service).
     * Returns a stream of Server-Sent Events (text/event-stream).
     *
     * @param prompt The prompt string to stream
     * @return Flux of text event chunks
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamPrompt(@RequestParam("prompt") String prompt) {
        log.info("Received streaming AI prompt request");
        AiPromptRequest request = AiPromptRequest.builder().prompt(prompt).build();
        return aiModelRouter.routeAndStream(request);
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
        
        EnrichedPromptResult enriched = promptEnricherService.enrichAndClassify(request.getPrompt());
        log.info("Enrichment process completed. Enriched Msg: {}", enriched.optimizedPrompt());

        request.setPrompt(enriched.optimizedPrompt());
        AiPromptResponse response = aiModelRouter.routeAndGenerate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Structured JSON Extraction Endpoint (used by web-scraper-service).
     * Extracts structured entity JSON from raw HTML or text content.
     */
    @PostMapping("/extract-structured-json")
    public ResponseEntity<Map<String, String>> extractStructuredJson(@RequestBody Map<String, String> request) {
        String rawContent = request.get("htmlContent");
        if (rawContent == null || rawContent.isBlank()) {
            rawContent = request.get("text");
        }
        log.info("Received structured JSON extraction request");

        String jsonOutput = chatClient.prompt()
                .system("You are an expert data extractor. Extract key entity fields from the provided raw text or HTML content and output strictly valid JSON.")
                .user(rawContent != null ? rawContent : "")
                .call()
                .content();

        return ResponseEntity.ok(Map.of("data", jsonOutput));
    }

    /**
     * Dynamic Text-to-SQL Endpoint (used by sql-generator-service).
     * Converts natural language user queries into database SQL based on schema.
     */
    @PostMapping("/generate-sql")
    public ResponseEntity<Map<String, String>> generateSql(@RequestBody Map<String, String> request) {
        String dbSchema = request.get("schema");
        String naturalLanguageQuery = request.get("query");
        log.info("Received Text-to-SQL generation request");

        String sql = chatClient.prompt()
                .system("You are an expert database engineer. Write a valid, safe SQL query based strictly on the provided database schema.\nSchema:\n" + dbSchema)
                .user(naturalLanguageQuery != null ? naturalLanguageQuery : "")
                .call()
                .content();

        return ResponseEntity.ok(Map.of("sql", sql));
    }

    /**
     * Structured Sentiment Analysis Endpoint.
     * Returns typed SentimentAnalysisResponse record.
     */
    @PostMapping("/analyze-sentiment")
    public ResponseEntity<SentimentAnalysisResponse> analyzeSentiment(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        log.info("Received sentiment analysis request");

        SentimentAnalysisResponse analysis = chatClient.prompt()
                .system("Analyze the sentiment of the provided text. Return sentiment classification (POSITIVE, NEGATIVE, NEUTRAL), confidence score (0.0 to 1.0), and a brief explanation.")
                .user(text != null ? text : "")
                .call()
                .entity(SentimentAnalysisResponse.class);

        return ResponseEntity.ok(analysis);
    }
}
