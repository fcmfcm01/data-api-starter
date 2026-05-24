package org.cafeng.openapi.definition;

import java.util.List;

/**
 * Defines the shape of an API response.
 *
 * <p>Three response types are supported: {@code page} (paginated list with total count),
 * {@code list} (flat array), and {@code single} (one object). The compact constructor
 * defaults to {@code "list"} when type is null or blank.</p>
 */
public record ApiResponse(
    String type,
    List<ResponseField> fields
) {
    public ApiResponse {
        if (type == null || type.isBlank()) {
            type = "list";
        }
        if (!List.of("page", "list", "single").contains(type)) {
            throw new IllegalArgumentException("response.type must be one of: page, list, single");
        }
    }
}
