package com.opendb.dialect;

import com.opendb.exception.OpenDbException;
import com.opendb.model.DatabaseType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DialectRegistry {

    private final Map<DatabaseType, SqlDialect> dialects = new EnumMap<>(DatabaseType.class);

    public DialectRegistry(List<SqlDialect> dialectList) {
        for (SqlDialect dialect : dialectList) {
            dialects.put(dialect.getType(), dialect);
        }
    }

    public SqlDialect require(DatabaseType type) {
        SqlDialect dialect = dialects.get(type);
        if (dialect == null) {
            throw new OpenDbException("Unsupported database type: " + type);
        }
        return dialect;
    }
}
