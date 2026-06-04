package com.opendb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiConfigRequest {

    private boolean enabled;

    @NotBlank
    private String provider;

    @NotBlank
    private String apiUrl;

    private String apiKey;

    @NotBlank
    private String model;

    private String apiVersion;

    @NotNull
    @Min(10)
    @Max(300)
    private Integer timeoutSeconds;

    @NotNull
    @Min(0)
    @Max(2)
    private Double temperature;

    @NotNull
    @Min(256)
    @Max(128000)
    private Integer maxTokens;
}
