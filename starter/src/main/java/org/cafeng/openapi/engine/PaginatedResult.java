package org.cafeng.openapi.engine;

import java.util.List;
import java.util.Map;

/**
 * Holds the result of a paginated query executed on a single JDBC connection.
 *
 * <p>Contains both the data rows and the total count obtained by running
 * the data query and count query sequentially on the same connection.</p>
 */
public record PaginatedResult(
    List<Map<String, Object>> data,
    long total
) {}
