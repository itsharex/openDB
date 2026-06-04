package com.opendb.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiProviderType {

    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini", true),
    AZURE_OPENAI("Azure OpenAI", "", "gpt-4o-mini", true),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1/messages", "claude-3-5-sonnet-20241022", true),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat", true),
    OLLAMA("Ollama (本地)", "http://localhost:11434/v1/chat/completions", "llama3.2", false),
    QWEN("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", true),
    ZHIPU("智谱 AI", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash", true),
    MOONSHOT("Moonshot (Kimi)", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k", true),
    CUSTOM("自定义 (OpenAI 兼容)", "", "gpt-4o-mini", false);

    private final String label;
    private final String defaultApiUrl;
    private final String defaultModel;
    private final boolean apiKeyRequired;

    public static AiProviderType fromId(String id) {
        if (id == null || id.isBlank()) {
            return OPENAI;
        }
        for (AiProviderType type : values()) {
            if (type.name().equalsIgnoreCase(id) || type.name().replace('_', '-').equalsIgnoreCase(id)) {
                return type;
            }
        }
        try {
            return valueOf(id.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
