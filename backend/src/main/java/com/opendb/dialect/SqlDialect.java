package com.opendb.dialect;

import com.opendb.dto.CreateDatabaseRequest;
import com.opendb.dto.DatabaseObjectInfo;
import com.opendb.dto.IndexInfo;
import com.opendb.dto.TableInfo;
import com.opendb.model.DatabaseType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public interface SqlDialect {

    DatabaseType getType();

    String getAiDialectName();

    String buildJdbcUrl(String host, int port, String database);

    Properties getConnectionProperties(String username, String password);

    String useNamespaceSql(String namespace);

    String listNamespacesSql();

    String listTablesSql(String namespace);

    String previewTableSql(String namespace, String table, int limit);

    String countRowsSql(String namespace, String table);

    String exportTableSql(String namespace, String table, int limit);

    String createDatabaseSql(CreateDatabaseRequest request);

    String dropNamespaceSql(String namespace);

    String dropTableSql(String namespace, String table);

    String truncateTableSql(String namespace, String table);

    String showCreateTableSql(String namespace, String table);

    String showCreateViewSql(String namespace, String view);

    String listViewsSql(String namespace);

    String listProceduresSql(String namespace);

    String listFunctionsSql(String namespace);

    String listTriggersSql(String namespace);

    String listIndexesSql(String namespace, String table);

    String listForeignKeysSql(String namespace);

    String transferDataSql(String sourceNamespace, String sourceTable, String targetNamespace, String targetTable);

    String backupScriptHeader(String namespace);

    String quoteIdentifier(String identifier);

    String escapeLiteral(String value);

    boolean isQuery(String normalizedSql);

    String metadataCatalog(String namespace);

    String metadataSchema(String namespace);

    TableInfo readTableInfo(ResultSet rs) throws SQLException;

    DatabaseObjectInfo readDatabaseObject(ResultSet rs, String type) throws SQLException;

    IndexInfo readIndexInfo(ResultSet rs) throws SQLException;

    int showCreateDdlColumnIndex();

    default String qualifyTable(String namespace, String table) {
        return quoteIdentifier(table);
    }
}
