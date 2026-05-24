package org.cafeng.openapi.engine;

import java.util.Set;

/**
 * Shared SQL operation classification utilities.
 * Centralizes write-operation and DDL detection logic used across the framework.
 */
public final class SqlOperationUtils {

    public static final Set<String> WRITE_PREFIXES = Set.of("INSERT", "UPDATE", "DELETE", "MERGE");

    public static final Set<String> DDL_KEYWORDS = Set.of(
            "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "GRANT", "REVOKE"
    );

    private SqlOperationUtils() {}

    /**
     * Returns true if the SQL statement is a write operation (INSERT, UPDATE, DELETE, MERGE).
     */
    public static boolean isWriteOperation(String sql) {
        if (sql == null) return false;
        String trimmed = sql.trim().toUpperCase();
        return WRITE_PREFIXES.stream().anyMatch(trimmed::startsWith);
    }

    /**
     * Returns true if the SQL statement is a DDL operation (CREATE, ALTER, DROP, etc.).
     */
    public static boolean isDdlOperation(String sql) {
        if (sql == null || sql.isBlank()) return false;
        String trimmed = sql.trim().toUpperCase();
        for (String keyword : DDL_KEYWORDS) {
            if (trimmed.startsWith(keyword)) return true;
        }
        return false;
    }
}
