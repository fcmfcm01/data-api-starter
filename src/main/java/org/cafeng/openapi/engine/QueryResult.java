package org.cafeng.openapi.engine;

import java.util.List;
import java.util.Map;

/**
 * Carries the outcome of a query execution.
 *
 * <p>For reads, {@code data} holds the rows and {@code totalCount} is populated
 * only for paginated queries. For writes, {@code affectedRows} indicates how many
 * rows were modified.</p>
 */
public record QueryResult(
    List<Map<String, Object>> data,
    long totalCount,
    int affectedRows
) {}
