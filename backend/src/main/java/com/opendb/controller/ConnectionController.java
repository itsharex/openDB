package com.opendb.controller;

import com.opendb.dto.ApiResponse;
import com.opendb.dto.ConnectionRequest;
import com.opendb.dto.ConnectionResponse;
import com.opendb.model.DatabaseType;
import com.opendb.service.ConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping("/database-types")
    public ApiResponse<List<Map<String, Object>>> databaseTypes() {
        List<Map<String, Object>> types = Arrays.stream(DatabaseType.values())
                .map(type -> Map.<String, Object>of(
                        "value", type.name(),
                        "label", type.getDisplayName(),
                        "defaultPort", type.getDefaultPort(),
                        "supported", type.isSupported()
                ))
                .collect(Collectors.toList());
        return ApiResponse.ok(types);
    }

    @GetMapping("/connections")
    public ApiResponse<List<ConnectionResponse>> list() {
        return ApiResponse.ok(connectionService.list());
    }

    @PostMapping("/connections/test")
    public ApiResponse<ConnectionResponse> test(@Valid @RequestBody ConnectionRequest request) {
        return ApiResponse.ok("Connection successful", connectionService.test(request));
    }

    @PostMapping("/connections")
    public ApiResponse<ConnectionResponse> create(@Valid @RequestBody ConnectionRequest request) {
        return ApiResponse.ok("Connection created", connectionService.create(request));
    }

    @DeleteMapping("/connections/{id}")
    public ApiResponse<Void> disconnect(@PathVariable String id) {
        connectionService.disconnect(id);
        return ApiResponse.ok("Connection disconnected", null);
    }
}
