package org.cafeng.openapi.engine;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.error.DataApiException;
import org.cafeng.openapi.r2dbc.ConnectionFactoryRegistry;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/**
 * R2DBC-backed query engine that executes SQL via {@link io.r2dbc.spi.ConnectionFactory}.
 *
 * <p>Provides a blocking ({@code .block()}) facade over reactive R2DBC operations.
 * Named parameters ({@code :paramName}) are converted to {@code ?} placeholders
 * and bound via 0-based positional indexing. Column names are automatically
 * converted from {@code snake_case} to {@code camelCase}.</p>
 *
 * @implNote Thread-safe. Uses {@link ConcurrentHashMap} caches for parsed SQL
 * and camelCase conversions. R2DBC uses 0-based parameter indexing (unlike JDBC's 1-based).
 */
public class R2dbcQueryEngine implements QueryEngine {

    private final ConnectionFactoryRegistry registry;

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([a-zA-Z]\\w*)");
    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("_([a-z])");
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(?i)\\s+ORDER\\s+BY\\s+[^)]+(?=(\\)|$))");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("(?i)\\s+OFFSET\\s+\\d+\\s+ROWS?\\s+");
    private static final Pattern FETCH_PATTERN = Pattern.compile("(?i)\\s+FETCH\\s+NEXT\\s+\\d+\\s+ROWS?\\s+ONLY");
    private static final Pattern LIMIT_OFFSET_PATTERN = Pattern.compile("(?i)\\s+LIMIT\\s+\\d+\\s+OFFSET\\s+\\d+");

    private final ConcurrentHashMap<String, ParsedSql> parsedSqlCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> camelCaseCache = new ConcurrentHashMap<>();

    public R2dbcQueryEngine(ConnectionFactoryRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getType() {
        return "r2dbc";
    }

    @Override
    public QueryResult execute(ApiDefinition api, String processedQuery, Map<String, Object> parameters) {
        try {
            if (SqlOperationUtils.isWriteOperation(processedQuery)) {
                int rows = executeUpdate(api, processedQuery, parameters);
                return new QueryResult(List.of(), -1, rows);
            }
            List<Map<String, Object>> data = executeQuery(api, processedQuery, parameters);
            return new QueryResult(data, -1, -1);
        } catch (Exception e) {
            throw new DataApiException("R2DBC query failed", e);
        }
    }

    public List<Map<String, Object>> executeQuery(ApiDefinition api, String sql, Map<String, Object> params) {
        ConnectionFactory cf = registry.get(api.source().datasource());
        ParsedSql parsed = parsedSqlCache.computeIfAbsent(sql, this::parseSql);

        return Mono.from(cf.create())
            .flatMap(conn -> {
                Statement stmt = conn.createStatement(parsed.jdbcSql());
                bindParameters(stmt, parsed, params);
                return Mono.from(stmt.execute())
                    .flatMapMany(result ->
                        Flux.from(result.map((row, meta) -> {
                            Map<String, Object> rowMap = new LinkedHashMap<>();
                            for (int i = 0; i < meta.getColumnMetadatas().size(); i++) {
                                String colName = meta.getColumnMetadatas().get(i).getName();
                                rowMap.put(toCamelCase(colName), row.get(i));
                            }
                            return rowMap;
                        }))
                    )
                    .collectList()
                    .doFinally(signal -> Mono.from(conn.close()).subscribe());
            })
            .block();
    }

    @Override
    public long executeCount(ApiDefinition api, String originalSql, Map<String, Object> params) {
        String countSql = buildCountSql(originalSql);
        ConnectionFactory cf = registry.get(api.source().datasource());
        ParsedSql parsed = parsedSqlCache.computeIfAbsent(countSql, this::parseSql);

        return Mono.from(cf.create())
            .flatMap(conn -> {
                Statement stmt = conn.createStatement(parsed.jdbcSql());
                bindParameters(stmt, parsed, params);
                return Mono.from(stmt.execute())
                    .flatMap(result ->
                        Mono.from(result.map((row, meta) -> {
                            Object val = row.get(0);
                            return val instanceof Number n ? n.longValue() : 0L;
                        }))
                    )
                    .doFinally(signal -> Mono.from(conn.close()).subscribe());
            })
            .block();
    }

    @Override
    public PaginatedResult executePaginated(ApiDefinition api, String dataSql, String countSql,
            Map<String, Object> params) {
        List<Map<String, Object>> data = executeQuery(api, dataSql, params);
        long total = executeCount(api, countSql, params);
        return new PaginatedResult(data, total);
    }

    private int executeUpdate(ApiDefinition api, String sql, Map<String, Object> params) {
        ConnectionFactory cf = registry.get(api.source().datasource());
        ParsedSql parsed = parsedSqlCache.computeIfAbsent(sql, this::parseSql);

        Long result = Mono.from(cf.create())
            .flatMap(conn -> {
                Statement stmt = conn.createStatement(parsed.jdbcSql());
                bindParameters(stmt, parsed, params);
                return Mono.from(stmt.execute())
                    .flatMap(result1 -> Mono.from(result1.getRowsUpdated()))
                    .doFinally(signal -> Mono.from(conn.close()).subscribe());
            })
            .block();
        return result != null ? result.intValue() : 0;
    }

    private void bindParameters(Statement stmt, ParsedSql parsed, Map<String, Object> params) {
        if (params == null) return;
        // R2DBC uses 0-based index binding (JDBC is 1-based)
        int idx = 0;
        for (String key : parsed.paramNames()) {
            Object value = params.get(key);
            if (value != null) {
                stmt.bind(idx, value);
            } else {
                stmt.bindNull(idx, String.class);
            }
            idx++;
        }
    }

    private ParsedSql parseSql(String sql) {
        List<String> paramNames = new ArrayList<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
        }
        String r2dbcSql = sql.replaceAll(":([a-zA-Z]\\w*)", "?");
        return new ParsedSql(r2dbcSql, List.copyOf(paramNames));
    }

    String buildCountSql(String originalSql) {
        String withoutOrderBy = ORDER_BY_PATTERN.matcher(originalSql).replaceAll("");
        String withoutPagination = OFFSET_PATTERN.matcher(withoutOrderBy).replaceAll(" ");
        withoutPagination = FETCH_PATTERN.matcher(withoutPagination).replaceAll("");
        withoutPagination = LIMIT_OFFSET_PATTERN.matcher(withoutPagination).replaceAll("");
        return "SELECT COUNT(*) FROM (" + withoutPagination + ") AS _count";
    }

    private String toCamelCase(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return columnName;
        }
        return camelCaseCache.computeIfAbsent(columnName, this::convertToCamelCase);
    }

    private String convertToCamelCase(String columnName) {
        Matcher matcher = SNAKE_CASE_PATTERN.matcher(columnName.toLowerCase());
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, matcher.group(1).toUpperCase());
        }
        matcher.appendTail(result);
        return result.length() > 0 ? result.toString() : columnName;
    }
}
