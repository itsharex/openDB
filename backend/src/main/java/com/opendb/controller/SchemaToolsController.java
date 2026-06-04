package com.opendb.controller;

import com.opendb.dto.ApiResponse;
import com.opendb.dto.DataCompareResponse;
import com.opendb.dto.DataTransferRequest;
import com.opendb.dto.ErDiagramResponse;
import com.opendb.dto.SchemaCompareResponse;
import com.opendb.model.ManagedConnection;
import com.opendb.service.ConnectionService;
import com.opendb.service.JdbcSchemaToolsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/connections/{connectionId}")
@RequiredArgsConstructor
public class SchemaToolsController {

    private final ConnectionService connectionService;
    private final JdbcSchemaToolsService schemaToolsService;

    @GetMapping("/databases/{database}/er-diagram")
    public ApiResponse<ErDiagramResponse> erDiagram(@PathVariable String connectionId,
                                                    @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(schemaToolsService.getErDiagram(managed, database));
    }

    @GetMapping("/schema-compare")
    public ApiResponse<SchemaCompareResponse> compareSchemas(@PathVariable String connectionId,
                                                             @RequestParam String sourceDatabase,
                                                             @RequestParam String targetDatabase) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(schemaToolsService.compareSchemas(managed, sourceDatabase, targetDatabase));
    }

    @PostMapping("/data-transfer")
    public ApiResponse<Map<String, Integer>> transfer(@PathVariable String connectionId,
                                                      @Valid @RequestBody DataTransferRequest request) {
        ManagedConnection managed = connectionService.get(connectionId);
        int rows = schemaToolsService.transferData(managed, request);
        return ApiResponse.ok(Map.of("transferred", rows));
    }

    @GetMapping("/data-compare")
    public ApiResponse<DataCompareResponse> compareData(@PathVariable String connectionId,
                                                        @RequestParam String sourceDatabase,
                                                        @RequestParam String sourceTable,
                                                        @RequestParam String targetDatabase,
                                                        @RequestParam String targetTable) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(schemaToolsService.compareData(
                managed, sourceDatabase, sourceTable, targetDatabase, targetTable));
    }
}
