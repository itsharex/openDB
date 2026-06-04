package com.opendb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opendb.dto.AiChatRequest;
import com.opendb.dto.AiChatResponse;
import com.opendb.exception.OpenDbException;
import com.opendb.model.AiConfig;
import com.opendb.model.AiProviderType;
import com.opendb.model.ManagedConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiConfigService aiConfigService;
    private final JdbcDatabaseService databaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private record ChatPrompts(AiConfig config, String systemPrompt, String userPrompt, String disabledMessage) {
    }

    public AiChatResponse chat(ManagedConnection managed, AiChatRequest request) {
        ChatPrompts prompts = buildPrompts(managed, request);
        if (prompts.disabledMessage() != null) {
            return AiChatResponse.builder()
                    .enabled(false)
                    .content(prompts.disabledMessage())
                    .build();
        }
        if (!aiConfigService.isConfigured()) {
            throw new OpenDbException("AI 已启用但未完成配置，请在「AI → AI 设置」中填写 API 信息");
        }

        try {
            String content = invokeProvider(prompts.config(), prompts.systemPrompt(), prompts.userPrompt());
            return AiChatResponse.builder()
                    .enabled(true)
                    .content(content)
                    .build();
        } catch (OpenDbException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI request failed", e);
            throw new OpenDbException("AI 请求失败: " + e.getMessage(), e);
        }
    }

    public void streamChat(ManagedConnection managed, AiChatRequest request,
                           Consumer<String> onStatus, Consumer<String> onChunk) {
        ChatPrompts prompts = buildPrompts(managed, request, onStatus);
        if (prompts.disabledMessage() != null) {
            onStatus.accept("完成");
            onChunk.accept(prompts.disabledMessage());
            return;
        }
        if (!aiConfigService.isConfigured()) {
            throw new OpenDbException("AI 已启用但未完成配置，请在「AI → AI 设置」中填写 API 信息");
        }

        try {
            onStatus.accept("正在思考...");
            java.util.concurrent.atomic.AtomicBoolean generating = new java.util.concurrent.atomic.AtomicBoolean(false);
            streamProvider(prompts.config(), prompts.systemPrompt(), prompts.userPrompt(), chunk -> {
                if (generating.compareAndSet(false, true)) {
                    onStatus.accept("正在生成回答...");
                }
                onChunk.accept(chunk);
            });
            onStatus.accept("完成");
        } catch (OpenDbException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI stream request failed", e);
            throw new OpenDbException("AI 请求失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> status() {
        AiConfig config = aiConfigService.getEffectiveConfig();
        AiProviderType provider = AiProviderType.fromId(config.getProvider());
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", config.isEnabled());
        status.put("configured", aiConfigService.isConfigured());
        status.put("provider", provider.name());
        status.put("providerLabel", provider.getLabel());
        status.put("model", config.getModel());
        status.put("apiUrl", config.getApiUrl());
        return status;
    }

    public String testConnection() {
        AiConfig config = aiConfigService.getEffectiveConfig();
        if (!config.isEnabled()) {
            throw new OpenDbException("请先启用 AI 助手");
        }
        if (!aiConfigService.isConfigured()) {
            throw new OpenDbException("请先完成 AI 配置");
        }
        try {
            return invokeProvider(config, "You are a helpful assistant.", "Reply with exactly: OK");
        } catch (OpenDbException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenDbException("AI 连接测试失败: " + e.getMessage(), e);
        }
    }

    private ChatPrompts buildPrompts(ManagedConnection managed, AiChatRequest request) {
        return buildPrompts(managed, request, null);
    }

    private ChatPrompts buildPrompts(ManagedConnection managed, AiChatRequest request, Consumer<String> onStatus) {
        if (onStatus != null) {
            onStatus.accept("正在准备...");
        }

        AiConfig config = aiConfigService.getEffectiveConfig();
        if (!config.isEnabled()) {
            return new ChatPrompts(config, null, null, buildDisabledResponse(request));
        }

        String schemaContext = "";
        if (request.getDatabase() != null && !request.getDatabase().isBlank()) {
            if (onStatus != null) {
                onStatus.accept("正在分析数据库结构...");
            }
            schemaContext = databaseService.getSchemaSummary(managed, request.getDatabase());
        }

        String dialectName = managed.getDialect().getAiDialectName();
        String systemPrompt = """
                You are openDB AI assistant, an expert database helper.
                Generate safe, readable SQL for %s when asked.
                Prefer SELECT queries. Warn before destructive operations.
                Return concise answers in Markdown. When providing SQL, wrap it in ```sql blocks.
                SQL must be valid %s syntax with proper spaces between keywords, identifiers, and operators
                (for example: `name AS alias`, `FROM photos p`, `JOIN categories c ON`, `ORDER BY`, `LIMIT 10`).
                Never glue keywords to identifiers like `nameASalias` or `FROMtable`.
                """.formatted(dialectName, dialectName);

        String userPrompt = request.getPrompt();
        if (!schemaContext.isBlank()) {
            userPrompt = "Schema context:\n" + schemaContext + "\n\nUser request:\n" + request.getPrompt();
        }
        if (request.getContextSql() != null && !request.getContextSql().isBlank()) {
            if (onStatus != null) {
                onStatus.accept("正在分析 SQL 上下文...");
            }
            userPrompt += "\n\nCurrent SQL editor content:\n```sql\n" + request.getContextSql() + "\n```";
        }

        return new ChatPrompts(config, systemPrompt, userPrompt, null);
    }

    private String invokeProvider(AiConfig config, String systemPrompt, String userPrompt) throws Exception {
        AiProviderType provider = AiProviderType.fromId(config.getProvider());
        return switch (provider) {
            case ANTHROPIC -> callAnthropic(config, systemPrompt, userPrompt);
            case AZURE_OPENAI -> callAzureOpenAi(config, systemPrompt, userPrompt);
            default -> callOpenAiCompatible(config, systemPrompt, userPrompt);
        };
    }

    private void streamProvider(AiConfig config, String systemPrompt, String userPrompt, Consumer<String> onChunk)
            throws Exception {
        AiProviderType provider = AiProviderType.fromId(config.getProvider());
        switch (provider) {
            case ANTHROPIC -> streamAnthropic(config, systemPrompt, userPrompt, onChunk);
            case AZURE_OPENAI -> streamAzureOpenAi(config, systemPrompt, userPrompt, onChunk);
            default -> streamOpenAiCompatible(config, systemPrompt, userPrompt, onChunk);
        }
    }

    private String callOpenAiCompatible(AiConfig config, String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getModel());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", config.getMaxTokens());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        return parseOpenAiCompatibleResponse(sendRequest(builder.build()));
    }

    private void streamOpenAiCompatible(AiConfig config, String systemPrompt, String userPrompt, Consumer<String> onChunk)
            throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getModel());
        payload.put("stream", true);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", config.getMaxTokens());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        streamOpenAiCompatibleBody(sendStreamingRequest(builder.build()), onChunk);
    }

    private String callAzureOpenAi(AiConfig config, String systemPrompt, String userPrompt) throws Exception {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new OpenDbException("Azure OpenAI 需要 API Key");
        }

        Map<String, Object> payload = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", config.getTemperature(),
                "max_tokens", config.getMaxTokens()
        );

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("api-key", config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        return parseOpenAiCompatibleResponse(sendRequest(httpRequest));
    }

    private void streamAzureOpenAi(AiConfig config, String systemPrompt, String userPrompt, Consumer<String> onChunk)
            throws Exception {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new OpenDbException("Azure OpenAI 需要 API Key");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("stream", true);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", config.getMaxTokens());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("api-key", config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        streamOpenAiCompatibleBody(sendStreamingRequest(httpRequest), onChunk);
    }

    private String callAnthropic(AiConfig config, String systemPrompt, String userPrompt) throws Exception {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new OpenDbException("Anthropic 需要 API Key");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", config.getModel());
        payload.put("max_tokens", config.getMaxTokens());
        payload.put("system", systemPrompt);
        payload.put("temperature", config.getTemperature());
        ArrayNode messages = payload.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        String body = sendRequest(httpRequest);
        JsonNode root = objectMapper.readTree(body);
        JsonNode content = root.path("content").path(0).path("text");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new OpenDbException("Anthropic API 返回空响应");
        }
        return content.asText();
    }

    private void streamAnthropic(AiConfig config, String systemPrompt, String userPrompt, Consumer<String> onChunk)
            throws Exception {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new OpenDbException("Anthropic 需要 API Key");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", config.getModel());
        payload.put("max_tokens", config.getMaxTokens());
        payload.put("system", systemPrompt);
        payload.put("stream", true);
        payload.put("temperature", config.getTemperature());
        ArrayNode messages = payload.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        streamAnthropicBody(sendStreamingRequest(httpRequest), onChunk);
    }

    private String sendRequest(HttpRequest httpRequest) throws Exception {
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new OpenDbException("AI API 错误 (" + response.statusCode() + "): " + summarizeErrorBody(response.body()));
        }
        return response.body();
    }

    private InputStream sendStreamingRequest(HttpRequest httpRequest) throws Exception {
        HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new OpenDbException("AI API 错误 (" + response.statusCode() + "): " + summarizeErrorBody(body));
        }
        return response.body();
    }

    private void streamOpenAiCompatibleBody(InputStream body, Consumer<String> onChunk) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(data);
                JsonNode delta = root.path("choices").path(0).path("delta").path("content");
                if (!delta.isMissingNode() && !delta.isNull()) {
                    onChunk.accept(delta.asText());
                }
            }
        }
    }

    private void streamAnthropicBody(InputStream body, Consumer<String> onChunk) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(data);
                if ("content_block_delta".equals(root.path("type").asText())) {
                    JsonNode text = root.path("delta").path("text");
                    if (!text.isMissingNode() && !text.isNull()) {
                        onChunk.accept(text.asText());
                    }
                }
            }
        }
    }

    private String parseOpenAiCompatibleResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new OpenDbException("AI API 返回空响应");
        }
        return content.asText();
    }

    private String summarizeErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return "empty response";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.path("error").path("message");
            if (!message.isMissingNode()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return body.length() > 300 ? body.substring(0, 300) + "..." : body;
    }

    private String buildDisabledResponse(AiChatRequest request) {
        return """
                AI 助手当前未启用。

                请在菜单 **AI → AI 设置...** 中：
                1. 勾选「启用 AI 助手」
                2. 选择提供商（OpenAI、Claude、DeepSeek、Ollama 等）
                3. 填写 API 地址、Key 和模型
                4. 点击「测试连接」验证

                你的问题是：%s
                """.formatted(request.getPrompt());
    }
}
