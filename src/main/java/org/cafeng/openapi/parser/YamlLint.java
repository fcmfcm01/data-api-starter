package org.cafeng.openapi.parser;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.engine.SqlOperationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates a list of parsed {@link ApiDefinition} objects at startup time.
 *
 * <p>Checks ID uniqueness, path+method conflicts, required fields, SQL injection
 * patterns in condition syntax, proper parameter binding in condition fragments,
 * and DDL operation usage (warns but does not fail).</p>
 */
public class YamlLint {

    private static final Logger log = LoggerFactory.getLogger(YamlLint.class);

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "\\$\\{[^}]*\\+[^}]*}", Pattern.CASE_INSENSITIVE);

    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\$\\{(\\w+):\\s*([^}]+)\\}");

    public List<String> lint(List<ApiDefinition> apis) {
        List<String> errors = new ArrayList<>();

        checkIdUniqueness(apis, errors);
        checkPathMethodConflicts(apis, errors);
        checkRequiredFields(apis, errors);
        checkSqlInjection(apis, errors);
        checkConditionBinding(apis, errors);
        checkDdlOperations(apis, errors);

        return errors;
    }

    public void lintAndThrow(List<ApiDefinition> apis) {
        List<String> errors = lint(apis);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("YAML lint errors:\n  - " + String.join("\n  - ", errors));
        }
    }

    private void checkIdUniqueness(List<ApiDefinition> apis, List<String> errors) {
        Set<String> ids = new HashSet<>();
        for (ApiDefinition api : apis) {
            if (!ids.add(api.id())) {
                errors.add("Duplicate api.id: '" + api.id() + "' (path: " + api.path() + ")");
            }
        }
    }

    private void checkPathMethodConflicts(List<ApiDefinition> apis, List<String> errors) {
        Set<String> pathMethods = new HashSet<>();
        for (ApiDefinition api : apis) {
            String key = api.method().toUpperCase() + " " + api.path();
            if (!pathMethods.add(key)) {
                errors.add("Duplicate path+method: " + key + " (id: " + api.id() + ")");
            }
        }
    }

    private void checkRequiredFields(List<ApiDefinition> apis, List<String> errors) {
        for (ApiDefinition api : apis) {
            if (api.id() == null || api.id().isBlank()) {
                errors.add("api.id is required (path: " + api.path() + ")");
            }
            if (api.path() == null || api.path().isBlank()) {
                errors.add("api.path is required (id: " + api.id() + ")");
            }
            if (api.method() == null || api.method().isBlank()) {
                errors.add("api.method is required (id: " + api.id() + ")");
            }
            if (api.source() == null) {
                errors.add("api.source is required (id: " + api.id() + ")");
            } else {
                String sourceType = api.source().type();
                if ("http".equals(sourceType)) {
                    if (api.source().url() == null || api.source().url().isBlank()) {
                        errors.add("source.url is required for HTTP type (id: " + api.id() + ")");
                    }
                } else if ("jdbc".equals(sourceType) || "r2dbc".equals(sourceType)) {
                    if (api.source().datasource() == null || api.source().datasource().isBlank()) {
                        errors.add("source.datasource is required for " + sourceType + " type (id: " + api.id() + ")");
                    }
                    if (api.source().query() == null || api.source().query().isBlank()) {
                        errors.add("source.query is required for " + sourceType + " type (id: " + api.id() + ")");
                    }
                } else {
                    errors.add("Unknown source.type '" + sourceType + "' (id: " + api.id() + "). Valid: jdbc, r2dbc, http");
                }
            }
        }
    }

    private void checkSqlInjection(List<ApiDefinition> apis, List<String> errors) {
        for (ApiDefinition api : apis) {
            if (api.source() != null && api.source().query() != null) {
                String query = api.source().query();
                if (SQL_INJECTION_PATTERN.matcher(query).find()) {
                    errors.add("Possible SQL injection pattern in " + api.id() + ": string concatenation detected in query");
                }
            }
        }
    }

    private void checkConditionBinding(List<ApiDefinition> apis, List<String> errors) {
        for (ApiDefinition api : apis) {
            if (api.source() != null && api.source().query() != null) {
                var matcher = CONDITION_PATTERN.matcher(api.source().query());
                while (matcher.find()) {
                    String paramName = matcher.group(1);
                    String sqlFragment = matcher.group(2);
                    if (!sqlFragment.contains(":" + paramName)) {
                        errors.add("Condition fragment in " + api.id() +
                                " for param '" + paramName + "' must contain binding :" + paramName);
                    }
                }
            }
        }
    }

    private void checkDdlOperations(List<ApiDefinition> apis, List<String> errors) {
        for (ApiDefinition api : apis) {
            if (api.source() != null && api.source().query() != null) {
                String query = api.source().query();
                if (SqlOperationUtils.isDdlOperation(query)) {
                    String keyword = query.trim().toUpperCase().split("\\s+")[0];
                    log.warn("DDL operation detected in API '{}': query starts with '{}'. " +
                            "Ensure caller has 'ddl' scope for authorization.",
                            api.id(), keyword);
                }
            }
        }
    }
}
