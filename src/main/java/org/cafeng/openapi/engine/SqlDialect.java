package org.cafeng.openapi.engine;

public enum SqlDialect {
    MSSQL,       // OFFSET N ROWS FETCH NEXT M ROWS ONLY
    MYSQL,       // LIMIT M OFFSET N
    POSTGRESQL,  // LIMIT M OFFSET N
    H2;          // LIMIT M OFFSET N (or MSSQL if MODE=MSSQLServer)

    /**
     * Detect dialect from JDBC or R2DBC URL.
     * Supports: jdbc:sqlserver:, jdbc:postgresql:, r2dbc:postgresql:,
     *           jdbc:mysql:, r2dbc:mysql:, jdbc:h2:, r2dbc:h2:
     * H2 with MODE=MSSQLServer in URL params → MSSQL dialect.
     */
    public static SqlDialect fromUrl(String url) {
        if (url == null || url.isBlank()) return H2;
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:sqlserver:") || lower.startsWith("r2dbc:sqlserver:")) return MSSQL;
        if (lower.startsWith("jdbc:postgresql:") || lower.startsWith("r2dbc:postgresql:")) return POSTGRESQL;
        if (lower.startsWith("jdbc:mysql:") || lower.startsWith("r2dbc:mysql:")) return MYSQL;
        if (lower.startsWith("jdbc:h2:") || lower.startsWith("r2dbc:h2:")) {
            if (lower.contains("mode=mssqlserver") || lower.contains("mode=mssql")) return MSSQL;
            return H2;
        }
        return H2;
    }
}
