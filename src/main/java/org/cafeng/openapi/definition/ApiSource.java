package org.cafeng.openapi.definition;

import java.util.Map;

/**
 * Data source configuration for an API endpoint.
 *
 * <p>Supports three source types: {@code jdbc} (default) for JDBC database queries,
 * {@code r2dbc} for reactive R2DBC database queries, and {@code http} for forwarding
 * requests to an upstream service. JDBC/R2DBC sources require {@code datasource} and
 * {@code query}; HTTP sources require {@code url}.</p>
 */
public record ApiSource(
    String type,
    String datasource,
    String query,
    String url,
    String method,
    Map<String, String> headers,
    int timeout
) {
    public ApiSource {
        if (type == null || type.isBlank()) {
            type = "jdbc";
        }
        if ("jdbc".equals(type) || "r2dbc".equals(type)) {
            if (datasource == null || datasource.isBlank()) {
                throw new IllegalArgumentException("source.datasource is required for " + type.toUpperCase() + " type");
            }
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("source.query is required for " + type.toUpperCase() + " type");
            }
        }
        if ("http".equals(type)) {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("source.url is required for HTTP type");
            }
        }
    }

    // Compact constructor for JDBC (backward compat)
    public ApiSource(String type, String datasource, String query) {
        this(type, datasource, query, null, null, null, 0);
    }
}
