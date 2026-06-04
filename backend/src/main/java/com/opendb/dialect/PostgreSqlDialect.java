package com.opendb.dialect;

import com.opendb.dto.CreateDatabaseRequest;
import com.opendb.dto.IndexInfo;
import com.opendb.model.DatabaseType;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class PostgreSqlDialect extends AbstractSqlDialect {

    @Override
    public DatabaseType getType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public String getAiDialectName() {
        return "PostgreSQL";
    }

    @Override
    public String buildJdbcUrl(String host, int port, String database) {
        if (database == null || database.isBlank()) {
            return "jdbc:postgresql://%s:%d/postgres".formatted(host, port);
        }
        return "jdbc:postgresql://%s:%d/%s".formatted(host, port, database);
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
        return "SET search_path TO " + quoteIdentifier(defaultSchema(namespace));
    }

    @Override
    public String listNamespacesSql() {
        return """
                SELECT datname
                FROM pg_database
                WHERE datistemplate = false
                ORDER BY datname
                """;
    }

    @Override
    public String listTablesSql(String namespace) {
        return """
                SELECT table_name, '' AS engine, 0 AS table_rows, '' AS table_comment
                FROM information_schema.tables
                WHERE table_catalog = '%s'
                  AND table_schema NOT IN ('pg_catalog', 'information_schema')
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String previewTableSql(String namespace, String table, int limit) {
        return "SELECT * FROM " + qualifyTable(namespace, table) + " FETCH FIRST " + limit + " ROWS ONLY";
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
        return "CREATE DATABASE " + quoteIdentifier(request.getName());
    }

    @Override
    public String dropNamespaceSql(String namespace) {
        return "DROP DATABASE " + quoteIdentifier(namespace);
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
                SELECT table_name,
                       'CREATE TABLE ' || quote_ident(table_schema) || '.' || quote_ident(table_name) || E';\\n-- Use pg_dump for full DDL' AS ddl
                FROM information_schema.tables
                WHERE table_catalog = '%s' AND table_name = '%s'
                ORDER BY table_schema
                LIMIT 1
                """.formatted(escapeLiteral(namespace), escapeLiteral(table));
    }

    @Override
    public String showCreateViewSql(String namespace, String view) {
        return """
                SELECT table_name,
                       'CREATE VIEW ' || quote_ident(table_schema) || '.' || quote_ident(table_name) || E';\\n-- Use pg_dump for full DDL' AS ddl
                FROM information_schema.views
                WHERE table_catalog = '%s' AND table_name = '%s'
                LIMIT 1
                """.formatted(escapeLiteral(namespace), escapeLiteral(view));
    }

    @Override
    public String listViewsSql(String namespace) {
        return """
                SELECT table_name, '' AS comment
                FROM information_schema.views
                WHERE table_catalog = '%s'
                  AND table_schema NOT IN ('pg_catalog', 'information_schema')
                ORDER BY table_name
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String listProceduresSql(String namespace) {
        return """
                SELECT routine_name, COALESCE(external_name, '') AS comment
                FROM information_schema.routines
                WHERE specific_catalog = '%s' AND routine_type = 'PROCEDURE'
                ORDER BY routine_name
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String listFunctionsSql(String namespace) {
        return """
                SELECT routine_name, COALESCE(external_name, '') AS comment
                FROM information_schema.routines
                WHERE specific_catalog = '%s' AND routine_type = 'FUNCTION'
                ORDER BY routine_name
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String listTriggersSql(String namespace) {
        return """
                SELECT trigger_name, action_timing
                FROM information_schema.triggers
                WHERE trigger_catalog = '%s'
                ORDER BY trigger_name
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String listIndexesSql(String namespace, String table) {
        return """
                SELECT indexname, split_part(indexdef, '(', 2) AS column_name, indexdef LIKE '%UNIQUE%' AS is_unique, 'BTREE' AS index_type
                FROM pg_indexes
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                  AND tablename = '%s'
                ORDER BY indexname
                """.formatted(escapeLiteral(table));
    }

    @Override
    public String listForeignKeysSql(String namespace) {
        return """
                SELECT tc.constraint_name, tc.table_name, kcu.column_name, ccu.table_name AS referenced_table_name, ccu.column_name AS referenced_column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_catalog = '%s'
                ORDER BY tc.table_name, kcu.column_name
                """.formatted(escapeLiteral(namespace));
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
        return "-- openDB backup script for database " + namespace + "\n\\c " + namespace + ";\n\n";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String metadataCatalog(String namespace) {
        return namespace;
    }

    @Override
    public String metadataSchema(String namespace) {
        return "public";
    }

    @Override
    public String qualifyTable(String namespace, String table) {
        return quoteIdentifier(defaultSchema(namespace)) + "." + quoteIdentifier(table);
    }

    @Override
    public int showCreateDdlColumnIndex() {
        return 2;
    }

    @Override
    public IndexInfo readIndexInfo(ResultSet rs) throws SQLException {
        return IndexInfo.builder()
                .name(rs.getString(1))
                .columnName(rs.getString(2) == null ? "" : rs.getString(2).replace(")", "").trim())
                .unique(rs.getBoolean(3))
                .type(rs.getString(4))
                .build();
    }

    private String defaultSchema(String namespace) {
        return "public";
    }
}
