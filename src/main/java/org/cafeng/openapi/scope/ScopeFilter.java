package org.cafeng.openapi.scope;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.definition.ResponseField;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strips response fields the caller is not authorized to see.
 *
 * <p>Builds a scope hierarchy from the field declaration order in the YAML
 * (e.g. basic &lt; detail &lt; financial) and keeps only fields at or below
 * the caller's highest granted tier. Results are cached per field configuration
 * and scope combination.</p>
 */
public class ScopeFilter {

    private final boolean strictScopes;

    private final ConcurrentHashMap<String, Set<String>> scopeFieldCache = new ConcurrentHashMap<>();

    public ScopeFilter() {
        this(false);
    }

    public ScopeFilter(boolean strictScopes) {
        this.strictScopes = strictScopes;
    }

    public List<Map<String, Object>> apply(
            List<Map<String, Object>> data,
            Set<String> callerScopes,
            ApiDefinition apiDefinition) {

        if (apiDefinition.response() == null || apiDefinition.response().fields() == null) {
            return data;
        }

        List<ResponseField> fields = apiDefinition.response().fields();

        // Build scope hierarchy from field declaration order (basic=0, detail=1, sensitive=2)
        List<String> scopeHierarchy = new ArrayList<>();
        for (ResponseField field : fields) {
            if (!scopeHierarchy.contains(field.scope())) {
                scopeHierarchy.add(field.scope());
            }
        }

        Set<String> allowedFields;

        if (callerScopes == null || callerScopes.isEmpty()) {
            if (strictScopes) {
                return emptyRows(data.size());
            }
            allowedFields = scopeFieldCache.computeIfAbsent(
                    buildCacheKey(fields, null), k -> excludeSensitiveFields(fields));
        } else {
            int highestIndex = -1;
            for (String scope : callerScopes) {
                int idx = scopeHierarchy.indexOf(scope);
                if (idx >= 0 && idx > highestIndex) {
                    highestIndex = idx;
                }
            }

            if (highestIndex < 0) {
                if (strictScopes) {
                    return emptyRows(data.size());
                }
                allowedFields = scopeFieldCache.computeIfAbsent(
                        buildCacheKey(fields, null), k -> excludeSensitiveFields(fields));
            } else {
                final int hi = highestIndex;
                String scopeKey = buildCacheKey(fields, callerScopes);
                allowedFields = scopeFieldCache.computeIfAbsent(scopeKey, k -> {
                    Set<String> resolved = new HashSet<>();
                    for (int i = 0; i <= hi; i++) {
                        String scopeLevel = scopeHierarchy.get(i);
                        for (ResponseField field : fields) {
                            if (scopeLevel.equals(field.scope())) {
                                resolved.add(field.name());
                            }
                        }
                    }
                    return resolved;
                });
            }
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Map<String, Object> filteredRow = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (allowedFields.contains(entry.getKey())) {
                    filteredRow.put(entry.getKey(), entry.getValue());
                }
            }
            filtered.add(filteredRow);
        }

        return filtered;
    }

    private List<Map<String, Object>> emptyRows(int count) {
        List<Map<String, Object>> empty = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            empty.add(new LinkedHashMap<>());
        }
        return empty;
    }

    private Set<String> excludeSensitiveFields(List<ResponseField> fields) {
        Set<String> allowedFields = new HashSet<>();
        for (ResponseField field : fields) {
            if (!"sensitive".equals(field.scope())) {
                allowedFields.add(field.name());
            }
        }
        return allowedFields;
    }

    private String buildCacheKey(List<ResponseField> fields, Set<String> callerScopes) {
        StringBuilder key = new StringBuilder();
        for (ResponseField f : fields) {
            key.append(f.name()).append(':').append(f.scope()).append(',');
        }
        key.append('|');
        if (callerScopes != null) {
            for (String s : callerScopes.stream().sorted().toList()) {
                key.append(s).append(',');
            }
        }
        return key.toString();
    }
}
