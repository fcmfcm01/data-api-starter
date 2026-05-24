package org.cafeng.openapi.engine;

import org.cafeng.openapi.datasource.DataSourceRegistry;
import org.cafeng.openapi.definition.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JdbcQueryEngine with H2 in-memory database.
 * H2 doesn't support :namedParams natively, so we use ? placeholders
 * and test the extractParamNames/createStatement logic separately.
 * These tests focus on executeQuery/executeCount/executeUpdate with
 * parameter-free SQL and verify snake_case->camelCase conversion.
 */
class JdbcQueryEngineTest {

    private static final String DS_NAME = "test-ds";
    private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";

    private DataSourceRegistry registry;
    private JdbcQueryEngine engine;

    @BeforeEach
    void setUp() throws SQLException {
        DataSource h2DataSource = createH2DataSource();
        initTestData(h2DataSource);
        registry = new DataSourceRegistry();
        registry.registerDataSource(DS_NAME, h2DataSource);
        engine = new JdbcQueryEngine(registry);
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
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, user_name VARCHAR(50), email VARCHAR(100))");
            stmt.execute("DELETE FROM users");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@test.com')");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 'bob@test.com')");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 'charlie@test.com')");
        }
    }

    private ApiDefinition createTestApi() {
        return new ApiDefinition(
                "test-api", "Test", "/api/test", "GET",
                null,
                new ApiSource("jdbc", DS_NAME, "SELECT 1"),
                new ApiResponse("list", null),
                Map.of(), null
        );
    }

    @Test
    void shouldExecuteSimpleSelect() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users", Map.of());
        assertEquals(3, results.size());
        assertEquals(1, results.get(0).get("id"));
    }

    @Test
    void shouldReturnCorrectColumns() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users WHERE id = 1", Map.of());
        assertEquals(1, results.size());
        assertTrue(results.get(0).containsKey("id"));
        assertTrue(results.get(0).containsKey("userName"));
        assertTrue(results.get(0).containsKey("email"));
    }

    @Test
    void shouldConvertSnakeCaseToCamelCase() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users WHERE id = 1", Map.of());
        Map<String, Object> row = results.get(0);
        assertTrue(row.containsKey("id"));
        assertTrue(row.containsKey("userName"));
        assertTrue(row.containsKey("email"));
        // Snake case originals should NOT be present
        assertFalse(row.containsKey("user_name"));
    }

    @Test
    void shouldExecuteCount() throws SQLException {
        var api = createTestApi();
        long count = engine.executeCount(api, "SELECT * FROM users", Map.of());
        assertEquals(3, count);
    }

    @Test
    void shouldCountFilteredResults() throws SQLException {
        var api = createTestApi();
        long count = engine.executeCount(api, "SELECT * FROM users WHERE id = 1", Map.of());
        assertEquals(1, count);
    }

    @Test
    void shouldExecuteInsertViaUpdate() throws SQLException {
        var api = createTestApi();
        int rows = engine.executeUpdate(api, "INSERT INTO users (id, user_name, email) VALUES (4, 'Dave', 'dave@test.com')", Map.of());
        assertEquals(1, rows);

        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users WHERE id = 4", Map.of());
        assertEquals(1, results.size());
        assertEquals("Dave", results.get(0).get("userName"));
    }

    @Test
    void shouldHandleEmptyResult() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(
                api, "SELECT * FROM users WHERE user_name = 'nonexistent'", Map.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldHandleNullParameters() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users", null);
        assertEquals(3, results.size());
    }

    @Test
    void shouldThrowOnInvalidDatasource() {
        var api = new ApiDefinition(
                "bad-api", "Bad", "/api/bad", "GET",
                null,
                new ApiSource("jdbc", "nonexistent-ds", "SELECT 1"),
                new ApiResponse("list", null),
                Map.of(), null
        );
        assertThrows(DataSourceRegistry.DataSourceNotFoundException.class,
                () -> engine.executeQuery(api, "SELECT 1", Map.of()));
    }

    @Test
    void shouldCountZeroForNoResults() throws SQLException {
        var api = createTestApi();
        long count = engine.executeCount(api, "SELECT * FROM users WHERE id = 999", Map.of());
        assertEquals(0, count);
    }

    @Test
    void shouldExecuteDeleteViaUpdate() throws SQLException {
        var api = createTestApi();
        int rows = engine.executeUpdate(api, "DELETE FROM users WHERE id = 3", Map.of());
        assertEquals(1, rows);

        long remaining = engine.executeCount(api, "SELECT * FROM users", Map.of());
        assertEquals(2, remaining);
    }

    @Test
    void shouldReturnResultsInOrder() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users ORDER BY id", Map.of());
        assertEquals(1, results.get(0).get("id"));
        assertEquals(2, results.get(1).get("id"));
        assertEquals(3, results.get(2).get("id"));
    }

    @Test
    void buildCountSql_shouldStripOrderBy() {
        String sql = "SELECT * FROM users ORDER BY id";
        String countSql = engine.buildCountSql(sql);
        assertFalse(countSql.toUpperCase().contains("ORDER BY"), "COUNT SQL should not contain ORDER BY");
        assertTrue(countSql.contains("SELECT COUNT(*)"));
    }

    @Test
    void buildCountSql_shouldStripOrderByWithPagination() {
        String sql = "SELECT * FROM users ORDER BY name OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY";
        String countSql = engine.buildCountSql(sql);
        assertFalse(countSql.toUpperCase().contains("ORDER BY"), "COUNT SQL should not contain ORDER BY");
        assertFalse(countSql.toUpperCase().contains("OFFSET"), "COUNT SQL should not contain OFFSET");
        assertFalse(countSql.toUpperCase().contains("FETCH"), "COUNT SQL should not contain FETCH");
    }

    @Test
    void buildCountSql_shouldPreserveQueryBodyWithoutOrderBy() {
        String sql = "SELECT * FROM users WHERE id > 1 ORDER BY id";
        String countSql = engine.buildCountSql(sql);
        assertTrue(countSql.contains("WHERE id > 1"), "WHERE clause should be preserved");
        assertFalse(countSql.toUpperCase().contains("ORDER BY"), "ORDER BY should be removed");
    }

    @Test
    void buildCountSql_stripsLimitOffset() {
        String sql = "SELECT * FROM users ORDER BY name LIMIT 10 OFFSET 20";
        String countSql = engine.buildCountSql(sql);
        assertFalse(countSql.toUpperCase().contains("LIMIT"), "COUNT SQL should not contain LIMIT");
        assertFalse(countSql.toUpperCase().contains("OFFSET"), "COUNT SQL should not contain OFFSET");
        assertTrue(countSql.contains("SELECT COUNT(*)"));
    }

    @Test
    void buildCountSql_stripsBothSyntaxes() {
        String mssql = "SELECT * FROM users ORDER BY name OFFSET 5 ROWS FETCH NEXT 10 ROWS ONLY";
        String countMssql = engine.buildCountSql(mssql);
        assertFalse(countMssql.toUpperCase().contains("OFFSET"));
        assertFalse(countMssql.toUpperCase().contains("FETCH"));

        String mysql = "SELECT * FROM users ORDER BY name LIMIT 10 OFFSET 5";
        String countMysql = engine.buildCountSql(mysql);
        assertFalse(countMysql.toUpperCase().contains("LIMIT"));
        assertFalse(countMysql.toUpperCase().contains("OFFSET"));
    }

    @Test
    void shouldHandleMultipleInserts() throws SQLException {
        var api = createTestApi();
        engine.executeUpdate(api, "INSERT INTO users (id, user_name, email) VALUES (10, 'X', 'x@t.com')", Map.of());
        engine.executeUpdate(api, "INSERT INTO users (id, user_name, email) VALUES (11, 'Y', 'y@t.com')", Map.of());

        long count = engine.executeCount(api, "SELECT * FROM users", Map.of());
        assertEquals(5, count);
    }

    @Test
    void shouldExecuteQueryWithNamedParameters() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(
                api, "SELECT * FROM users WHERE id = :id", Map.of("id", 1));
        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).get("userName"));
    }

    @Test
    void shouldExecuteQueryWithMultipleNamedParameters() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(
                api, "SELECT * FROM users WHERE id > :minId AND id < :maxId",
                Map.of("minId", 1, "maxId", 3));
        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).get("userName"));
    }

    @Test
    void shouldExecuteInsertWithNamedParameters() throws SQLException {
        var api = createTestApi();
        int rows = engine.executeUpdate(
                api,
                "INSERT INTO users (id, user_name, email) VALUES (:id, :userName, :email)",
                Map.of("id", 99, "userName", "TestUser", "email", "test@test.com"));
        assertEquals(1, rows);

        List<Map<String, Object>> results = engine.executeQuery(
                api, "SELECT * FROM users WHERE id = :id", Map.of("id", 99));
        assertEquals("TestUser", results.get(0).get("userName"));
    }

    @Test
    void shouldHandleNullParameterBinding() throws SQLException {
        var api = createTestApi();
        engine.executeUpdate(
                api,
                "INSERT INTO users (id, user_name, email) VALUES (:id, :userName, :email)",
                Map.of("id", 50, "userName", "NullEmail"));

        List<Map<String, Object>> results = engine.executeQuery(
                api, "SELECT * FROM users WHERE id = :id", Map.of("id", 50));
        assertEquals(1, results.size());
        assertEquals("NullEmail", results.get(0).get("userName"));
    }

    @Test
    void shouldExecuteCountWithNamedParameters() throws SQLException {
        var api = createTestApi();
        long count = engine.executeCount(
                api, "SELECT * FROM users WHERE id > :minId", Map.of("minId", 1));
        assertEquals(2, count);
    }

    @Test
    void shouldBindParametersInCorrectOrder() throws SQLException {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(
                api,
                "SELECT * FROM users WHERE user_name = :userName AND id = :id",
                Map.of("id", 1, "userName", "Alice"));
        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).get("userName"));
    }
}
