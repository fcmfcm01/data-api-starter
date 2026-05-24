package org.cafeng.openapi.param;

import org.cafeng.openapi.definition.ApiParameter;
import org.cafeng.openapi.definition.ApiDefinition;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Extracts, validates, and type-converts request parameters for an API call.
 *
 * <p>Handles query, path, and body parameter sources. Validates required
 * fields and enum constraints. Converts string values to the declared
 * type (integer, long, double, boolean, or string).</p>
 */
public class RequestParameterMapper {

    private final ObjectMapper objectMapper;

    public RequestParameterMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> mapParameters(
            ApiDefinition apiDefinition,
            HttpServletRequest request,
            Map<String, Object> bodyParams) {
        
        Map<String, Object> result = new HashMap<>();
        
        if (apiDefinition.parameters() == null) {
            return result;
        }
        
        for (ApiParameter param : apiDefinition.parameters()) {
            Object value = extractValue(param, request, bodyParams);
            
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                if (param.required()) {
                    throw new IllegalArgumentException("Required parameter missing: " + param.name());
                }
                continue;
            }
            
            if (param.enumValues() != null && !param.enumValues().isEmpty()) {
                if (!param.enumValues().contains(value.toString())) {
                    throw new IllegalArgumentException(
                            "Invalid value for " + param.name() + ": " + value + 
                            ". Must be one of: " + param.enumValues());
                }
            }
            
            result.put(param.name(), convertType(value, param.type()));
        }
        
        return result;
    }

    private Object extractValue(
            ApiParameter param,
            HttpServletRequest request,
            Map<String, Object> bodyParams) {
        
        return switch (param.in().toLowerCase()) {
            case "path" -> request.getAttribute("path." + param.name());
            case "body" -> bodyParams != null ? bodyParams.get(param.name()) : null;
            default -> request.getParameter(param.name());
        };
    }

    private Object convertType(Object value, String type) {
        if (value == null) return null;
        
        return switch (type.toLowerCase()) {
            case "integer", "int" -> {
                if (value instanceof Number) {
                    yield ((Number) value).intValue();
                }
                yield Integer.parseInt(value.toString());
            }
            case "long" -> {
                if (value instanceof Number) {
                    yield ((Number) value).longValue();
                }
                yield Long.parseLong(value.toString());
            }
            case "double" -> {
                if (value instanceof Number) {
                    yield ((Number) value).doubleValue();
                }
                yield Double.parseDouble(value.toString());
            }
            case "boolean" -> {
                if (value instanceof Boolean) {
                    yield value;
                }
                yield Boolean.parseBoolean(value.toString());
            }
            default -> value.toString();
        };
    }
}