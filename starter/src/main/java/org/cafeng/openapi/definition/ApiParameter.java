package org.cafeng.openapi.definition;

import java.util.List;

/**
 * Describes a single request parameter for an API endpoint.
 *
 * <p>Parameters can originate from query strings ({@code in: query}),
 * path variables ({@code in: path}), or request bodies ({@code in: body}).
 * The compact constructor defaults {@code in} to {@code "query"} and
 * {@code type} to {@code "string"} when null.</p>
 */
public record ApiParameter(
    String name,
    String in,
    String type,
    boolean required,
    String description,
    List<String> enumValues,
    Object defaultValue,
    Integer maxLength,
    Integer minValue,
    Integer maxValue
) {
    public ApiParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("parameter.name is required");
        }
        in = in != null ? in : "query";
        type = type != null ? type : "string";
        required = required;
    }
}
