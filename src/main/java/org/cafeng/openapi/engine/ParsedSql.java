package org.cafeng.openapi.engine;

import java.util.List;

/**
 * Holds a SQL statement converted from named parameters to JDBC placeholders,
 * along with the ordered list of parameter names for binding.
 */
public record ParsedSql(String jdbcSql, List<String> paramNames) {
}
