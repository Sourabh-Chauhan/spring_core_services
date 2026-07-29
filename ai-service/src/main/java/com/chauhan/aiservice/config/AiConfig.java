package com.chauhan.aiservice.config;

import com.chauhan.aiservice.tools.SystemMetricsTool;
import com.chauhan.aiservice.tools.UserServiceTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Configures the primary ChatClient bean using ChatClient.Builder.
     * Integrates QuestionAnswerAdvisor with VectorStore for transparent RAG context search,
     * and registers default tools for dynamic LLM function calling.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                VectorStore vectorStore,
                                SystemMetricsTool systemMetricsTool,
                                UserServiceTool userServiceTool) {
        return builder
                .defaultSystem("You are an intelligent AI Assistant for the Spring Core Microservices Platform. " +
                        "Provide accurate, concise, and helpful responses. You have access to system tools to query real-time system metrics and user service details when requested.")
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .defaultTools(systemMetricsTool, userServiceTool)
                .build();
    }
}

