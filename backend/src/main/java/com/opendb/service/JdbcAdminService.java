package com.opendb.service;

import com.opendb.config.ConnectionProperties;
import com.opendb.dialect.SqlDialect;
import com.opendb.dto.CreateDatabaseRequest;
import com.opendb.dto.CreateTableRequest;
import com.opendb.dto.DatabaseObjectInfo;
import com.opendb.dto.DdlResponse;
import com.opendb.dto.IndexInfo;
import com.opendb.dto.TableInfo;
import com.opendb.exception.OpenDbException;
import com.opendb.model.ManagedConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JdbcAdminService {

    private final ConnectionProperties connectionProperties;
    private final JdbcDatabaseService databaseService;

    public String generateBackupScript(ManagedConnection managed, String database) {
        SqlDialect dialect = JdbcHelper.dialect(managed);
        List<TableInfo> tables = databaseService.listTables(managed, database);
        StringBuilder builder = new StringBuilder(dialect.backupScriptHeader(database));
        for (TableInfo table : tables) {
            DdlResponse ddl = showCreateTable(managed, database, table.getName());
            builder.append(ddl.getDdl()).append(";\n\n");
        }
        return builder.toString();
    }

    public void createDatabase(ManagedConnection managed, CreateDatabaseRequest request) {
        SqlDialect dialect = JdbcHelper.dialect(managed);
        executeUpdate(managed, dialect.createDatabaseSql(request));
    }

    public void dropDatabase(ManagedConnection managed, String database) {
        SqlDialect dialect = JdbcHelper.dialect(managed);
        executeUpdate(managed, dialect.dropNamespaceSql(database));
    }

    public void dropTable(ManagedConnection managed, String database, String table) {
        JdbcHelper.useNamespace(managed, database);
        SqlDialect dialect = JdbcHelper.dialect(managed);
        executeUpdate(managed, dialect.dropTableSql(database, table));
    }

    public void truncateTable(ManagedConnection managed, String database, String table) {
        JdbcHelper.useNamespace(managed, database);
        SqlDialect dialect = JdbcHelper.dialect(managed);
        executeUpdate(managed, dialect.truncateTableSql(database, table));
    }

    public void createTable(ManagedConnection managed, String database, CreateTableRequest request) {
        JdbcHelper.useNamespace(managed, database);
        executeUpdate(managed, request.getDdl().trim());
    }

    public List<DatabaseObjectInfo> listViews(ManagedConnection managed, String database) {
        return queryObjects(managed, database, JdbcHelper.dialect(managed).listViewsSql(database), "VIEW");
    }

    public List<DatabaseObjectInfo> listProcedures(ManagedConnection managed, String database) {
        return queryObjects(managed, database, JdbcHelper.dialect(managed).listProceduresSql(database), "PROCEDURE");
    }

    public List<DatabaseObjectInfo> listFunctions(ManagedConnection managed, String database) {
        return queryObjects(managed, database, JdbcHelper.dialect(managed).listFunctionsSql(database), "FUNCTION");
    }

    public List<DatabaseObjectInfo> listTriggers(ManagedConnection managed, String database) {
        return queryObjects(managed, database, JdbcHelper.dialect(managed).listTriggersSql(database), "TRIGGER");
    }

    public List<IndexInfo> listIndexes(ManagedConnection managed, String database, String table) {
        JdbcHelper.useNamespace(managed, database);
        SqlDialect dialect = JdbcHelper.dialect(managed);
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createAdminStatement(connection, connectionProperties);
             ResultSet rs = statement.executeQuery(dialect.listIndexesSql(database, table))) {
            List<IndexInfo> indexes = new ArrayList<>();
            while (rs.next()) {
                indexes.add(dialect.readIndexInfo(rs));
            }
            return indexes;
        } catch (SQLException e) {
            throw new OpenDbException("Failed to list indexes: " + e.getMessage(), e);
        }
    }

    public DdlResponse showCreateTable(ManagedConnection managed, String database, String table) {
        JdbcHelper.useNamespace(managed, database);
        return showCreate(managed, JdbcHelper.dialect(managed).showCreateTableSql(database, table), "Table");
    }

    public DdlResponse showCreateView(ManagedConnection managed, String database, String view) {
        JdbcHelper.useNamespace(managed, database);
        return showCreate(managed, JdbcHelper.dialect(managed).showCreateViewSql(database, view), "View");
    }

    public String exportTableCsv(ManagedConnection managed, String database, String table, int limit) {
        JdbcHelper.useNamespace(managed, database);
        SqlDialect dialect = JdbcHelper.dialect(managed);
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createAdminStatement(connection, connectionProperties);
             ResultSet rs = statement.executeQuery(dialect.exportTableSql(database, table, limit))) {
            int columnCount = rs.getMetaData().getColumnCount();
            StringBuilder builder = new StringBuilder("\uFEFF");
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) builder.append(',');
                builder.append(JdbcHelper.csvEscape(rs.getMetaData().getColumnLabel(i)));
            }
            builder.append('\n');
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) builder.append(',');
                    Object value = rs.getObject(i);
                    builder.append(JdbcHelper.csvEscape(value == null ? "" : String.valueOf(value)));
                }
                builder.append('\n');
            }
            return builder.toString();
        } catch (SQLException e) {
            throw new OpenDbException("Failed to export table: " + e.getMessage(), e);
        }
    }

    private List<DatabaseObjectInfo> queryObjects(ManagedConnection managed, String database, String sql, String type) {
        JdbcHelper.useNamespace(managed, database);
        SqlDialect dialect = JdbcHelper.dialect(managed);
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createAdminStatement(connection, connectionProperties);
             ResultSet rs = statement.executeQuery(sql)) {
            List<DatabaseObjectInfo> objects = new ArrayList<>();
            while (rs.next()) {
                objects.add(dialect.readDatabaseObject(rs, type));
            }
            return objects;
        } catch (SQLException e) {
            throw new OpenDbException("Failed to list objects: " + e.getMessage(), e);
        }
    }

    private DdlResponse showCreate(ManagedConnection managed, String sql, String objectType) {
        SqlDialect dialect = JdbcHelper.dialect(managed);
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createAdminStatement(connection, connectionProperties);
             ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                throw new OpenDbException("Object not found");
            }
            return DdlResponse.builder()
                    .objectName(rs.getString(1))
                    .objectType(objectType)
                    .ddl(rs.getString(dialect.showCreateDdlColumnIndex()))
                    .build();
        } catch (SQLException e) {
            throw new OpenDbException("Failed to show create statement: " + e.getMessage(), e);
        }
    }

    private void executeUpdate(ManagedConnection managed, String sql) {
        Connection connection = managed.getActiveConnection();
        try (Statement statement = JdbcHelper.createAdminStatement(connection, connectionProperties)) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new OpenDbException("SQL execution failed: " + e.getMessage(), e);
        }
    }
}
