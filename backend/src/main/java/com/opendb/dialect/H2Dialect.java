package com.opendb.dialect;

import com.opendb.dto.CreateDatabaseRequest;
import com.opendb.dto.IndexInfo;
import com.opendb.model.DatabaseType;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class H2Dialect extends AbstractSqlDialect {

    @Override
    public DatabaseType getType() {
        return DatabaseType.H2;
    }

    @Override
    public String getAiDialectName() {
        return "H2";
    }

    @Override
    public String buildJdbcUrl(String host, int port, String database) {
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
            if (database == null || database.isBlank()) {
                return "jdbc:h2:mem:opendb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
            }
            return "jdbc:h2:mem:%s;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                    .formatted(sanitizeToken(database));
        }
        String db = database == null || database.isBlank() ? "opendb" : database;
        return "jdbc:h2:tcp://%s:%d/~/%s;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                .formatted(host, port, sanitizeToken(db));
    }

    @Override
    public Properties getConnectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password == null ? "" : password);
        return props;
    }

    @Override
    public String useNamespaceSql(String namespace) {
        return "SET SCHEMA " + quoteIdentifier(namespace.toUpperCase());
    }

    @Override
    public String listNamespacesSql() {
        return """
                SELECT SCHEMA_NAME
                FROM INFORMATION_SCHEMA.SCHEMATA
                WHERE SCHEMA_NAME NOT IN ('INFORMATION_SCHEMA')
                ORDER BY SCHEMA_NAME
                """;
    }

    @Override
    public String listTablesSql(String namespace) {
        return """
                SELECT TABLE_NAME, '' AS engine, 0 AS table_rows, REMARKS AS table_comment
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_TYPE = 'TABLE'
                ORDER BY TABLE_NAME
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String previewTableSql(String namespace, String table, int limit) {
        return "SELECT * FROM " + qualifyTable(namespace, table) + " LIMIT " + limit;
    }

    @Override
    public String countRowsSql(String namespace, String table) {
        return "SELECT COUNT(*) FROM " + qualifyTable(namespace, table);
    }

    @Override
    public String exportTableSql(String namespace, String table, int limit) {
        return previewTableSql(namespace, table, limit);
    }

    @Override
    public String createDatabaseSql(CreateDatabaseRequest request) {
        return "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(request.getName().toUpperCase());
    }

    @Override
    public String dropNamespaceSql(String namespace) {
        return "DROP SCHEMA IF EXISTS " + quoteIdentifier(namespace.toUpperCase()) + " CASCADE";
    }

    @Override
    public String dropTableSql(String namespace, String table) {
        return "DROP TABLE " + qualifyTable(namespace, table);
    }

    @Override
    public String truncateTableSql(String namespace, String table) {
        return "TRUNCATE TABLE " + qualifyTable(namespace, table);
    }

    @Override
    public String showCreateTableSql(String namespace, String table) {
        return """
                SELECT TABLE_NAME, 'CREATE TABLE ' || TABLE_SCHEMA || '.' || TABLE_NAME || ';' AS ddl
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'
                """.formatted(escapeLiteral(namespace.toUpperCase()), escapeLiteral(table.toUpperCase()));
    }

    @Override
    public String showCreateViewSql(String namespace, String view) {
        return """
                SELECT TABLE_NAME, 'CREATE VIEW ' || TABLE_SCHEMA || '.' || TABLE_NAME || ';' AS ddl
                FROM INFORMATION_SCHEMA.VIEWS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'
                """.formatted(escapeLiteral(namespace.toUpperCase()), escapeLiteral(view.toUpperCase()));
    }

    @Override
    public String listViewsSql(String namespace) {
        return """
                SELECT TABLE_NAME, REMARKS
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_TYPE = 'VIEW'
                ORDER BY TABLE_NAME
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String listProceduresSql(String namespace) {
        return """
                SELECT ROUTINE_NAME, REMARKS
                FROM INFORMATION_SCHEMA.ROUTINES
                WHERE ROUTINE_SCHEMA = '%s' AND ROUTINE_TYPE = 'PROCEDURE'
                ORDER BY ROUTINE_NAME
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String listFunctionsSql(String namespace) {
        return """
                SELECT ROUTINE_NAME, REMARKS
                FROM INFORMATION_SCHEMA.ROUTINES
                WHERE ROUTINE_SCHEMA = '%s' AND ROUTINE_TYPE = 'FUNCTION'
                ORDER BY ROUTINE_NAME
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String listTriggersSql(String namespace) {
        return """
                SELECT TRIGGER_NAME, ACTION_TIMING
                FROM INFORMATION_SCHEMA.TRIGGERS
                WHERE TRIGGER_SCHEMA = '%s'
                ORDER BY TRIGGER_NAME
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String listIndexesSql(String namespace, String table) {
        return """
                SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE = FALSE AS is_unique, TYPE_NAME
                FROM INFORMATION_SCHEMA.INDEXES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'
                ORDER BY INDEX_NAME, ORDINAL_POSITION
                """.formatted(escapeLiteral(namespace.toUpperCase()), escapeLiteral(table.toUpperCase()));
    }

    @Override
    public String listForeignKeysSql(String namespace) {
        return """
                SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                FROM INFORMATION_SCHEMA.CROSS_REFERENCES
                WHERE CONSTRAINT_SCHEMA = '%s'
                ORDER BY TABLE_NAME, COLUMN_NAME
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String transferDataSql(String sourceNamespace, String sourceTable, String targetNamespace, String targetTable) {
        return "INSERT INTO %s SELECT * FROM %s".formatted(
                qualifyTable(targetNamespace, targetTable),
                qualifyTable(sourceNamespace, sourceTable)
        );
    }

    @Override
    public String backupScriptHeader(String namespace) {
        return "-- openDB backup script for schema " + namespace + "\nSET SCHEMA "
                + quoteIdentifier(namespace.toUpperCase()) + ";\n\n";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String metadataCatalog(String namespace) {
        return null;
    }

    @Override
    public String metadataSchema(String namespace) {
        return namespace.toUpperCase();
    }

    @Override
    public String qualifyTable(String namespace, String table) {
        return quoteIdentifier(namespace.toUpperCase()) + "." + quoteIdentifier(table.toUpperCase());
    }

    @Override
    public int showCreateDdlColumnIndex() {
        return 2;
    }

    @Override
    public IndexInfo readIndexInfo(ResultSet rs) throws SQLException {
        return IndexInfo.builder()
                .name(rs.getString(1))
                .columnName(rs.getString(2))
                .unique(rs.getBoolean(3))
                .type(rs.getString(4))
                .build();
    }
}
