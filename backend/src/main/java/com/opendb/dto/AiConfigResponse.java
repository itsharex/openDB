package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiConfigResponse {

    private boolean enabled;
    private String provider;
    private String providerLabel;
    private String apiUrl;
    private String model;
    private String apiVersion;
    private int timeoutSeconds;
    private double temperature;
    private int maxTokens;
    private boolean hasApiKey;
    private String apiKeyMasked;
}
