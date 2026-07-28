package com.chauhan.aiservice.service;

import com.chauhan.aiservice.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
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
     * Uses TokenTextSplitter.builder() to split long text into optimal 500-token chunks.
     */
    public DocumentIngestionResponse ingestDocument(DocumentIngestionRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("Document content must not be empty");
        }

        long startTime = System.currentTimeMillis();
        String docId = UUID.randomUUID().toString();

        Map<String, Object> meta = request.metadata() != null ? new HashMap<>(request.metadata()) : new HashMap<>();
        meta.put("source", request.source() != null ? request.source() : "USER_UPLOAD");
        meta.put("category", request.category() != null ? request.category() : "GENERAL");
        meta.put("documentId", docId);

        Document doc = new Document(docId, request.content(), meta);

        // 1. Chunk document text using non-deprecated TokenTextSplitter Builder (Spring AI 2.0.0+)
        TokenTextSplitter textSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(100)
                .build();
        List<Document> chunks = textSplitter.apply(List.of(doc));

        log.info("Ingesting document ID: {} (Split into {} chunks) into PGvector Store...", docId, chunks.size());

        // 2. Embed and store chunks in PGvector
        vectorStore.add(chunks);

        return new DocumentIngestionResponse(
                docId,
                "SUCCESS",
                chunks.size(),
                System.currentTimeMillis() - startTime
        );
    }

    /**
     * Performs RAG query: Similarity search on PGvector -> Context Injection -> LLM Answer.
     */
    public RagQueryResponse askWithContext(RagQueryRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Query must not be empty");
        }

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
                    String text = d.getText();
                    contextBuilder.append(text).append("\n---\n");
                    return text;
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
