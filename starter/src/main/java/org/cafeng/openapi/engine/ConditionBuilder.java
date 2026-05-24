package org.cafeng.openapi.engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/**
 * Processes conditional SQL fragments of the form {@code ${paramName: SQL fragment}}.
 *
 * <p>When a parameter is present and non-empty, its SQL fragment is spliced into the
 * query. When absent, null, or empty, the fragment is omitted. Results are cached
 * keyed on the SQL template and active parameter names.</p>
 *
 * @implNote Thread-safe. Uses {@link ConcurrentHashMap} cache with stable
 * {@link TreeSet}-based keys. Size capped at 5000 entries.
 */
public class ConditionBuilder {

    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\$\\{(\\w+):\\s*([^}]*)\\}");

    private final ConcurrentHashMap<String, CachedCondition> conditionCache = new ConcurrentHashMap<>();

    public ConditionResult build(String baseSql, Map<String, Object> parameters) {
        Map<String, Object> params = parameters != null ? parameters : Map.of();

        String cacheKey = baseSql + "|" + new TreeSet<>(params.keySet());
        if (conditionCache.size() > 5000) conditionCache.clear();
        CachedCondition cached = conditionCache.computeIfAbsent(cacheKey, k -> doBuildTemplate(baseSql, params));

        Map<String, Object> effectiveParams = new LinkedHashMap<>();
        for (String param : cached.activeParams()) {
            effectiveParams.put(param, params.get(param));
        }
        return new ConditionResult(cached.sql(), effectiveParams);
    }

    private CachedCondition doBuildTemplate(String baseSql, Map<String, Object> parameters) {
        StringBuilder sqlBuilder = new StringBuilder();
        List<String> activeParams = new ArrayList<>();

        Matcher matcher = CONDITION_PATTERN.matcher(baseSql);
        int lastEnd = 0;

        while (matcher.find()) {
            sqlBuilder.append(baseSql, lastEnd, matcher.start());

            String paramName = matcher.group(1);
            String sqlFragment = matcher.group(2).trim();
            Object value = parameters.get(paramName);

            if (isPresent(value)) {
                sqlBuilder.append(" ").append(sqlFragment);
                activeParams.add(paramName);
            }

            lastEnd = matcher.end();
        }

        sqlBuilder.append(baseSql.substring(lastEnd));

        return new CachedCondition(sqlBuilder.toString(), List.copyOf(activeParams));
    }

    private boolean isPresent(Object value) {
        if (value == null) return false;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }

    public record ConditionResult(String sql, Map<String, Object> parameters) {}

    private record CachedCondition(String sql, List<String> activeParams) {}
}
