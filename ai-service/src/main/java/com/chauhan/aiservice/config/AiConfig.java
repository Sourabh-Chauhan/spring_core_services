package com.chauhan.aiservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Configures the primary ChatClient bean using ChatClient.Builder.
     * Integrates QuestionAnswerAdvisor with VectorStore for transparent RAG context search.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
                .defaultSystem("You are an intelligent AI Assistant for the Spring Core Microservices Platform. " +
                        "Provide accurate, concise, and helpful responses.")
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }
}
