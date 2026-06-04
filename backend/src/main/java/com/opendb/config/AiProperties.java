package com.opendb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "opendb.ai")
public class AiProperties {

    private boolean enabled = false;
    private String apiUrl = "https://api.openai.com/v1/chat/completions";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int timeoutSeconds = 60;
}
