# 🤖 AI Service (`ai-service`) Master Implementation Plan

> **Target Audience:** Developers new to AI, LLMs, and **Spring AI**.  
> **Location:** `ai-service/markdowns/AI_SERVICE_IMPLEMENTATION_PLAN.md`  
> **Reference Design Plan:** [markdowns/ai_service_design_plan.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/markdowns/ai_service_design_plan.md)  
> **Project Context:** Multi-Module Spring Boot / Spring Cloud Microservices (`spring_core_services`)

---

## 📑 Table of Contents

1. [Architectural Vision & Delegation Model](#1-architectural-vision--delegation-model)
2. [Spring AI & AI Core Primer for Beginners](#2-spring-ai--ai-core-primer-for-beginners)
3. [LM Studio & Provider-Agnostic Strategy Architecture](#3-lm-studio--provider-agnostic-strategy-architecture)
4. [Step-by-Step Implementation Roadmap](#4-step-by-step-implementation-roadmap)
   - [Phase 1: Maven Dependencies & `application.yml` Setup](#phase-1-maven-dependencies--applicationyml-setup)
   - [Phase 2: Provider Strategy Pattern Implementation](#phase-2-provider-strategy-pattern-implementation)
   - [Phase 3: Domain API Endpoints (`/api/v1/ai/**`)](#phase-3-domain-api-endpoints-apiv1ai)
   - [Phase 4: RAG (Retrieval-Augmented Generation) & PGvector](#phase-4-rag-retrieval-augmented-generation--pgvector)
   - [Phase 5: Function Calling (Spring AI `@Tool`)](#phase-5-function-calling-spring-ai-tool)
   - [Phase 6: Gateway Routing & Circuit Breaker Resilience](#phase-6-gateway-routing--circuit-breaker-resilience)
5. [LM Studio Setup & Local Testing Guide](#5-lm-studio-setup--local-testing-guide)
6. [Specialized Consumer Integration Guide](#6-specialized-consumer-integration-guide)

---

## 1. 🏗 Architectural Vision & Delegation Model

Based on the [ai_service_design_plan.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/markdowns/ai_service_design_plan.md):

### 1.1 Core Infrastructure Protection Rule
Core infrastructure microservices (`auth-service`, `gateway-service`, `notification-service`, `Eureka-server`) **do NOT run LLM inference directly**. Security, routing, and notifications must stay deterministic, instant, and high-throughput.

### 1.2 `ai-service` as Centralized AI Infrastructure Engine
`ai-service` runs on port **8085**, registered with Eureka. Future specialized consumer microservices delegate heavy AI tasks to `ai-service`:

```text
┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐
│   web-scraper-service   │   │  sql-generator-service  │   │ research-agent-service  │
│  (Fetches Raw HTML/DOM) │   │ (Natural Lang to SQL)   │   │  (Multi-Step ReAct Loop)│
└────────────┬────────────┘   └────────────┬────────────┘   └────────────┬────────────┘
             │                             │                             │
             │ POST /extract-structured-json│ POST /generate-sql          │ GET /stream (SSE)
             └──────────────────────┐      │      ┌──────────────────────┘
                                    ▼      ▼      ▼
                        ┌───────────────────────────────────┐
                        │            ai-service             │
                        │    (Port 8085 - Eureka Registered)│
                        └─────────────────┬─────────────────┘
                                          │
                                          ▼
                        ┌───────────────────────────────────┐
                        │   Local LM Studio (localhost:1234)│
                        │    Cloud Gemini / Anthropic / GPT │
                        └───────────────────────────────────┘
```

---

## 2. 🧠 Spring AI & AI Core Primer for Beginners

If you are new to AI and **Spring AI**, here is a simple breakdown of concepts:

| Concept               | Explanation                                                                 | Spring AI Implementation                     |
|:----------------------|:----------------------------------------------------------------------------|:---------------------------------------------|
| **LLM / SLM**         | Large/Small Language Model (e.g. Llama 3 in LM Studio, GPT-4o, Gemini 1.5). | `ChatModel` (e.g., `OpenAiChatModel`).       |
| **Prompt**            | Instructions and text passed to the AI model.                               | `Prompt`, `ChatClient`.                      |
| **Tokens**            | Small text chunks processed by the AI (1 token $\approx$ 4 characters).     | Managed internally by Spring AI.             |
| **Structured Output** | Forcing the AI to return typed JSON instead of free text.                   | `BeanOutputConverter<T>` / `.entity(Class)`. |
| **Embeddings**        | Arrays of numbers representing text meaning.                                | `EmbeddingModel`.                            |
| **Vector Database**   | Database for storing and querying text embeddings by similarity.            | `PgVectorStore` (PostgreSQL `pgvector`).     |
| **RAG**               | Feeding private/custom documents into prompt context.                       | `VectorStore.similaritySearch()`.            |
| **Function Calling**  | Allowing the AI to trigger Java code dynamically.                           | `@Tool` annotations / `FunctionCallback`.    |

---

## 3. 🔌 LM Studio & Provider-Agnostic Strategy Architecture

`ai-service` uses the **Strategy Pattern** to dynamically route prompts to **Local LM Studio** (zero cost, offline) or Cloud LLMs (Google Gemini, Anthropic Claude, OpenAI).

```text
                                 ┌───────────────────────────┐
                                 │     AiProviderStrategy    │
                                 └─────────────┬─────────────┘
                                               │
       ┌──────────────────────┬────────────────┼──────────────────────┐
       ▼                      ▼                ▼                      ▼
┌───────────────┐      ┌───────────────┐┌───────────────┐      ┌───────────────┐
│ LmStudioLocal │      │ GeminiCloud   ││ ClaudeCloud   │      │ OpenAiCloud   │
│ Provider      │      │ Provider      ││ Provider      │      │ Provider      │
│(localhost:1234│      │ (Google GenAI)││ (Anthropic)   │      │ (OpenAI API)  │
└───────────────┘      └───────────────┘└───────────────┘      └───────────────┘
```

---

## 4. 🗺 Step-by-Step Implementation Roadmap

---

### Phase 1: Maven Dependencies & `application.yml` Setup

#### Step 1.1: `ai-service/pom.xml`

```xml
<dependencies>
    <!-- Spring Boot Web & WebFlux (Streaming SSE) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Spring Cloud Eureka Client -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>

    <!-- Spring AI Starters (Spring Initializr 1.0+/2.0+ Naming, Managed by parent spring-ai-bom) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-vector-store-advisor</artifactId>
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### Step 1.2: `ai-service/src/main/resources/application.yml`

```yaml
server:
  port: 8085

spring:
  application:
    name: ai-service

  # Spring AI Configuration pointing to Local LM Studio (OpenAI Compatible API)
  ai:
    openai:
      base-url: ${LM_STUDIO_URL:http://localhost:1234/v1}
      api-key: lm-studio # LM Studio accepts any non-null string
      chat:
        options:
          model: local-model
          temperature: 0.7

    # PGvector Store Configuration
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536

  # Shared PostgreSQL Container for Vector Store
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5001}/${POSTGRES_DB:auth_db}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

### Phase 2: Provider Strategy Pattern Implementation

#### Step 2.1: `ProviderType` Enum & Models

```java
package com.chauhan.aiservice.provider;

public enum ProviderType {
    LM_STUDIO,
    GEMINI,
    CLAUDE,
    OPENAI
}
```

```java
package com.chauhan.aiservice.model;

import com.chauhan.aiservice.provider.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptRequest {
    private String taskId;
    private String prompt;
    private String systemInstruction;
    private ProviderType preferredProvider;
}
```

```java
package com.chauhan.aiservice.model;

import com.chauhan.aiservice.provider.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptResponse {
    private String taskId;
    private String output;
    private ProviderType providerUsed;
    private long executionTimeMs;
}
```

#### Step 2.2: Strategy Interface & LM Studio Provider

```java
package com.chauhan.aiservice.provider;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import reactor.core.publisher.Flux;

public interface AiProviderStrategy {

    ProviderType getProviderType();

    AiPromptResponse generate(AiPromptRequest request);

    Flux<String> generateStream(AiPromptRequest request);

    boolean isAvailable();
}
```

```java
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
        log.info("Executing AI request via Local LM Studio for task: {}", request.getTaskId());

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
        return openAiChatModel.stream(request.getPrompt());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
```

#### Step 2.3: `AiModelRouter`

```java
package com.chauhan.aiservice.router;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import com.chauhan.aiservice.provider.AiProviderStrategy;
import com.chauhan.aiservice.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiModelRouter {

    private final List<AiProviderStrategy> providerStrategies;

    public AiPromptResponse routeAndGenerate(AiPromptRequest request) {
        ProviderType target = request.getPreferredProvider() != null 
                ? request.getPreferredProvider() 
                : ProviderType.LM_STUDIO;

        AiProviderStrategy strategy = providerStrategies.stream()
                .filter(s -> s.getProviderType() == target && s.isAvailable())
                .findFirst()
                .orElseGet(() -> providerStrategies.stream()
                        .filter(AiProviderStrategy::isAvailable)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No AI Provider Strategy available")));

        return strategy.generate(request);
    }

    public Flux<String> routeAndStream(AiPromptRequest request) {
        AiProviderStrategy strategy = providerStrategies.stream()
                .filter(s -> s.getProviderType() == ProviderType.LM_STUDIO)
                .findFirst()
                .orElseThrow();
        return strategy.generateStream(request);
    }
}
```

---

### Phase 3: Domain API Endpoints (`/api/v1/ai/**`)

Create `com.chauhan.aiservice.controller.AiController`:

```java
package com.chauhan.aiservice.controller;

import com.chauhan.aiservice.model.AiPromptRequest;
import com.chauhan.aiservice.model.AiPromptResponse;
import com.chauhan.aiservice.router.AiModelRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiModelRouter aiModelRouter;
    private final ChatClient chatClient;

    /**
     * Standard Prompt Execution Endpoint
     */
    @PostMapping("/generate")
    public ResponseEntity<AiPromptResponse> generate(@RequestBody AiPromptRequest request) {
        return ResponseEntity.ok(aiModelRouter.routeAndGenerate(request));
    }

    /**
     * Real-time Streaming SSE Endpoint (used by ai-research-agent-service)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamPrompt(@RequestParam("prompt") String prompt) {
        AiPromptRequest request = AiPromptRequest.builder().prompt(prompt).build();
        return aiModelRouter.routeAndStream(request);
    }

    /**
     * 1. Structured JSON Extraction Endpoint (used by web-scraper-service)
     */
    @PostMapping("/extract-structured-json")
    public ResponseEntity<Map<String, Object>> extractStructuredJson(@RequestBody Map<String, String> request) {
        String rawHtmlOrText = request.get("htmlContent");

        String jsonOutput = chatClient.prompt()
                .system("Extract key entity fields from the provided raw text and format strictly as clean JSON.")
                .user(rawHtmlOrText)
                .call()
                .content();

        return ResponseEntity.ok(Map.of("data", jsonOutput));
    }

    /**
     * 2. Dynamic Text-to-SQL Endpoint (used by sql-generator-service)
     */
    @PostMapping("/generate-sql")
    public ResponseEntity<Map<String, String>> generateSql(@RequestBody Map<String, String> request) {
        String dbSchema = request.get("schema");
        String naturalLanguageQuery = request.get("query");

        String sql = chatClient.prompt()
                .system("You are a database expert. Write a valid, safe PostgreSQL SQL query based on the provided schema.\nSchema:\n" + dbSchema)
                .user(naturalLanguageQuery)
                .call()
                .content();

        return ResponseEntity.ok(Map.of("sql", sql));
    }
}
```

---

### Phase 4: RAG (Retrieval-Augmented Generation) & PGvector

#### Step 4.1: Enable `vector` Extension in PostgreSQL
Run in container `postgres-auth` (`5001`):
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

#### Step 4.2: Vector Store Service (`RagService.java`)
```java
package com.chauhan.aiservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public void addDocuments(List<Document> documents) {
        vectorStore.add(documents);
    }

    public String askWithVectorContext(String userQuery) {
        List<Document> docs = vectorStore.similaritySearch(userQuery);
        StringBuilder context = new StringBuilder();
        for (Document d : docs) {
            context.append(d.getContent()).append("\n");
        }

        return chatClient.prompt()
                .system("Answer using ONLY the following context:\n" + context)
                .user(userQuery)
                .call()
                .content();
    }
}
```

---

### Phase 5: Function Calling (Spring AI `@Tool`)

Create an AI Tool class:

```java
package com.chauhan.aiservice.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SystemMetricsTool {

    @Tool(description = "Returns current system operational status and active Eureka microservices count.")
    public String getSystemStatus() {
        return "ALL_SERVICES_UP (Eureka registered services: auth-service, gateway-service, notification-service, ai-service)";
    }
}
```

---

### Phase 6: Gateway Routing & Circuit Breaker Resilience

Update `gateway-service/src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: ai-service
          uri: lb://ai-service
          predicates:
            - Path=/api/v1/ai/**
          filters:
            - StripPrefix=0
            - name: CircuitBreaker
              args:
                name: aiCircuitBreaker
                fallbackUri: forward:/fallback/ai
```

---

## 5. 🚀 LM Studio Setup & Local Testing Guide

1. Download and open **LM Studio**.
2. Search and load a GGUF model (e.g. `Meta-Llama-3-8B-Instruct.Q4_K_M.gguf` or `Phi-3-mini-4k-instruct`).
3. Open **Local Server** tab in LM Studio and click **Start Server** on Port **1234** (Cross-Origin Resource Sharing enabled).
4. Run `ai-service`:
   ```bash
   cd ai-service
   mvn spring-boot:run
   ```
5. Test endpoint via cURL / Postman:
   ```bash
   curl -X POST http://localhost:8080/api/v1/ai/generate \
     -H "Content-Type: application.json" \
     -d '{"taskId": "t1", "prompt": "Hello LM Studio from Spring AI!"}'
   ```

---

## 6. 🌐 Specialized Consumer Integration Guide

When building future specialized microservices, make REST calls to `ai-service`:

| Specialized Consumer | Target Endpoint | Description |
| :--- | :--- | :--- |
| **`web-scraper-service`** | `POST /api/v1/ai/extract-structured-json` | Sends raw HTML string, receives extracted JSON DTO. |
| **`sql-generator-service`** | `POST /api/v1/ai/generate-sql` | Sends DB schema + user query, receives SQL query string. |
| **`ai-research-agent-service`** | `GET /api/v1/ai/stream?prompt=...` | Opens SSE connection to stream multi-step ReAct thought steps. |

---

*Plan updated and aligned with [markdowns/ai_service_design_plan.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/markdowns/ai_service_design_plan.md).*
