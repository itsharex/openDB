package com.opendb.service;

import com.opendb.config.ConnectionProperties;
import com.opendb.dialect.SqlDialect;
import com.opendb.dto.ColumnInfo;
import com.opendb.dto.ImportCsvRequest;
import com.opendb.dto.RowMutationRequest;
import com.opendb.dto.TableMetaResponse;
import com.opendb.exception.OpenDbException;
import com.opendb.model.ManagedConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JdbcDataService {

    private final ConnectionProperties connectionProperties;
    private final JdbcDatabaseService databaseService;

    public TableMetaResponse getTableMeta(ManagedConnection managed, String database, String table) {
        List<ColumnInfo> columns = databaseService.listColumns(managed, database, table);
        List<String> primaryKeys = columns.stream()
                .filter(column -> "PRI".equals(column.getKey()))
                .map(ColumnInfo::getName)
                .toList();
        return TableMetaResponse.builder()
                .database(database)
                .table(table)
                .columns(columns)
                .primaryKeys(primaryKeys)
                .build();
    }

    public void insertRow(ManagedConnection managed, RowMutationRequest request) {
        JdbcHelper.useNamespace(managed, request.getDatabase());
        SqlDialect dialect = JdbcHelper.dialect(managed);
        List<String> columns = new ArrayList<>(request.getData().keySet());
        String columnSql = columns.stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", "));
        String valueSql = columns.stream().map(column -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + dialect.qualifyTable(request.getDatabase(), request.getTable())
                + " (" + columnSql + ") VALUES (" + valueSql + ")";
        Connection connection = managed.getActiveConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindValues(statement, columns, request.getData());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new OpenDbException("Insert failed: " + e.getMessage(), e);
        }
    }

    public void updateRow(ManagedConnection managed, RowMutationRequest request) {
        if (request.getPrimaryKey() == null || request.getPrimaryKey().isEmpty()) {
            throw new OpenDbException("Primary key is required for update");
        }
        JdbcHelper.useNamespace(managed, request.getDatabase());
        SqlDialect dialect = JdbcHelper.dialect(managed);
        List<String> setColumns = request.getData().keySet().stream()
                .filter(key -> !request.getPrimaryKey().containsKey(key))
                .toList();
        if (setColumns.isEmpty()) {
            throw new OpenDbException("No columns to update");
        }
        String setSql = setColumns.stream()
                .map(column -> dialect.quoteIdentifier(column) + " = ?")
                .collect(Collectors.joining(", "));
        WhereClause whereClause = buildWhereClause(dialect, request.getPrimaryKey());
        String sql = "UPDATE " + dialect.qualifyTable(request.getDatabase(), request.getTable())
                + " SET " + setSql + " WHERE " + whereClause.sql();
        Connection connection = managed.getActiveConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String column : setColumns) {
                bindValue(statement, index++, request.getData().get(column));
            }
            bindWhereValues(statement, index, request.getPrimaryKey(), whereClause);
            int affected = statement.executeUpdate();
            if (affected == 0) {
                throw new OpenDbException("No row updated. Check row identifier values.");
            }
        } catch (SQLException e) {
            throw new OpenDbException("Update failed: " + e.getMessage(), e);
        }
    }

    public void deleteRow(ManagedConnection managed, RowMutationRequest request) {
        if (request.getPrimaryKey() == null || request.getPrimaryKey().isEmpty()) {
            throw new OpenDbException("Row identifier is required for delete");
        }
        JdbcHelper.useNamespace(managed, request.getDatabase());
        SqlDialect dialect = JdbcHelper.dialect(managed);
        WhereClause whereClause = buildWhereClause(dialect, request.getPrimaryKey());
        String sql = "DELETE FROM " + dialect.qualifyTable(request.getDatabase(), request.getTable())
                + " WHERE " + whereClause.sql();
        Connection connection = managed.getActiveConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindWhereValues(statement, 1, request.getPrimaryKey(), whereClause);
            int affected = statement.executeUpdate();
            if (affected == 0) {
                throw new OpenDbException("No row deleted. Check row identifier values.");
            }
        } catch (SQLException e) {
            throw new OpenDbException("Delete failed: " + e.getMessage(), e);
        }
    }

    public int importCsv(ManagedConnection managed, ImportCsvRequest request) {
        useDatabaseWithTimeout(managed, request.getDatabase());
        String[] lines = request.getCsvContent().split("\\r?\\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            return 0;
        }
        List<String> headers;
        int startLine;
        if (request.isHasHeader()) {
            headers = parseCsvLine(lines[0]);
            startLine = 1;
        } else {
            headers = databaseService.listColumns(managed, request.getDatabase(), request.getTable()).stream()
                    .map(ColumnInfo::getName)
                    .toList();
            startLine = 0;
        }
        int imported = 0;
        for (int i = startLine; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(lines[i]);
            Map<String, Object> data = new LinkedHashMap<>();
            for (int j = 0; j < headers.size() && j < values.size(); j++) {
                data.put(headers.get(j), nullify(values.get(j)));
            }
            RowMutationRequest rowRequest = new RowMutationRequest();
            rowRequest.setDatabase(request.getDatabase());
            rowRequest.setTable(request.getTable());
            rowRequest.setData(data);
            insertRow(managed, rowRequest);
            imported++;
        }
        return imported;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private record WhereClause(String sql, List<String> bindColumns) {
    }

    private WhereClause buildWhereClause(SqlDialect dialect, Map<String, Object> keys) {
        List<String> bindColumns = new ArrayList<>();
        String sql = keys.entrySet().stream()
                .map(entry -> {
                    if (entry.getValue() == null) {
                        return dialect.quoteIdentifier(entry.getKey()) + " IS NULL";
                    }
                    bindColumns.add(entry.getKey());
                    return dialect.quoteIdentifier(entry.getKey()) + " = ?";
                })
                .collect(Collectors.joining(" AND "));
        return new WhereClause(sql, bindColumns);
    }

    private int bindWhereValues(PreparedStatement statement, int startIndex, Map<String, Object> keys,
                                WhereClause whereClause) throws SQLException {
        int index = startIndex;
        for (String column : whereClause.bindColumns()) {
            bindValue(statement, index++, keys.get(column));
        }
        return index;
    }

    private Object nullify(String value) {
        if (value == null || value.isBlank() || "NULL".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private void bindValues(PreparedStatement statement, List<String> columns, Map<String, Object> data)
            throws SQLException {
        for (int i = 0; i < columns.size(); i++) {
            bindValue(statement, i + 1, data.get(columns.get(i)));
        }
    }

    private void bindValue(PreparedStatement statement, int index, Object value) throws SQLException {
        statement.setObject(index, value);
    }

    private void useDatabaseWithTimeout(ManagedConnection managed, String database) {
        try (Statement statement = managed.getActiveConnection().createStatement()) {
            statement.setQueryTimeout(connectionProperties.getQueryTimeoutSeconds());
            statement.execute(JdbcHelper.dialect(managed).useNamespaceSql(database));
        } catch (SQLException e) {
            throw new OpenDbException("Failed to switch namespace: " + e.getMessage(), e);
        }
    }
}
