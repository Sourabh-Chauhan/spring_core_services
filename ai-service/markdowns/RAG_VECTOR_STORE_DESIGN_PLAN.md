# 🧠 RAG (Retrieval-Augmented Generation) & Vector Store Setup Plan

> **Target Service:** `ai-service` (Port `8085`)  
> **Database:** PostgreSQL (`postgres-auth` on Port `5001`, DB: `auth_db`)  
> **Target Audience:** Developers learning AI, Vector Databases, and Spring AI  
> **Goal:** Enable PostgreSQL `vector` extension, build `RagService` for document ingestion & similarity search, configure Spring AI `QuestionAnswerAdvisor` / `VectorStoreAdvisor`, and expose RAG REST endpoints in `AiController`.

---

## 📑 Goal & Conceptual Guide for Beginners

### What is RAG & Why Do We Need It?
Standard AI Large Language Models (LLMs) like GPT-4o or Llama 3 do not know private microservice enterprise data, internal database schemas, or company knowledge bases.

**Retrieval-Augmented Generation (RAG)** bridges this gap without expensive model re-training:
1. **Ingestion (ETL):** Custom text documents are split into chunks, converted into 1536-dimensional floating-point numbers (**Vector Embeddings**) by an `EmbeddingModel`, and saved in PostgreSQL using the `pgvector` extension.
2. **Retrieval:** When a user asks a question, a **Cosine Distance Similarity Search** finds the top matching text chunks in PostgreSQL.
3. **Generation:** Spring AI injects those matching text chunks as **Context** into the LLM prompt, allowing the AI to answer accurately using your private data without hallucinating.

```text
  [Raw Document / Text]
           │
           ▼
   [EmbeddingModel] ──► Converts to Vector Array [0.012, -0.453, ...]
           │
           ▼
┌───────────────────────────┐
│ PostgreSQL (pgvector)     │  ◄── Stores Vector Embeddings (HNSW Index)
└─────────────┬─────────────┘
              │
  User Query  │ 1. Similarity Search (COSINE_DISTANCE)
              ▼
┌───────────────────────────┐
│ Top Matching Documents    │
└─────────────┬─────────────┘
              │ 2. Inject as Context into Prompt
              ▼
┌───────────────────────────┐
│ LLM (OpenAI / LM Studio) │ ──► 3. Accurate Grounded Answer
└───────────────────────────┘
```

---

## 🛠 Implementation Blueprint & Code Changes

---

### Component 1: Domain DTOs (`com.chauhan.aiservice.model`)

#### `DocumentIngestionRequest.java`
```java
package com.chauhan.aiservice.model;

import java.util.Map;

public record DocumentIngestionRequest(
        String content,
        String source,
        String category,
        Map<String, Object> metadata
) {}
```

#### `DocumentIngestionResponse.java`
```java
package com.chauhan.aiservice.model;

public record DocumentIngestionResponse(
        String documentId,
        String status,
        int chunkCount,
        long executionTimeMs
) {}
```

#### `RagQueryRequest.java`
```java
package com.chauhan.aiservice.model;

public record RagQueryRequest(
        String query,
        int topK,                   // Default 3 top matching documents
        double similarityThreshold  // Default 0.70
) {}
```

#### `RagQueryResponse.java`
```java
package com.chauhan.aiservice.model;

import java.util.List;

public record RagQueryResponse(
        String query,
        String answer,
        List<String> sourceDocuments,
        long executionTimeMs
) {}
```

---

### Component 2: RAG Service (`com.chauhan.aiservice.service.RagService`)

