package com.opendb.dialect;

import com.opendb.dto.CreateDatabaseRequest;
import com.opendb.dto.DatabaseObjectInfo;
import com.opendb.dto.IndexInfo;
import com.opendb.dto.TableInfo;
import com.opendb.exception.OpenDbException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

abstract class AbstractSqlDialect implements SqlDialect {

    @Override
    public String escapeLiteral(String value) {
        return value.replace("'", "''");
    }

    protected String sanitizeToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]+")) {
            throw new OpenDbException("Invalid identifier: " + value);
        }
        return value;
    }

    @Override
    public boolean isQuery(String normalizedSql) {
        return normalizedSql.startsWith("select")
                || normalizedSql.startsWith("show")
                || normalizedSql.startsWith("describe")
                || normalizedSql.startsWith("desc")
                || normalizedSql.startsWith("explain")
                || normalizedSql.startsWith("with");
    }

    @Override
    public TableInfo readTableInfo(ResultSet rs) throws SQLException {
        return TableInfo.builder()
                .name(rs.getString(1))
                .engine(rs.getString(2))
                .rows(rs.getLong(3))
                .comment(rs.getString(4))
                .build();
    }

    @Override
    public DatabaseObjectInfo readDatabaseObject(ResultSet rs, String type) throws SQLException {
        return DatabaseObjectInfo.builder()
                .name(rs.getString(1))
                .type(type)
                .comment(rs.getString(2))
                .build();
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

    protected String informationSchemaTablesSql(String namespace, String tableType) {
        return """
                SELECT TABLE_NAME, '' AS ENGINE, COALESCE(TABLE_ROWS, 0) AS TABLE_ROWS, COALESCE(TABLE_COMMENT, '') AS TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_TYPE = '%s'
                ORDER BY TABLE_NAME
                """.formatted(escapeLiteral(namespace), tableType);
    }

    protected String createDatabaseFromTemplate(CreateDatabaseRequest request, String template) {
        return template.formatted(
                quoteIdentifier(request.getName()),
                sanitizeToken(request.getCharset()),
                sanitizeToken(request.getCollation())
        );
    }

    protected String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
