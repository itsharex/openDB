package com.opendb.dialect;

import com.opendb.dto.CreateDatabaseRequest;
import com.opendb.dto.IndexInfo;
import com.opendb.model.DatabaseType;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class MySqlDialect extends AbstractSqlDialect {

    @Override
    public DatabaseType getType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String getAiDialectName() {
        return "MySQL";
    }

    @Override
    public String buildJdbcUrl(String host, int port, String database) {
        if (database == null || database.isBlank()) {
            return "jdbc:mysql://%s:%d/?characterEncoding=utf8&useUnicode=true".formatted(host, port);
        }
        return "jdbc:mysql://%s:%d/%s?characterEncoding=utf8&useUnicode=true".formatted(host, port, database);
    }

    @Override
    public Properties getConnectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password == null ? "" : password);
        props.setProperty("useSSL", "false");
        props.setProperty("allowPublicKeyRetrieval", "true");
        props.setProperty("serverTimezone", "UTC");
        return props;
    }

    @Override
    public String useNamespaceSql(String namespace) {
        return "USE " + quoteIdentifier(namespace);
    }

    @Override
    public String listNamespacesSql() {
        return "SHOW DATABASES";
    }

    @Override
    public String listTablesSql(String namespace) {
        return """
                SELECT TABLE_NAME, ENGINE, TABLE_ROWS, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """.formatted(escapeLiteral(namespace));
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
        return createDatabaseFromTemplate(request,
                "CREATE DATABASE %s CHARACTER SET %s COLLATE %s");
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
        return "SHOW CREATE TABLE " + qualifyTable(namespace, table);
    }

    @Override
    public String showCreateViewSql(String namespace, String view) {
        return "SHOW CREATE VIEW " + quoteIdentifier(view);
    }

    @Override
    public String listViewsSql(String namespace) {
        return """
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_TYPE = 'VIEW'
                ORDER BY TABLE_NAME
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String listProceduresSql(String namespace) {
        return """
                SELECT ROUTINE_NAME, ROUTINE_COMMENT
                FROM information_schema.ROUTINES
                WHERE ROUTINE_SCHEMA = '%s' AND ROUTINE_TYPE = 'PROCEDURE'
                ORDER BY ROUTINE_NAME
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String listFunctionsSql(String namespace) {
        return """
                SELECT ROUTINE_NAME, ROUTINE_COMMENT
                FROM information_schema.ROUTINES
                WHERE ROUTINE_SCHEMA = '%s' AND ROUTINE_TYPE = 'FUNCTION'
                ORDER BY ROUTINE_NAME
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String listTriggersSql(String namespace) {
        return """
                SELECT TRIGGER_NAME, ACTION_TIMING
                FROM information_schema.TRIGGERS
                WHERE TRIGGER_SCHEMA = '%s'
                ORDER BY TRIGGER_NAME
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String listIndexesSql(String namespace, String table) {
        return "SHOW INDEX FROM " + qualifyTable(namespace, table);
    }

    @Override
    public String listForeignKeysSql(String namespace) {
        return """
                SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = '%s' AND REFERENCED_TABLE_NAME IS NOT NULL
                ORDER BY TABLE_NAME, COLUMN_NAME
                """.formatted(escapeLiteral(namespace));
    }

    @Override
    public String transferDataSql(String sourceNamespace, String sourceTable, String targetNamespace, String targetTable) {
        return "INSERT INTO %s SELECT * FROM %s".formatted(
                quoteIdentifier(targetNamespace) + "." + quoteIdentifier(targetTable),
                quoteIdentifier(sourceNamespace) + "." + quoteIdentifier(sourceTable)
        );
    }

    @Override
    public String backupScriptHeader(String namespace) {
        return """
                -- openDB backup script for `%s`
                CREATE DATABASE IF NOT EXISTS %s;
                USE %s;

                """.formatted(namespace, quoteIdentifier(namespace), quoteIdentifier(namespace));
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public String metadataCatalog(String namespace) {
        return namespace;
    }

    @Override
    public String metadataSchema(String namespace) {
        return null;
    }

    @Override
    public int showCreateDdlColumnIndex() {
        return 2;
    }

    @Override
    public IndexInfo readIndexInfo(ResultSet rs) throws SQLException {
        return IndexInfo.builder()
                .name(rs.getString("Key_name"))
                .columnName(rs.getString("Column_name"))
                .unique(rs.getInt("Non_unique") == 0)
                .type(rs.getString("Index_type"))
                .build();
    }
}