#### `RagService.java`
```java
package com.chauhan.aiservice.service;

import com.chauhan.aiservice.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    /**
     * Ingests a text document into PostgreSQL PGvector Store.
     */
    public DocumentIngestionResponse ingestDocument(DocumentIngestionRequest request) {
        long startTime = System.currentTimeMillis();
        String docId = UUID.randomUUID().toString();

        Map<String, Object> meta = request.metadata() != null ? new HashMap<>(request.metadata()) : new HashMap<>();
        meta.put("source", request.source() != null ? request.source() : "USER_UPLOAD");
        meta.put("category", request.category() != null ? request.category() : "GENERAL");
        meta.put("documentId", docId);

        Document doc = new Document(docId, request.content(), meta);
        log.info("Ingesting document ID: {} into PGvector Store...", docId);

        vectorStore.add(List.of(doc));

        return new DocumentIngestionResponse(
                docId,
                "SUCCESS",
                1,
                System.currentTimeMillis() - startTime
        );
    }

    /**
     * Performs RAG query: Similarity search on PGvector -> Context Injection -> LLM Answer.
     */
    public RagQueryResponse askWithContext(RagQueryRequest request) {
        long startTime = System.currentTimeMillis();
        int topK = request.topK() > 0 ? request.topK() : 3;

        log.info("Performing PGvector similarity search for query: {}", request.query());

        // 1. Perform Similarity Search in PostgreSQL
        List<Document> similarDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.query())
                        .topK(topK)
                        .build()
        );

        // 2. Build context string and source list
        StringBuilder contextBuilder = new StringBuilder();
        List<String> sources = similarDocs.stream()
                .map(d -> {
                    contextBuilder.append(d.getContent()).append("\n---\n");
                    return d.getContent();
                })
                .toList();

        // 3. Prompt LLM with injected context
        String answer = chatClient.prompt()
                .system("""
                    Answer the user question using ONLY the provided context.
                    If the context does not contain the answer, politely state that the information is unavailable.
                    
                    Context:
                    """ + contextBuilder)
                .user(request.query())
                .call()
                .content();

        return new RagQueryResponse(
                request.query(),
                answer,
                sources,
                System.currentTimeMillis() - startTime
        );
    }
}
```

---

### Component 3: Spring AI Configuration & Advisor (`com.chauhan.aiservice.config.AiConfig`)

#### `AiConfig.java`
Wire `VectorStore` into `ChatClient.Builder` using Spring AI `QuestionAnswerAdvisor`:

```java
package com.chauhan.aiservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
                .defaultSystem("You are an intelligent AI Assistant for the Spring Core Microservices Platform. " +
                        "Provide accurate, concise, and helpful responses.")
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .build();
    }
}
```

---

### Component 4: REST API Endpoints (`com.chauhan.aiservice.controller.AiController`)

#### `AiController.java`
Expose RAG ingestion and Q&A endpoints:

```java
    /**
     * Ingest custom document into PostgreSQL PGvector Store.
     */
    @PostMapping("/rag/ingest")
    public ResponseEntity<DocumentIngestionResponse> ingestDocument(@RequestBody DocumentIngestionRequest request) {
        log.info("Received RAG document ingestion request");
        return ResponseEntity.ok(ragService.ingestDocument(request));
    }

    /**
     * Perform RAG Query: Similarity search in PGvector + Context Injection + LLM Answer.
     */
    @PostMapping("/rag/ask")
    public ResponseEntity<RagQueryResponse> askWithContext(@RequestBody RagQueryRequest request) {
        log.info("Received RAG Q&A request");
        return ResponseEntity.ok(ragService.askWithContext(request));
    }
```

---

## 🧪 Verification Plan

### Automated Tests
1. Run Maven compilation check:
   ```bash
   mvn compile -pl ai-service
   ```

### Manual Verification
1. **Ingest Knowledge:**
   `POST http://localhost:8080/api/v1/ai/rag/ingest`
   ```json
   {
     "content": "Spring Core Microservices uses Auth Service on Port 8083, Gateway Service on Port 8080, and AI Service on Port 8085.",
     "source": "architecture-doc",
     "category": "INFRASTRUCTURE"
   }
   ```

2. **Query Knowledge via RAG:**
   `POST http://localhost:8080/api/v1/ai/rag/ask`
   ```json
   {
     "query": "Which port does AI Service run on according to infrastructure doc?",
     "topK": 3
   }
   ```
