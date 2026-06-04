package com.opendb.controller;

import com.opendb.dto.ApiResponse;
import com.opendb.dto.ColumnInfo;
import com.opendb.dto.SchemaSummary;
import com.opendb.dto.SqlExecuteRequest;
import com.opendb.dto.SqlExecuteResponse;
import com.opendb.dto.TableInfo;
import com.opendb.model.ManagedConnection;
import com.opendb.service.ConnectionService;
import com.opendb.service.JdbcDatabaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/connections/{connectionId}")
@RequiredArgsConstructor
public class DatabaseController {

    private final ConnectionService connectionService;
    private final JdbcDatabaseService databaseService;

    @GetMapping("/databases")
    public ApiResponse<List<String>> listDatabases(@PathVariable String connectionId) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(databaseService.listDatabases(managed));
    }

    @GetMapping("/databases/{database}/tables")
    public ApiResponse<List<TableInfo>> listTables(@PathVariable String connectionId,
                                                   @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(databaseService.listTables(managed, database));
    }

    @GetMapping("/databases/{database}/tables/{table}/columns")
    public ApiResponse<List<ColumnInfo>> listColumns(@PathVariable String connectionId,
                                                     @PathVariable String database,
                                                     @PathVariable String table) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(databaseService.listColumns(managed, database, table));
    }

    @GetMapping("/databases/{database}/schema")
    public ApiResponse<SchemaSummary> getSchema(@PathVariable String connectionId,
                                                @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(databaseService.getSchema(managed, database));
    }

    @GetMapping("/databases/{database}/tables/{table}/preview")
    public ApiResponse<SqlExecuteResponse> previewTable(@PathVariable String connectionId,
                                                        @PathVariable String database,
                                                        @PathVariable String table,
                                                        @RequestParam(defaultValue = "100") int limit) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(databaseService.previewTable(managed, database, table, limit));
    }

    @PostMapping("/query")
    public ApiResponse<SqlExecuteResponse> execute(@PathVariable String connectionId,
                                                   @Valid @RequestBody SqlExecuteRequest request) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(databaseService.execute(managed, request));
    }
}
