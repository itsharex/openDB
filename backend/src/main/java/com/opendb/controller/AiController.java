package com.opendb.controller;

import com.opendb.dto.AiChatRequest;
import com.opendb.dto.AiChatResponse;
import com.opendb.dto.AiConfigRequest;
import com.opendb.dto.AiConfigResponse;
import com.opendb.dto.AiProviderPresetResponse;
import com.opendb.dto.ApiResponse;
import com.opendb.exception.OpenDbException;
import com.opendb.model.ManagedConnection;
import com.opendb.service.AiConfigService;
import com.opendb.service.AiService;
import com.opendb.service.ConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiConfigService aiConfigService;
    private final ConnectionService connectionService;

    @GetMapping("/ai/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(aiService.status());
    }

    @GetMapping("/ai/config")
    public ApiResponse<AiConfigResponse> getConfig() {
        return ApiResponse.ok(aiConfigService.getPublicConfig());
    }

    @PutMapping("/ai/config")
    public ApiResponse<AiConfigResponse> updateConfig(@Valid @RequestBody AiConfigRequest request) {
        return ApiResponse.ok("AI 配置已保存", aiConfigService.update(request));
    }

    @GetMapping("/ai/providers")
    public ApiResponse<List<AiProviderPresetResponse>> listProviders() {
        return ApiResponse.ok(aiConfigService.listPresets());
    }

    @PostMapping("/ai/test")
    public ApiResponse<Map<String, String>> testConfig() {
        String reply = aiService.testConnection();
        return ApiResponse.ok("AI 连接测试成功", Map.of("reply", reply));
    }

    @PostMapping("/connections/{connectionId}/ai/chat")
    public ApiResponse<AiChatResponse> chat(@PathVariable String connectionId,
                                            @Valid @RequestBody AiChatRequest request) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(aiService.chat(managed, request));
    }

    @PostMapping(value = "/connections/{connectionId}/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable String connectionId,
                                 @Valid @RequestBody AiChatRequest request) {
        ManagedConnection managed = connectionService.get(connectionId);
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                aiService.streamChat(managed, request, status -> {
                    try {
                        emitter.send(SseEmitter.event().name("status").data(status, MediaType.TEXT_PLAIN));
                    } catch (IOException e) {
                        throw new OpenDbException("流式输出中断: " + e.getMessage(), e);
                    }
                }, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(chunk, MediaType.TEXT_PLAIN));
                    } catch (IOException e) {
                        throw new OpenDbException("流式输出中断: " + e.getMessage(), e);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
            } catch (OpenDbException e) {
                sendStreamError(emitter, e.getMessage());
            } catch (Exception e) {
                sendStreamError(emitter, e.getMessage() != null ? e.getMessage() : "AI 流式请求失败");
            }
        });

        return emitter;
    }

    private void sendStreamError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message, MediaType.TEXT_PLAIN));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
