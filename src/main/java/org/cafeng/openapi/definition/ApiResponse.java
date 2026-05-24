package org.cafeng.openapi.definition;

import java.util.List;

public record ApiResponse(
    String type,
    List<ResponseField> fields
) {
    public ApiResponse {
        if (type == null || type.isBlank()) {
            type = ResponseType.LIST.yamlValue();
        }
        ResponseType.fromYamlValue(type);
    }
}
