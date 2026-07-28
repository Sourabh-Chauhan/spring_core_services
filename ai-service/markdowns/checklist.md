# 🤖 AI Service (`ai-service`) Implementation Checklist

This checklist tracks the step-by-step development goals, components, and integrations for **`ai-service`** based on:
- 📖 [markdowns/ai_service_design_plan.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/markdowns/ai_service_design_plan.md)
- 📖 [ai-service/markdowns/AI_SERVICE_IMPLEMENTATION_PLAN.md](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/ai-service/markdowns/AI_SERVICE_IMPLEMENTATION_PLAN.md)

---

## 1. Project Setup & Multi-Module Maven Configuration

- [x] **Parent POM Setup (`pom.xml`)**
  - [x] Register `<module>ai-service</module>` under `<modules>` in root `pom.xml`.
  - [x] Add `<spring-ai.version>2.0.0</spring-ai.version>` property to root `pom.xml`.
  - [x] Add `spring-ai-bom` under `<dependencyManagement>` in root `pom.xml`.
  - [x] Add Spring Milestones Repository (`https://repo.spring.io/milestone`) to root `pom.xml`.

- [x] **Module POM Setup (`ai-service/pom.xml`)**
  - [x] Set `<parent>` to `com.chauhan:spring-core-services:0.0.1-SNAPSHOT` (`../pom.xml`).
  - [x] Add `spring-boot-starter-webmvc` and `spring-boot-starter-webflux` (for WebClient & SSE Streaming).
  - [x] Add `spring-cloud-starter-netflix-eureka-client`.
  - [x] Add Spring AI Starters:
    - [x] `spring-ai-starter-model-openai` (LM Studio & OpenAI API compatible).
    - [x] `spring-ai-starter-vector-store-pgvector` (PGvector Store).
    - [x] `spring-ai-vector-store-advisor` (Context Search Advisors).
  - [x] Add `lombok` and testing dependencies (`spring-boot-starter-test`, `webflux-test`, `webmvc-test`).
  - [x] Configure `spring-boot-maven-plugin` and `maven-compiler-plugin` for Lombok processing.
  - [x] Verify Maven build validity with `mvn validate -pl ai-service` (**BUILD SUCCESS**).

---

## 2. Service Configuration & Service Discovery

- [x] **Application Configuration (`application.yaml`)**
  - [x] Configure `server.port: 8085` and `spring.application.name: ai-service`.
  - [x] Enable `@EnableDiscoveryClient` in `AiServiceApplication.java`.
  - [x] Configure Eureka Client discovery settings (`eureka.client.service-url.defaultZone: http://localhost:8761/eureka/`).
  - [x] Configure Spring AI OpenAI connection for Local LM Studio:
    - Base URL: `http://localhost:1234/v1`
    - API Key: `lm-studio`
    - Model: `local-model`
  - [x] Configure PostgreSQL datasource for PGvector database (`jdbc:postgresql://localhost:5001/auth_db`).
  - [x] Configure `spring.ai.vectorstore.pgvector` properties (`index-type: HNSW`, `distance-type: COSINE_DISTANCE`, `dimensions: 1536`).

- [x] **Spring AI Core Configuration Bean**
  - [x] Create `com.chauhan.aiservice.config.AiConfig`.
  - [x] Configure `ChatClient` bean using `ChatClient.Builder` with default system instruction persona.

---

## 3. Provider Strategy Architecture (LM Studio & Cloud Fallbacks)

- [x] **Provider Strategy Layer**
  - [x] Create `ProviderType` enum (`LM_STUDIO`, `GEMINI`, `CLAUDE`, `OPENAI`).
  - [x] Create `AiPromptRequest` DTO (taskId, prompt, systemInstruction, preferredProvider).
  - [x] Create `AiPromptResponse` DTO (taskId, output, providerUsed, executionTimeMs).
  - [x] Create `AiProviderStrategy` interface (`generate()`, `generateStream()`, `isAvailable()`).
  - [x] Implement `LmStudioLocalProvider` using `OpenAiChatModel` pointing to `http://localhost:1234/v1`.
  - [x] Create `AiModelRouter` component for dynamic provider selection and fallback handling.

