package com.opendb.controller;

import com.opendb.dto.ApiResponse;
import com.opendb.dto.CreateDatabaseRequest;
import com.opendb.dto.CreateTableRequest;
import com.opendb.dto.DatabaseObjectInfo;
import com.opendb.dto.DdlResponse;
import com.opendb.dto.IndexInfo;
import com.opendb.model.ManagedConnection;
import com.opendb.service.ConnectionService;
import com.opendb.service.JdbcAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections/{connectionId}")
@RequiredArgsConstructor
public class DatabaseAdminController {

    private final ConnectionService connectionService;
    private final JdbcAdminService adminService;

    @PostMapping("/databases")
    public ApiResponse<Void> createDatabase(@PathVariable String connectionId,
                                            @Valid @RequestBody CreateDatabaseRequest request) {
        ManagedConnection managed = connectionService.get(connectionId);
        adminService.createDatabase(managed, request);
        return ApiResponse.ok("Database created", null);
    }

    @DeleteMapping("/databases/{database}")
    public ApiResponse<Void> dropDatabase(@PathVariable String connectionId,
                                          @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        adminService.dropDatabase(managed, database);
        return ApiResponse.ok("Database dropped", null);
    }

    @GetMapping("/databases/{database}/views")
    public ApiResponse<List<DatabaseObjectInfo>> listViews(@PathVariable String connectionId,
                                                           @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(adminService.listViews(managed, database));
    }

    @GetMapping("/databases/{database}/procedures")
    public ApiResponse<List<DatabaseObjectInfo>> listProcedures(@PathVariable String connectionId,
                                                                @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(adminService.listProcedures(managed, database));
    }

    @GetMapping("/databases/{database}/functions")
    public ApiResponse<List<DatabaseObjectInfo>> listFunctions(@PathVariable String connectionId,
                                                               @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(adminService.listFunctions(managed, database));
    }

    @GetMapping("/databases/{database}/triggers")
    public ApiResponse<List<DatabaseObjectInfo>> listTriggers(@PathVariable String connectionId,
                                                              @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(adminService.listTriggers(managed, database));
    }

    @GetMapping("/databases/{database}/tables/{table}/indexes")
    public ApiResponse<List<IndexInfo>> listIndexes(@PathVariable String connectionId,
                                                    @PathVariable String database,
                                                    @PathVariable String table) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(adminService.listIndexes(managed, database, table));
    }

    @GetMapping("/databases/{database}/tables/{table}/ddl")
    public ApiResponse<DdlResponse> showCreateTable(@PathVariable String connectionId,
                                                    @PathVariable String database,
                                                    @PathVariable String table) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(adminService.showCreateTable(managed, database, table));
    }

    @GetMapping("/databases/{database}/views/{view}/ddl")
    public ApiResponse<DdlResponse> showCreateView(@PathVariable String connectionId,
                                                   @PathVariable String database,
                                                   @PathVariable String view) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(adminService.showCreateView(managed, database, view));
    }

    @PostMapping("/databases/{database}/tables")
    public ApiResponse<Void> createTable(@PathVariable String connectionId,
                                         @PathVariable String database,
                                         @Valid @RequestBody CreateTableRequest request) {
        ManagedConnection managed = connectionService.get(connectionId);
        adminService.createTable(managed, database, request);
        return ApiResponse.ok("Table created", null);
    }

    @DeleteMapping("/databases/{database}/tables/{table}")
    public ApiResponse<Void> dropTable(@PathVariable String connectionId,
                                       @PathVariable String database,
                                       @PathVariable String table) {
        ManagedConnection managed = connectionService.get(connectionId);
        adminService.dropTable(managed, database, table);
        return ApiResponse.ok("Table dropped", null);
    }

    @PostMapping("/databases/{database}/tables/{table}/truncate")
    public ApiResponse<Void> truncateTable(@PathVariable String connectionId,
                                           @PathVariable String database,
                                           @PathVariable String table) {
        ManagedConnection managed = connectionService.get(connectionId);
        adminService.truncateTable(managed, database, table);
        return ApiResponse.ok("Table truncated", null);
    }

    @GetMapping("/databases/{database}/tables/{table}/export")
    public ResponseEntity<String> exportTable(@PathVariable String connectionId,
                                              @PathVariable String database,
                                              @PathVariable String table,
                                              @RequestParam(defaultValue = "10000") int limit) {
        ManagedConnection managed = connectionService.get(connectionId);
        String csv = adminService.exportTableCsv(managed, database, table, limit);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + table + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/databases/{database}/backup-script")
    public ApiResponse<Map<String, String>> backupScript(@PathVariable String connectionId,
                                                         @PathVariable String database) {
        ManagedConnection managed = connectionService.get(connectionId);
        String script = adminService.generateBackupScript(managed, database);
        return ApiResponse.ok(Map.of("script", script));
    }
}
