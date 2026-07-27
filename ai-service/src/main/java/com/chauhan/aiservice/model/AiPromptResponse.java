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
