package com.opendb.dialect;

import com.opendb.dto.CreateDatabaseRequest;
import com.opendb.dto.IndexInfo;
import com.opendb.exception.OpenDbException;
import com.opendb.model.DatabaseType;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class OracleDialect extends AbstractSqlDialect {

    @Override
    public DatabaseType getType() {
        return DatabaseType.ORACLE;
    }

    @Override
    public String getAiDialectName() {
        return "Oracle";
    }

    @Override
    public String buildJdbcUrl(String host, int port, String database) {
        String service = database == null || database.isBlank() ? "ORCL" : database;
        return "jdbc:oracle:thin:@%s:%d/%s".formatted(host, port, service);
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
        return "ALTER SESSION SET CURRENT_SCHEMA = " + quoteIdentifier(namespace.toUpperCase());
    }

    @Override
    public String listNamespacesSql() {
        return """
                SELECT username
                FROM all_users
                WHERE username NOT IN ('SYS','SYSTEM','OUTLN','DBSNMP','XDB','ORDSYS','CTXSYS','MDSYS','OLAPSYS','WMSYS')
                ORDER BY username
                """;
    }

    @Override
    public String listTablesSql(String namespace) {
        return """
                SELECT table_name, '' AS engine, num_rows, '' AS table_comment
                FROM all_tables
                WHERE owner = '%s'
                ORDER BY table_name
                """.formatted(escapeLiteral(namespace.toUpperCase()));
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
        throw new OpenDbException("Oracle does not support CREATE DATABASE. Create a user/schema instead.");
    }

    @Override
    public String dropNamespaceSql(String namespace) {
        throw new OpenDbException("Oracle does not support DROP DATABASE. Drop user/schema manually.");
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
                       'CREATE TABLE ' || owner || '.' || table_name || '; -- Use DBMS_METADATA for full DDL' AS ddl
                FROM all_tables
                WHERE owner = '%s' AND table_name = '%s'
                """.formatted(escapeLiteral(namespace.toUpperCase()), escapeLiteral(table.toUpperCase()));
    }

    @Override
    public String showCreateViewSql(String namespace, String view) {
        return """
                SELECT view_name,
                       'CREATE VIEW ' || owner || '.' || view_name || '; -- Use DBMS_METADATA for full DDL' AS ddl
                FROM all_views
                WHERE owner = '%s' AND view_name = '%s'
                """.formatted(escapeLiteral(namespace.toUpperCase()), escapeLiteral(view.toUpperCase()));
    }

    @Override
    public String listViewsSql(String namespace) {
        return """
                SELECT view_name, '' AS comment
                FROM all_views
                WHERE owner = '%s'
                ORDER BY view_name
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String listProceduresSql(String namespace) {
        return """
                SELECT object_name, '' AS comment
                FROM all_procedures
                WHERE owner = '%s' AND object_type = 'PROCEDURE'
                ORDER BY object_name
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String listFunctionsSql(String namespace) {
        return """
                SELECT object_name, '' AS comment
                FROM all_procedures
                WHERE owner = '%s' AND object_type = 'FUNCTION'
                ORDER BY object_name
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String listTriggersSql(String namespace) {
        return """
                SELECT trigger_name, triggering_event
                FROM all_triggers
                WHERE owner = '%s'
                ORDER BY trigger_name
                """.formatted(escapeLiteral(namespace.toUpperCase()));
    }

    @Override
    public String listIndexesSql(String namespace, String table) {
        return """
                SELECT ai.index_name, aic.column_name, ai.uniqueness = 'UNIQUE' AS is_unique, ai.index_type
                FROM all_indexes ai
                JOIN all_ind_columns aic
                  ON ai.owner = aic.index_owner AND ai.index_name = aic.index_name
                WHERE ai.owner = '%s' AND ai.table_name = '%s'
                ORDER BY ai.index_name, aic.column_position
                """.formatted(escapeLiteral(namespace.toUpperCase()), escapeLiteral(table.toUpperCase()));
    }

    @Override
    public String listForeignKeysSql(String namespace) {
        return """
                SELECT ac.constraint_name, ac.table_name, acc.column_name, rc.table_name AS referenced_table_name, rcc.column_name AS referenced_column_name
                FROM all_constraints ac
                JOIN all_cons_columns acc
                  ON ac.owner = acc.owner AND ac.constraint_name = acc.constraint_name
                JOIN all_constraints rc
                  ON ac.r_owner = rc.owner AND ac.r_constraint_name = rc.constraint_name
                JOIN all_cons_columns rcc
                  ON rc.owner = rcc.owner AND rc.constraint_name = rcc.constraint_name AND acc.position = rcc.position
                WHERE ac.constraint_type = 'R' AND ac.owner = '%s'
                ORDER BY ac.table_name, acc.column_name
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
        return "-- openDB backup script for schema " + namespace + "\nALTER SESSION SET CURRENT_SCHEMA = "
                + quoteIdentifier(namespace.toUpperCase()) + ";\n\n";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"").toUpperCase() + "\"";
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
        return quoteIdentifier(namespace) + "." + quoteIdentifier(table);
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
