package org.cafeng.openapi.parser;

import org.cafeng.openapi.definition.*;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.InputStream;
import java.util.*;

/**
 * Parses a single YAML resource into an {@link ApiDefinition}.
 *
 * <p>Handles kebab-case to camelCase property name conversion and
 * populates default values for optional fields.</p>
 */
public class YamlParser {

    private final Yaml yaml;

    public YamlParser() {
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(options);
        PropertyUtils propertyUtils = new PropertyUtils() {
            @Override
            public Property getProperty(Class<?> type, String name) {
                return super.getProperty(type, convertPropertyName(name));
            }
        };
        propertyUtils.setSkipMissingProperties(true);
        constructor.setPropertyUtils(propertyUtils);
        this.yaml = new Yaml(constructor);
    }

    private String convertPropertyName(String name) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_') {
                nextUpper = true;
            } else {
                result.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return result.toString();
    }

    public ApiDefinition parse(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            Map<String, Object> yamlMap = yaml.load(is);
            return parseApiDefinition(yamlMap);
        }
    }

    @SuppressWarnings("unchecked")
    private ApiDefinition parseApiDefinition(Map<String, Object> yamlMap) {
        Map<String, Object> api = (Map<String, Object>) yamlMap.get("api");
        if (api == null) {
            throw new IllegalArgumentException("Missing 'api' section in YAML");
        }

        String id = (String) api.get("id");
        String name = (String) api.get("name");
        String path = (String) api.get("path");
        String method = (String) api.get("method");

        List<ApiParameter> parameters = parseParameters((List<Map<String, Object>>) api.get("parameters"));
        ApiSource source = parseSource((Map<String, Object>) api.get("source"));
        ApiResponse response = parseResponse((Map<String, Object>) api.get("response"));
        Map<String, String> scopes = parseScopes((Map<String, Object>) api.get("scopes"));
        ApiSla sla = parseSla((Map<String, Object>) api.get("sla"));

        return new ApiDefinition(id, name, path, method, parameters, source, response, scopes, sla);
    }

    @SuppressWarnings("unchecked")
    private List<ApiParameter> parseParameters(List<Map<String, Object>> params) {
        if (params == null) return null;
        
        List<ApiParameter> result = new ArrayList<>();
        for (Map<String, Object> p : params) {
            String paramName = (String) p.get("name");
            String in = (String) p.get("in");
            String type = (String) p.get("type");
            Boolean required = (Boolean) p.getOrDefault("required", false);
            String description = (String) p.get("description");
            List<String> enumValues = (List<String>) p.get("enum");
            
            result.add(new ApiParameter(
                    paramName, in, type, required != null && required,
                    description, enumValues, null, null, null, null
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private ApiSource parseSource(Map<String, Object> source) {
        String type = (String) source.getOrDefault("type", "jdbc");
        String datasource = (String) source.get("datasource");
        String query = (String) source.get("query");
        String url = (String) source.get("url");
        String method = (String) source.get("method");
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, Object> headersRaw = (Map<String, Object>) source.get("headers");
        if (headersRaw != null) {
            headersRaw.forEach((k, v) -> headers.put(k, String.valueOf(v)));
        }
        int timeout = 0;
        Object timeoutVal = source.get("timeout");
        if (timeoutVal instanceof Number n) {
            timeout = n.intValue();
        }
        return new ApiSource(type, datasource, query, url, method, headers, timeout);
    }

    @SuppressWarnings("unchecked")
    private ApiResponse parseResponse(Map<String, Object> response) {
        String type = (String) response.getOrDefault("type", "list");
        List<ResponseField> fields = new ArrayList<>();
        
        List<Map<String, Object>> fieldList = (List<Map<String, Object>>) response.get("fields");
        if (fieldList != null) {
            for (Map<String, Object> f : fieldList) {
                String fieldName = (String) f.get("name");
                String scope = (String) f.getOrDefault("scope", "basic");
                Boolean pii = (Boolean) f.getOrDefault("pii", false);
                String description = (String) f.get("description");
                fields.add(new ResponseField(fieldName, scope, pii != null && pii, description));
            }
        }
        
        return new ApiResponse(type, fields);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseScopes(Map<String, Object> scopes) {
        if (scopes == null) return Map.of();
        
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : scopes.entrySet()) {
            result.put(entry.getKey(), (String) entry.getValue());
        }
        return result;
    }

    private ApiSla parseSla(Map<String, Object> sla) {
        if (sla == null) return null;
        
        Integer timeout = null;
        Integer rateLimit = null;
        
        if (sla.get("timeout") != null) {
            timeout = ((Number) sla.get("timeout")).intValue();
        }
        if (sla.get("rateLimit") != null) {
            rateLimit = ((Number) sla.get("rateLimit")).intValue();
        }
        
        return new ApiSla(timeout, rateLimit);
    }
}
