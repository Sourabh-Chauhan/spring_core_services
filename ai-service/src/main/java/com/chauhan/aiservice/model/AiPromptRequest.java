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
