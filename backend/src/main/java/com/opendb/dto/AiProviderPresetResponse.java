package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiProviderPresetResponse {

    private String id;
    private String label;
    private String defaultApiUrl;
    private String defaultModel;
    private List<String> suggestedModels;
    private boolean apiKeyRequired;
    private String description;
}
