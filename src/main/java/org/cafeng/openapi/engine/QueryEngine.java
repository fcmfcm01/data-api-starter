package org.cafeng.openapi.engine;

import org.cafeng.openapi.definition.ApiDefinition;
import java.util.Map;

/**
 * Strategy interface for executing data source queries.
 *
 * <p>Implementations handle different source types (JDBC, HTTP).
 * The framework selects the appropriate engine based on
 * {@code ApiSource.type()} at request time.</p>
 */
public interface QueryEngine {
    String getType();
    QueryResult execute(ApiDefinition api, String processedQuery, Map<String, Object> parameters);

    default long executeCount(ApiDefinition api, String sql, Map<String, Object> params) throws Exception {
        throw new UnsupportedOperationException("Count not supported by " + getType());
    }
}
