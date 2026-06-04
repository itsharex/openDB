package com.opendb.service;

import com.opendb.config.ConnectionProperties;
import com.opendb.dialect.SqlDialect;
import com.opendb.dto.ColumnInfo;
import com.opendb.dto.SchemaSummary;
import com.opendb.dto.SqlExecuteRequest;
import com.opendb.dto.SqlExecuteResponse;
import com.opendb.dto.TableInfo;
import com.opendb.dto.TableSchema;
import com.opendb.exception.OpenDbException;
import com.opendb.model.ManagedConnection;
import com.opendb.util.SqlTableParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JdbcDatabaseService {

    private final ConnectionProperties connectionProperties;

    public List<String> listDatabases(ManagedConnection managed) {
        SqlDialect dialect = JdbcHelper.dialect(managed);
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createStatement(connection, connectionProperties);
             ResultSet rs = statement.executeQuery(dialect.listNamespacesSql())) {
            List<String> databases = new ArrayList<>();
            while (rs.next()) {
                databases.add(rs.getString(1));
            }
            return databases;
        } catch (SQLException e) {
            throw new OpenDbException("Failed to list databases: " + e.getMessage(), e);
        }
    }

    public List<TableInfo> listTables(ManagedConnection managed, String database) {
        JdbcHelper.useNamespace(managed, database);
        SqlDialect dialect = JdbcHelper.dialect(managed);
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createStatement(connection, connectionProperties);
             ResultSet rs = statement.executeQuery(dialect.listTablesSql(database))) {
            List<TableInfo> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(dialect.readTableInfo(rs));
            }
            return tables;
        } catch (SQLException e) {
            throw new OpenDbException("Failed to list tables: " + e.getMessage(), e);
        }
    }

    public List<ColumnInfo> listColumns(ManagedConnection managed, String database, String table) {
        JdbcHelper.useNamespace(managed, database);
        SqlDialect dialect = JdbcHelper.dialect(managed);
        try {
            Connection connection = managed.getActiveConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            List<ColumnInfo> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(
                    dialect.metadataCatalog(database),
                    dialect.metadataSchema(database),
                    table,
                    null)) {
                while (rs.next()) {
                    columns.add(ColumnInfo.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .type(rs.getString("TYPE_NAME"))
                            .nullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? "YES" : "NO")
                            .defaultValue(rs.getString("COLUMN_DEF"))
                            .extra("")
                            .key("")
                            .build());
                }
            }

            try (ResultSet rs = metaData.getPrimaryKeys(
                    dialect.metadataCatalog(database),
                    dialect.metadataSchema(database),
                    table)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    columns.stream()
                            .filter(column -> column.getName().equalsIgnoreCase(columnName))
                            .findFirst()
                            .ifPresent(column -> column.setKey("PRI"));
                }
            }
            return columns;
        } catch (SQLException e) {
            throw new OpenDbException("Failed to list columns: " + e.getMessage(), e);
        }
    }

    public SqlExecuteResponse execute(ManagedConnection managed, SqlExecuteRequest request) {
        if (request.getDatabase() != null && !request.getDatabase().isBlank()) {
            JdbcHelper.useNamespace(managed, request.getDatabase());
        }

        SqlDialect dialect = JdbcHelper.dialect(managed);
        long start = System.currentTimeMillis();
        String sql = request.getSql().trim();
        String normalized = sql.toLowerCase(Locale.ROOT);

        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createStatement(connection, connectionProperties)) {
            if (dialect.isQuery(normalized)) {
                SqlExecuteResponse response = executeQuery(statement, sql, request.getLimit(), start);
                return enrichEditMetadata(managed, request.getDatabase(), sql, response);
            }
            int affected = statement.executeUpdate(sql);
            return SqlExecuteResponse.builder()
                    .type("UPDATE")
                    .rowCount(affected)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .message("Query executed successfully. Affected rows: " + affected)
                    .build();
        } catch (SQLException e) {
            throw new OpenDbException("SQL execution failed: " + e.getMessage(), e);
        }
    }

    public SqlExecuteResponse previewTable(ManagedConnection managed, String database, String table, int limit) {
        SqlDialect dialect = JdbcHelper.dialect(managed);
        SqlExecuteRequest request = new SqlExecuteRequest();
        request.setSql(dialect.previewTableSql(database, table, limit));
        request.setDatabase(database);
        request.setLimit(limit);
        return execute(managed, request);
    }

    public String getSchemaSummary(ManagedConnection managed, String database) {
        List<TableInfo> tables = listTables(managed, database);
        StringBuilder builder = new StringBuilder();
        builder.append("Database: ").append(database).append('\n');
        for (TableInfo table : tables) {
            builder.append("- Table ").append(table.getName()).append('\n');
            for (ColumnInfo column : listColumns(managed, database, table.getName())) {
                builder.append("  - ")
                        .append(column.getName())
                        .append(" ")
                        .append(column.getType())
                        .append(column.getKey() != null && !column.getKey().isBlank()
                                ? " [" + column.getKey() + "]" : "")
                        .append('\n');
            }
        }
        return builder.toString();
    }

    public SchemaSummary getSchema(ManagedConnection managed, String database) {
        List<TableInfo> tables = listTables(managed, database);
        List<TableSchema> tableSchemas = new ArrayList<>();
        for (TableInfo table : tables) {
            tableSchemas.add(TableSchema.builder()
                    .name(table.getName())
                    .columns(listColumns(managed, database, table.getName()))
                    .build());
        }
        return SchemaSummary.builder()
                .database(database)
                .tables(tableSchemas)
                .build();
    }

    private SqlExecuteResponse executeQuery(Statement statement, String sql, int limit, long start)
            throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            int count = 0;
            while (rs.next() && count < limit) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
                count++;
            }

            return SqlExecuteResponse.builder()
                    .type("SELECT")
                    .columns(columns)
                    .rows(rows)
                    .rowCount(rows.size())
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .message("Query executed successfully.")
                    .editable(false)
                    .build();
        }
    }

    private SqlExecuteResponse enrichEditMetadata(ManagedConnection managed, String defaultDatabase,
                                                  String sql, SqlExecuteResponse response) {
        if (!"SELECT".equals(response.getType())) {
            return response;
        }

        var parsed = SqlTableParser.parseSingleTableSelect(sql);
        if (parsed.isEmpty()) {
            return response.toBuilder()
                    .editable(false)
                    .editableReason("仅支持单表 SELECT 查询的直接编辑")
                    .build();
        }

        String database = parsed.get().database() != null && !parsed.get().database().isBlank()
                ? parsed.get().database()
                : defaultDatabase;
        String table = parsed.get().table();

        if (database == null || database.isBlank()) {
            return response.toBuilder()
                    .editable(false)
                    .editableReason("请先选择数据库后再编辑查询结果")
                    .build();
        }

        try {
            listTables(managed, database).stream()
                    .filter(item -> item.getName().equalsIgnoreCase(table))
                    .findFirst()
                    .orElseThrow(() -> new OpenDbException("表不存在: " + table));

            List<String> primaryKeys = listColumns(managed, database, table).stream()
                    .filter(column -> "PRI".equals(column.getKey()))
                    .map(ColumnInfo::getName)
                    .toList();

            String reason = primaryKeys.isEmpty()
                    ? "该表无主键，将使用整行数据定位记录"
                    : null;

            return response.toBuilder()
                    .editable(true)
                    .sourceDatabase(database)
                    .sourceTable(table)
                    .primaryKeys(primaryKeys)
                    .editableReason(reason)
                    .build();
        } catch (OpenDbException e) {
            return response.toBuilder()
                    .editable(false)
                    .editableReason(e.getMessage())
                    .build();
        } catch (Exception e) {
            return response.toBuilder()
                    .editable(false)
                    .editableReason("无法编辑: " + e.getMessage())
                    .build();
        }
    }
}