---

## 3.1 Local Prompt Enrichment & Token Optimization Engine

- [ ] **Prompt Enrichment Engine (`PROMPT_ENRICHMENT_DESIGN_PLAN.md`)**
  - [ ] Create `IntentCategory` enum (`GENERAL_CHAT`, `TEXT_TO_SQL`, `STRUCTURED_EXTRACTION`, `DOCUMENT_QA`, `SUMMARIZATION`, `OUT_OF_SCOPE`).
  - [ ] Create `EnrichedPromptResult` Java Record (`rawPrompt`, `optimizedPrompt`, `primaryIntent`, `confidenceScore`, `canHandleLocally`, `estimatedTokenSavings`).
  - [ ] Create `PromptEnricherService` component using local LM Studio for zero-cost prompt compression & intent extraction.

---

## 4. Specialized Consumer REST & SSE APIs

- [ ] **Create `AiController` (`/api/v1/ai/**`)**
  - [x] Implement `POST /api/v1/ai/generate` – Synchronous text generation via `AiModelRouter`.
  - [ ] Implement `POST /api/v1/ai/enrich-prompt` – Pre-process & optimize user prompt via local LM Studio.
  - [ ] Implement `POST /api/v1/ai/generate-smart` – Smart generation pipeline (local enrichment -> router).
  - [ ] Implement `GET /api/v1/ai/stream` – Server-Sent Events (`Flux<String>`) streaming for **`ai-research-agent-service`**.
  - [ ] Implement `POST /api/v1/ai/extract-structured-json` – Structured entity extraction for **`web-scraper-service`**.
  - [ ] Implement `POST /api/v1/ai/generate-sql` – Natural language to SQL query converter for **`sql-generator-service`**.
  - [ ] Implement `POST /api/v1/ai/analyze-sentiment` – Structured sentiment analysis returning Java Record `SentimentAnalysisResponse`.

---

## 5. RAG (Retrieval-Augmented Generation) & Vector Store Setup

- [ ] **Database Vector Extension**
  - [ ] Enable PostgreSQL `vector` extension in database container (`postgres-auth:5001`) using `CREATE EXTENSION IF NOT EXISTS vector;`.
- [ ] **Vector Search & Ingestion Engine**
  - [ ] Create `com.chauhan.aiservice.service.RagService`.
  - [ ] Implement document ingestion (`vectorStore.add()`).
  - [ ] Implement similarity search & prompt context injection (`vectorStore.similaritySearch()`).
  - [ ] Integrate Spring AI `QuestionAnswerAdvisor` / `VectorStoreAdvisor` into `ChatClient`.

---

## 6. Spring AI Tools & Function Calling

- [ ] **Dynamic Tool Registration**
  - [ ] Create `@Tool` annotated components (e.g. `SystemMetricsTool`, `UserServiceTool`).
  - [ ] Bind tools to `ChatClient` prompts to allow LLM to trigger Java methods dynamically.

---

## 7. Gateway Routing, Resilience & Integration Verification

- [ ] **API Gateway Integration**
  - [ ] Add `ai-service` route (`/api/v1/ai/**`) in `gateway-service/src/main/resources/application.yml`.
  - [ ] Add Resilience4j Circuit Breaker (`aiCircuitBreaker`) with fallback forward `/fallback/ai`.
  - [ ] Add Redis IP RateLimiter to protect AI endpoints from excessive API calls.
- [ ] **Local LM Studio End-to-End Verification**
  - [ ] Launch LM Studio on port `1234` with a local GGUF model (e.g., Llama 3).
  - [ ] Start `Eureka-server`, `gateway-service`, and `ai-service`.
  - [ ] Test synchronous, streaming SSE, and structured JSON endpoints via cURL / Postman.

---

*Checklist created for `ai-service` in `spring_core_services` project.*
