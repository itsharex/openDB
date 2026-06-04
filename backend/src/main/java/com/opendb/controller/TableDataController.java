package com.opendb.controller;

import com.opendb.dto.ApiResponse;
import com.opendb.dto.ImportCsvRequest;
import com.opendb.dto.RowMutationRequest;
import com.opendb.dto.TableMetaResponse;
import com.opendb.model.ManagedConnection;
import com.opendb.service.ConnectionService;
import com.opendb.service.JdbcDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/connections/{connectionId}")
@RequiredArgsConstructor
public class TableDataController {

    private final ConnectionService connectionService;
    private final JdbcDataService dataService;

    @GetMapping("/databases/{database}/tables/{table}/meta")
    public ApiResponse<TableMetaResponse> meta(@PathVariable String connectionId,
                                               @PathVariable String database,
                                               @PathVariable String table) {
        ManagedConnection managed = connectionService.get(connectionId);
        return ApiResponse.ok(dataService.getTableMeta(managed, database, table));
    }

    @PostMapping("/databases/{database}/tables/{table}/rows")
    public ApiResponse<Void> insert(@PathVariable String connectionId,
                                    @PathVariable String database,
                                    @PathVariable String table,
                                    @Valid @RequestBody RowMutationRequest request) {
        request.setDatabase(database);
        request.setTable(table);
        ManagedConnection managed = connectionService.get(connectionId);
        dataService.insertRow(managed, request);
        return ApiResponse.ok("Row inserted", null);
    }

    @PutMapping("/databases/{database}/tables/{table}/rows")
    public ApiResponse<Void> update(@PathVariable String connectionId,
                                      @PathVariable String database,
                                      @PathVariable String table,
                                      @Valid @RequestBody RowMutationRequest request) {
        request.setDatabase(database);
        request.setTable(table);
        ManagedConnection managed = connectionService.get(connectionId);
        dataService.updateRow(managed, request);
        return ApiResponse.ok("Row updated", null);
    }

    @DeleteMapping("/databases/{database}/tables/{table}/rows")
    public ApiResponse<Void> delete(@PathVariable String connectionId,
                                      @PathVariable String database,
                                      @PathVariable String table,
                                      @Valid @RequestBody RowMutationRequest request) {
        request.setDatabase(database);
        request.setTable(table);
        ManagedConnection managed = connectionService.get(connectionId);
        dataService.deleteRow(managed, request);
        return ApiResponse.ok("Row deleted", null);
    }

    @PostMapping("/databases/{database}/tables/{table}/import-csv")
    public ApiResponse<Map<String, Integer>> importCsv(@PathVariable String connectionId,
                                                        @PathVariable String database,
                                                        @PathVariable String table,
                                                        @Valid @RequestBody ImportCsvRequest request) {
        request.setDatabase(database);
        request.setTable(table);
        ManagedConnection managed = connectionService.get(connectionId);
        int imported = dataService.importCsv(managed, request);
        return ApiResponse.ok(Map.of("imported", imported));
    }
}
