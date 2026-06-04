package com.opendb.service;

import com.opendb.config.ConnectionProperties;
import com.opendb.dialect.SqlDialect;
import com.opendb.dto.ColumnInfo;
import com.opendb.dto.DataCompareResponse;
import com.opendb.dto.DataTransferRequest;
import com.opendb.dto.ErDiagramResponse;
import com.opendb.dto.ForeignKeyInfo;
import com.opendb.dto.SchemaCompareResponse;
import com.opendb.dto.TableInfo;
import com.opendb.dto.TableSchema;
import com.opendb.exception.OpenDbException;
import com.opendb.model.ManagedConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JdbcSchemaToolsService {

    private final ConnectionProperties connectionProperties;
    private final JdbcDatabaseService databaseService;
    private final JdbcAdminService adminService;

    public ErDiagramResponse getErDiagram(ManagedConnection managed, String database) {
        List<TableSchema> tables = databaseService.getSchema(managed, database).getTables();
        List<ForeignKeyInfo> relationships = listForeignKeys(managed, database);
        return ErDiagramResponse.builder()
                .database(database)
                .tables(tables)
                .relationships(relationships)
                .build();
    }

    public SchemaCompareResponse compareSchemas(ManagedConnection managed, String sourceDatabase, String targetDatabase) {
        List<String> sourceTables = databaseService.listTables(managed, sourceDatabase).stream()
                .map(TableInfo::getName).sorted().toList();
        List<String> targetTables = databaseService.listTables(managed, targetDatabase).stream()
                .map(TableInfo::getName).sorted().toList();

        Set<String> sourceSet = new HashSet<>(sourceTables);
        Set<String> targetSet = new HashSet<>(targetTables);

        List<String> onlyInSource = sourceTables.stream().filter(table -> !targetSet.contains(table)).toList();
        List<String> onlyInTarget = targetTables.stream().filter(table -> !sourceSet.contains(table)).toList();
        List<String> modifiedTables = sourceTables.stream()
                .filter(targetSet::contains)
                .filter(table -> !sameTableStructure(managed, sourceDatabase, targetDatabase, table))
                .toList();

        StringBuilder script = new StringBuilder();
        script.append("-- Schema sync script: ").append(sourceDatabase).append(" -> ").append(targetDatabase).append('\n');
        for (String table : onlyInSource) {
            script.append(adminService.showCreateTable(managed, sourceDatabase, table).getDdl())
                    .append(";\n\n");
        }
        for (String table : modifiedTables) {
            script.append("-- TODO: review modified table `").append(table).append("`\n");
            script.append(adminService.showCreateTable(managed, sourceDatabase, table).getDdl())
                    .append(";\n\n");
        }

        return SchemaCompareResponse.builder()
                .sourceDatabase(sourceDatabase)
                .targetDatabase(targetDatabase)
                .onlyInSource(onlyInSource)
                .onlyInTarget(onlyInTarget)
                .modifiedTables(modifiedTables)
                .syncScript(script.toString())
                .build();
    }

    public int transferData(ManagedConnection managed, DataTransferRequest request) {
        if (request.isTruncateTarget()) {
            adminService.truncateTable(managed, request.getTargetDatabase(), request.getTargetTable());
        }
        SqlDialect dialect = JdbcHelper.dialect(managed);
        String sql = dialect.transferDataSql(
                request.getSourceDatabase(),
                request.getSourceTable(),
                request.getTargetDatabase(),
                request.getTargetTable()
        );
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createAdminStatement(connection, connectionProperties)) {
            return statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new OpenDbException("Data transfer failed: " + e.getMessage(), e);
        }
    }

    public DataCompareResponse compareData(ManagedConnection managed,
                                           String sourceDatabase,
                                           String sourceTable,
                                           String targetDatabase,
                                           String targetTable) {
        long sourceRows = countRows(managed, sourceDatabase, sourceTable);
        long targetRows = countRows(managed, targetDatabase, targetTable);
        String summary = String.format("Source %s.%s: %d rows; Target %s.%s: %d rows; Diff: %d",
                sourceDatabase, sourceTable, sourceRows, targetDatabase, targetTable, targetRows, sourceRows - targetRows);
        return DataCompareResponse.builder()
                .sourceDatabase(sourceDatabase)
                .sourceTable(sourceTable)
                .targetDatabase(targetDatabase)
                .targetTable(targetTable)
                .sourceRows(sourceRows)
                .targetRows(targetRows)
                .summary(summary)
                .build();
    }

    private boolean sameTableStructure(ManagedConnection managed, String sourceDb, String targetDb, String table) {
        List<ColumnInfo> sourceColumns = databaseService.listColumns(managed, sourceDb, table);
        List<ColumnInfo> targetColumns = databaseService.listColumns(managed, targetDb, table);
        if (sourceColumns.size() != targetColumns.size()) {
            return false;
        }
        for (int i = 0; i < sourceColumns.size(); i++) {
            ColumnInfo source = sourceColumns.get(i);
            ColumnInfo target = targetColumns.get(i);
            if (!source.getName().equalsIgnoreCase(target.getName()) || !source.getType().equalsIgnoreCase(target.getType())) {
                return false;
            }
        }
        return true;
    }

    private long countRows(ManagedConnection managed, String database, String table) {
        JdbcHelper.useNamespace(managed, database);
        SqlDialect dialect = JdbcHelper.dialect(managed);
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createAdminStatement(connection, connectionProperties);
             ResultSet rs = statement.executeQuery(dialect.countRowsSql(database, table))) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new OpenDbException("Count failed: " + e.getMessage(), e);
        }
    }

    private List<ForeignKeyInfo> listForeignKeys(ManagedConnection managed, String database) {
        SqlDialect dialect = JdbcHelper.dialect(managed);
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createAdminStatement(connection, connectionProperties);
             ResultSet rs = statement.executeQuery(dialect.listForeignKeysSql(database))) {
            List<ForeignKeyInfo> keys = new ArrayList<>();
            while (rs.next()) {
                keys.add(ForeignKeyInfo.builder()
                        .constraintName(rs.getString(1))
                        .fromTable(rs.getString(2))
                        .fromColumn(rs.getString(3))
                        .toTable(rs.getString(4))
                        .toColumn(rs.getString(5))
                        .build());
            }
            return keys;
        } catch (SQLException e) {
            throw new OpenDbException("Failed to list foreign keys: " + e.getMessage(), e);
        }
    }
}
