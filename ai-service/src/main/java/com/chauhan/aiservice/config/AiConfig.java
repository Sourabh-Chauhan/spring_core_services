package com.chauhan.aiservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Configures the primary ChatClient bean using ChatClient.Builder.
     * Sets default system instructions for the microservice platform assistant persona.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are an intelligent AI Assistant for the Spring Core Microservices Platform. " +
                        "Provide accurate, concise, and helpful responses.")
                .build();
    }
}
