package com.opendb.model;

import lombok.Data;

@Data
public class AiConfig {

    private boolean enabled = false;
    private String provider = AiProviderType.OPENAI.name();
    private String apiUrl = AiProviderType.OPENAI.getDefaultApiUrl();
    private String apiKey = "";
    private String model = AiProviderType.OPENAI.getDefaultModel();
    private String apiVersion = "2024-02-15-preview";
    private int timeoutSeconds = 60;
    private double temperature = 0.2;
    private int maxTokens = 4096;
}
