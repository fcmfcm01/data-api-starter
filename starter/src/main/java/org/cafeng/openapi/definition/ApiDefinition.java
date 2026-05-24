package org.cafeng.openapi.definition;

import java.util.List;
import java.util.Map;

/**
 * Root definition of a single YAML-driven API endpoint.
 *
 * <p>Each YAML file under {@code classpath:apis/} is parsed into one {@code ApiDefinition},
 * which is then registered as a Spring MVC route by {@code DynamicRouterRegistrar}.
 * The compact constructor validates that {@code id}, {@code path}, {@code method},
 * {@code source}, and {@code response} are all present and non-blank.</p>
 */
public record ApiDefinition(
    String id,
    String name,
    String path,
    String method,
    List<ApiParameter> parameters,
    ApiSource source,
    ApiResponse response,
    Map<String, String> scopes,
    ApiSla sla
) {
    public ApiDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("api.id is required");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("api.path is required");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("api.method is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("api.source is required");
        }
        if (response == null) {
            throw new IllegalArgumentException("api.response is required");
        }
    }
}
