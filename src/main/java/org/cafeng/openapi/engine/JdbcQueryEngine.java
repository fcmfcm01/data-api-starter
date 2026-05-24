package org.cafeng.openapi.engine;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.datasource.DataSourceRegistry;
import org.cafeng.openapi.error.DataApiException;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/**
 * JDBC-backed query engine that executes SQL via {@code PreparedStatement}.
 *
 * <p>Converts named parameters ({@code :paramName}) to JDBC positional placeholders
 * and binds values through {@code setObject()}. Column names are automatically
 * converted from {@code snake_case} to {@code camelCase}. Parsed SQL and
 * camelCase conversions are cached for performance.</p>
 */
public class JdbcQueryEngine implements QueryEngine {

    private final DataSourceRegistry dataSourceRegistry;
    private final int fetchSize;
    
    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("_([a-z])");
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([a-zA-Z]\\w*)");

    private final ConcurrentHashMap<String, ParsedSql> parsedSqlCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> camelCaseCache = new ConcurrentHashMap<>();

    public JdbcQueryEngine(DataSourceRegistry dataSourceRegistry) {
        this(dataSourceRegistry, 100);
    }

    public JdbcQueryEngine(DataSourceRegistry dataSourceRegistry, int fetchSize) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.fetchSize = fetchSize;
    }

    @Override
    public String getType() {
        return "jdbc";
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
        } catch (SQLException e) {
            throw new DataApiException("Query execution failed", e);
        }
    }

    public List<Map<String, Object>> executeQuery(
            ApiDefinition apiDefinition,
            String sql,
            Map<String, Object> parameters) throws SQLException {
        
        var dataSource = dataSourceRegistry.getDataSource(apiDefinition.source().datasource());
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = createStatement(conn, sql, parameters)) {
            
            setQueryTimeout(stmt, apiDefinition);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        }
    }

    @Override
    public long executeCount(
            ApiDefinition apiDefinition,
            String originalSql,
            Map<String, Object> parameters) throws SQLException {
        
        String countSql = buildCountSql(originalSql);
        
        var dataSource = dataSourceRegistry.getDataSource(apiDefinition.source().datasource());
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = createStatement(conn, countSql, parameters);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    public int executeUpdate(
            ApiDefinition apiDefinition,
            String sql,
            Map<String, Object> parameters) throws SQLException {
        
        var dataSource = dataSourceRegistry.getDataSource(apiDefinition.source().datasource());
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = createStatement(conn, sql, parameters)) {
            
            return stmt.executeUpdate();
        }
    }

    private PreparedStatement createStatement(
            Connection conn, String sql, Map<String, Object> parameters) throws SQLException {
        
        ParsedSql parsed = parsedSqlCache.computeIfAbsent(sql, this::parseSql);
        
        PreparedStatement stmt = conn.prepareStatement(parsed.jdbcSql());
        
        if (fetchSize > 0) {
            stmt.setFetchSize(fetchSize);
        }
        
        if (parameters != null) {
            int idx = 1;
            for (String key : parsed.paramNames()) {
                Object value = parameters.get(key);
                if (value != null && !value.toString().isEmpty()) {
                    stmt.setObject(idx++, value);
                } else {
                    stmt.setObject(idx++, null);
                }
            }
        }
        
        return stmt;
    }

    private ParsedSql parseSql(String sql) {
        List<String> paramNames = new ArrayList<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
        }
        String jdbcSql = sql.replaceAll(":([a-zA-Z]\\w*)", "?");
        return new ParsedSql(jdbcSql, List.copyOf(paramNames));
    }

    private void setQueryTimeout(PreparedStatement stmt, ApiDefinition apiDefinition) throws SQLException {
        int timeout = apiDefinition.sla() != null && apiDefinition.sla().timeout() != null
                ? apiDefinition.sla().timeout() / 1000
                : 5;
        stmt.setQueryTimeout(timeout);
    }

    String buildCountSql(String originalSql) {
        String withoutOrderBy = originalSql
                .replaceAll("(?i)\\s+ORDER\\s+BY\\s+[^)]+(?=(\\)|$))", "");
        String withoutPagination = withoutOrderBy
                .replaceAll("(?i)\\s+OFFSET\\s+\\d+\\s+ROWS?\\s+", " ")
                .replaceAll("(?i)\\s+FETCH\\s+NEXT\\s+\\d+\\s+ROWS?\\s+ONLY", "");

        return "SELECT COUNT(*) FROM (" + withoutPagination + ") AS _count";
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i);
                String camelCaseName = toCamelCase(columnName);
                row.put(camelCaseName, rs.getObject(i));
            }
            result.add(row);
        }
        
        return result;
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
