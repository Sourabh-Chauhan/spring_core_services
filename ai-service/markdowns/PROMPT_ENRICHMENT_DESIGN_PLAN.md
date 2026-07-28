# ⚡ Local Prompt Enrichment & Token Optimization Engine Design Plan

> **Location:** `ai-service/markdowns/PROMPT_ENRICHMENT_DESIGN_PLAN.md`  
> **Target Service:** `ai-service` (Port `8085`)  
> **Core Objective:** Pre-process raw user prompts using fast, local SLM/LLM (LM Studio at $0 cost) to extract intent, compress tokens by 50-80%, and route execution efficiently before calling cloud LLM APIs or downstream processors.

---

## 📑 Table of Contents

1. [Architectural Rationale & Token Economics](#1-architectural-rationale--token-economics)
2. [Data Flow Architecture](#2-data-flow-architecture)
3. [Domain Models & Data Transfer Objects](#3-domain-models--data-transfer-objects)
4. [Service Layer Implementation Specification](#4-service-layer-implementation-specification)
5. [Router & Controller Pipeline Integration](#5-router--controller-pipeline-integration)
6. [API Specifications](#6-api-specifications)
7. [Testing & Token Savings Verification](#7-testing--token-savings-verification)

---

## 1. 💡 Architectural Rationale & Token Economics

### 1.1 The Problem: Raw Prompt Inefficiency
User prompts are often verbose, conversational, or contain noise (e.g. *"Hello AI, could you please take a look at this document and if possible summarize it for me in a few bullet points..."*).
- **Cost Wastage:** Passing raw 500+ token prompts directly to paid Cloud APIs (GPT-4o, Claude 3.5, Gemini 1.5) wastes expensive input tokens.
- **Latency & Noise:** Extra fluff increases inference latency and risk of model hallucinations.

### 1.2 The Solution: Local Zero-Cost Pre-Processing
Before sending a prompt to cloud models or executing heavy database/vector operations, **`ai-service` passes the prompt through local LM Studio (running locally at $0 cost)**.

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Incoming Raw User Request                       │
│      "Hey assistant, can you please tell me how many users registered" │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│               Phase 1: Local LM Studio Intent Engine                   │
│                    (Fast & Free Local Pre-Processor)                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
                     Produces Enriched Prompt DTO:
            ┌────────────────────────────────────────────────┐
            │ Intent:           TEXT_TO_SQL                  │
            │ OptimizedPrompt:  "COUNT(*) FROM users"        │
            │ CanAnswerLocally: true                         │
            └───────────────────────┬────────────────────────┘
                                    │
                  ┌─────────────────┴─────────────────┐
                  ▼                                   ▼
      [Answered Locally by LM Studio]      [Routed to Cloud Model]
            (Cost: $0.00)                 (Token-Dense Prompt)
```

### Key Benefits
1. **Cost Reduction:** Prunes filler words, reducing input token counts by 50-80% before reaching cloud APIs.
2. **Intent Guardrailing:** Identifies invalid or `OUT_OF_SCOPE` queries early, avoiding cloud billing entirely.
3. **Local Offloading:** Simple queries are answered directly by local LM Studio at zero cost.

---

## 2. 🏗 Data Flow Architecture

```text
Specialized Consumer / Client
           │
           │ POST /api/v1/ai/generate-smart (rawPrompt)
           ▼
┌───────────────────────────────────────────────────────────┐
│                    PromptEnricherService                  │
│  - Invokes LM Studio with System Optimization Persona     │
│  - Parses JSON into EnrichedPromptResult                  │
└────────────────────────────┬──────────────────────────────┘
                             │
                             ▼
┌───────────────────────────────────────────────────────────┐
│                    AiModelRouter Decision                 │
│                                                           │
│  IF canHandleLocally == true ──► Execute via LM Studio    │
│  ELSE ──► Forward Optimized Prompt to Target Cloud Model  │
└───────────────────────────────────────────────────────────┘
```

---

## 3. 🧩 Domain Models & Data Transfer Objects

### 3.1 `IntentCategory` Enum
```java
package com.chauhan.aiservice.model;

public enum IntentCategory {
    GENERAL_CHAT,
    TEXT_TO_SQL,
    STRUCTURED_EXTRACTION,
    DOCUMENT_QA,
    SUMMARIZATION,
    OUT_OF_SCOPE
}
```

### 3.2 `EnrichedPromptResult` Java Record
```java
package com.chauhan.aiservice.model;

public record EnrichedPromptResult(
        String rawPrompt,
        String optimizedPrompt,
        IntentCategory primaryIntent,
        double confidenceScore,
        boolean canHandleLocally,
        int estimatedTokenSavings
) {}
```

---

## 4. ⚙️ Service Layer Implementation Specification

### `PromptEnricherService.java`
```java
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
     * Pre-processes raw user prompt via local LM Studio to detect intent 
     * and produce a token-optimized prompt.
     */
    public EnrichedPromptResult enrichAndClassify(String rawPrompt) {
        log.info("Running local prompt enrichment & intent classification for prompt length: {}", rawPrompt.length());

        return chatClient.prompt()
                .system("""
                    You are an AI Prompt Optimizer & Intent Classifier.
                    Analyze the raw user prompt. Perform the following steps:
                    1. Identify the primary intent (GENERAL_CHAT, TEXT_TO_SQL, STRUCTURED_EXTRACTION, DOCUMENT_QA, SUMMARIZATION, OUT_OF_SCOPE).
                    2. Strip all pleasantries, fluff, and conversational filler words.
                    3. Rewrite the request into a concise, token-dense, instruction-focused prompt.
                    4. Determine if the request is simple enough to be answered by a local model (canHandleLocally).
                    """)
                .user(rawPrompt)
                .call()
                .entity(EnrichedPromptResult.class);
    }
}
```

---

## 5. 🔀 Router & Controller Pipeline Integration

### 5.1 Update `AiController.java`
Add a smart execution endpoint `POST /api/v1/ai/generate-smart`:

```java
@PostMapping("/enrich-prompt")
public ResponseEntity<EnrichedPromptResult> enrichPrompt(@RequestBody Map<String, String> request) {
    String rawPrompt = request.get("prompt");
    return ResponseEntity.ok(promptEnricherService.enrichAndClassify(rawPrompt));
}

@PostMapping("/generate-smart")
public ResponseEntity<AiPromptResponse> generateSmart(@RequestBody AiPromptRequest request) {
    // 1. Enrich & optimize prompt locally
    EnrichedPromptResult enriched = promptEnricherService.enrichAndClassify(request.getPrompt());

    // 2. Replace raw prompt with optimized token-dense prompt
    request.setPrompt(enriched.optimizedPrompt());

    // 3. Route through AiModelRouter
    return ResponseEntity.ok(aiModelRouter.routeAndGenerate(request));
}
```

---

## 6. 🧪 Testing & Token Savings Verification

1. **Test Payload:**
```json
{
  "prompt": "Hello AI assistant! Could you please be so kind as to give me a very short 2 sentence explanation of what PostgreSQL is?"
}
```

2. **Expected Local Enrichment Output:**
```json
{
  "rawPrompt": "Hello AI assistant! Could you please be so kind as to give me a very short 2 sentence explanation of what PostgreSQL is?",
  "optimizedPrompt": "Explain PostgreSQL in exactly 2 sentences.",
  "primaryIntent": "GENERAL_CHAT",
  "confidenceScore": 0.95,
  "canHandleLocally": true,
  "estimatedTokenSavings": 18
}
```

---

*Design plan created for `ai-service` in `spring_core_services` project.*
