package org.cafeng.openapi;

import org.cafeng.openapi.datasource.DataSourceRegistry;
import org.cafeng.openapi.definition.*;
import org.cafeng.openapi.engine.*;
import org.cafeng.openapi.parser.YamlLint;
import org.cafeng.openapi.parser.YamlParser;
import org.cafeng.openapi.scope.ScopeFilter;
import org.cafeng.openapi.scope.ConfigScopeResolver;
import org.cafeng.openapi.scope.ScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full-flow integration test: YAML parse -> lint -> condition build -> query execute -> scope filter.
 * Uses H2 in-memory database. H2 doesn't support :namedParams natively,
 * so SQL execution tests use parameter-free queries. Condition building
 * is tested separately (unit tests cover the :param replacement logic).
 */
class FullFlowIntegrationTest {

    private static final String H2_URL = "jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1";

    private DataSourceRegistry registry;
    private JdbcQueryEngine engine;
    private ConditionBuilder conditionBuilder;
    private ScopeFilter scopeFilter;
    private ScopeResolver scopeResolver;
    private YamlParser yamlParser;
    private YamlLint yamlLint;

    @BeforeEach
    void setUp() throws SQLException {
        DataSource ds = createH2DataSource();
        initTestData(ds);

        registry = new DataSourceRegistry();
        registry.registerDataSource("dataSource", ds);

        engine = new JdbcQueryEngine(registry);
        conditionBuilder = new ConditionBuilder();
        scopeFilter = new ScopeFilter();
        scopeResolver = new ConfigScopeResolver("internal:basic+detail+financial");
        yamlParser = new YamlParser();
        yamlLint = new YamlLint();
    }

