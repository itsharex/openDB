package com.opendb.model;

public enum DatabaseType {
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", 3306, true),
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", 5432, true),
    ORACLE("Oracle", "oracle.jdbc.OracleDriver", 1521, true),
    H2("H2", "org.h2.Driver", 9092, true);

    private final String displayName;
    private final String driverClassName;
    private final int defaultPort;
    private final boolean supported;

    DatabaseType(String displayName, String driverClassName, int defaultPort, boolean supported) {
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
        this.supported = supported;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public boolean isSupported() {
        return supported;
    }
}