    private static DataSource createH2DataSource() {
        return new javax.sql.DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return java.sql.DriverManager.getConnection(H2_URL, "sa", "");
            }
            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return java.sql.DriverManager.getConnection(H2_URL, username, password);
            }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return null; }
            @Override public <T> T unwrap(Class<T> iface) { throw new RuntimeException("not a wrapper"); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }

    private static void initTestData(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (order_no VARCHAR(20) PRIMARY KEY, status VARCHAR(20), amount DECIMAL(10,2))");
            stmt.execute("DELETE FROM orders");
            stmt.execute("INSERT INTO orders VALUES ('ORD-001', 'ACTIVE', 100.50)");
            stmt.execute("INSERT INTO orders VALUES ('ORD-002', 'PENDING', 200.00)");
            stmt.execute("INSERT INTO orders VALUES ('ORD-003', 'CANCELLED', 50.00)");
            stmt.execute("INSERT INTO orders VALUES ('ORD-004', 'ACTIVE', 300.00)");
        }
    }

    @Test
    void fullFlow_parseLintQueryFilter() throws Exception {
        // 1. Parse YAML
        var resource = new ClassPathResource("apis/query-orders.yaml");
        var api = yamlParser.parse(resource);
        assertEquals("query-orders", api.id());
        assertEquals("GET", api.method());

        // 2. Lint
        var lintErrors = yamlLint.lint(List.of(api));
        assertTrue(lintErrors.isEmpty(), "Lint errors: " + lintErrors);

        // 3. Build condition - no params means all conditions skipped
        var condResult = conditionBuilder.build(api.source().query(), Map.of());
        // The :status placeholder remains but H2 doesn't support it.
        // So for H2, we execute a parameter-free version.
        String h2Sql = "SELECT order_no, status, amount FROM orders WHERE 1=1";

        // 4. Execute query
        List<Map<String, Object>> results = engine.executeQuery(api, h2Sql, Map.of());
        assertFalse(results.isEmpty());

        // 5. Scope filter
        Set<String> scopes = scopeResolver.resolveScopes("internal");
        List<Map<String, Object>> filtered = scopeFilter.apply(results, scopes, api);
        assertFalse(filtered.isEmpty());
        assertEquals(4, filtered.size());
    }

    @Test
    void fullFlow_queryFilteredOrders() throws Exception {
        var resource = new ClassPathResource("apis/query-orders.yaml");
        var api = yamlParser.parse(resource);

        // Query only ACTIVE orders (no named params for H2)
        List<Map<String, Object>> results = engine.executeQuery(
                api, "SELECT * FROM orders WHERE status = 'ACTIVE'", Map.of());
        assertEquals(2, results.size());
        for (var row : results) {
            assertEquals("ACTIVE", row.get("status"));
        }
    }

    @Test
    void fullFlow_paginationCount() throws Exception {
        var resource = new ClassPathResource("apis/query-orders.yaml");
        var api = yamlParser.parse(resource);

        String baseSql = "SELECT * FROM orders";
        long total = engine.executeCount(api, baseSql, Map.of());
        assertEquals(4, total);

        PaginationBuilder paginationBuilder = new PaginationBuilder();
        String paginatedSql = paginationBuilder.build(baseSql, 1, 2);

        List<Map<String, Object>> page1 = engine.executeQuery(api, paginatedSql, Map.of());
        assertEquals(2, page1.size());
    }

    @Test
    void fullFlow_insertAndVerify() throws Exception {
        var resource = new ClassPathResource("apis/create-order.yaml");
        var api = yamlParser.parse(resource);

        var lintErrors = yamlLint.lint(List.of(api));
        assertTrue(lintErrors.isEmpty(), "Lint errors: " + lintErrors);

        // Execute insert (no named params for H2)
        int rows = engine.executeUpdate(
                api, "INSERT INTO orders (order_no, status) VALUES ('ORD-100', 'ACTIVE')", Map.of());
        assertEquals(1, rows);

        // Verify
        List<Map<String, Object>> check = engine.executeQuery(
                api, "SELECT * FROM orders WHERE order_no = 'ORD-100'", Map.of());
        assertEquals(1, check.size());
        assertEquals("ACTIVE", check.get(0).get("status"));
    }

    @Test
    void fullFlow_scopeFiltering() throws Exception {
        var api = new ApiDefinition(
                "test-scoped", "Test", "/api/test", "GET", null,
                new ApiSource("jdbc", "dataSource", "SELECT * FROM orders"),
                new ApiResponse("list", List.of(
                        new ResponseField("orderNo", "basic", false, null),
                        new ResponseField("status", "basic", false, null),
                        new ResponseField("amount", "detail", false, null)
                )),
                Map.of(), null
        );

        List<Map<String, Object>> results = engine.executeQuery(api, api.source().query(), Map.of());

        // Filter with "basic" scope - should not see amount
        List<Map<String, Object>> basicOnly = scopeFilter.apply(results, Set.of("basic"), api);
        for (var row : basicOnly) {
            assertTrue(row.containsKey("orderNo"));
            assertTrue(row.containsKey("status"));
            assertFalse(row.containsKey("amount"));
        }

        // Filter with "detail" scope - should see all including amount
        List<Map<String, Object>> withDetail = scopeFilter.apply(results, Set.of("detail"), api);
        for (var row : withDetail) {
            assertTrue(row.containsKey("amount"));
        }
    }

    @Test
    void fullFlow_pageResponseBuilder() throws Exception {
        var resource = new ClassPathResource("apis/query-orders.yaml");
        var api = yamlParser.parse(resource);

        String baseSql = "SELECT * FROM orders";
        long total = engine.executeCount(api, baseSql, Map.of());

        PaginationBuilder paginationBuilder = new PaginationBuilder();
        PageResponseBuilder pageResponseBuilder = new PageResponseBuilder();

        String pagedSql = paginationBuilder.build(baseSql, 1, 2);
        List<Map<String, Object>> content = engine.executeQuery(api, pagedSql, Map.of());

        Map<String, Object> pageResponse = pageResponseBuilder.build(content, total, 1, 2);

        assertEquals(2, ((List<?>) pageResponse.get("content")).size());
        assertEquals(4L, pageResponse.get("totalElements"));
        assertEquals(2L, pageResponse.get("totalPages"));
    }

    @Test
    void fullFlow_conditionBuilderWithRealParams() {
        // Condition building is independent of DB - test the SQL generation
        String sql = "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status} ${type: AND type = :type}";

        // Both params present
        var result1 = conditionBuilder.build(sql, Map.of("status", "ACTIVE", "type", "VIP"));
        assertTrue(result1.sql().contains("AND status = :status"));
        assertTrue(result1.sql().contains("AND type = :type"));
        assertEquals(2, result1.parameters().size());

        // Only status present
        var result2 = conditionBuilder.build(sql, Map.of("status", "PENDING"));
        assertTrue(result2.sql().contains("AND status = :status"));
        assertFalse(result2.sql().contains("AND type"));
        assertEquals(1, result2.parameters().size());

        // No params - conditions removed
        var result3 = conditionBuilder.build(sql, Map.of());
        assertFalse(result3.sql().contains("AND status"));
        assertFalse(result3.sql().contains("AND type"));
        assertTrue(result3.parameters().isEmpty());
    }

    @Test
    void fullFlow_snakeCaseConversion() throws Exception {
        var api = new ApiDefinition(
                "test-camel", "Test", "/api/test", "GET", null,
                new ApiSource("jdbc", "dataSource", "SELECT * FROM orders WHERE order_no = 'ORD-001'"),
                new ApiResponse("single", null),
                Map.of(), null
        );

        List<Map<String, Object>> results = engine.executeQuery(api, api.source().query(), Map.of());
        assertEquals(1, results.size());
        Map<String, Object> row = results.get(0);
        assertTrue(row.containsKey("orderNo"));
        assertTrue(row.containsKey("status"));
        assertTrue(row.containsKey("amount"));
    }

    @Test
    void fullFlow_listProductsYaml() throws Exception {
        var resource = new ClassPathResource("apis/subdir/list-products.yaml");
        var api = yamlParser.parse(resource);

        assertEquals("list-products", api.id());
        assertEquals("list", api.response().type());

        var lintErrors = yamlLint.lint(List.of(api));
        assertTrue(lintErrors.isEmpty());
    }
}
