package org.cafeng.openapi.engine;

import org.cafeng.openapi.definition.*;
import org.cafeng.openapi.r2dbc.ConnectionFactoryRegistry;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class R2dbcQueryEngineTest {

    private static final String DS_NAME = "test-ds";

    private R2dbcQueryEngine engine;
    private ConnectionFactoryRegistry registry;
    private ConnectionFactory cf;

    @BeforeEach
    void setUp() {
        registry = new ConnectionFactoryRegistry();
        cf = H2ConnectionFactory.inMemory("testdb", "sa", "");
        registry.register(DS_NAME, cf);
        engine = new R2dbcQueryEngine(registry);

        // Create test table
        Mono.from(cf.create())
            .flatMap(conn -> Mono.from(conn.createStatement(
                "CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, user_name VARCHAR(50), email VARCHAR(100))"
            ).execute()).doFinally(s -> Mono.from(conn.close()).subscribe()))
            .block();
        // Clear and insert test data
        Mono.from(cf.create())
            .flatMap(conn -> Mono.from(conn.createStatement(
                "DELETE FROM users"
            ).execute()).doFinally(s -> Mono.from(conn.close()).subscribe()))
            .block();
        Mono.from(cf.create())
            .flatMap(conn -> Mono.from(conn.createStatement(
                "INSERT INTO users (id, user_name, email) VALUES (1, 'Alice', 'alice@test.com')"
            ).execute()).doFinally(s -> Mono.from(conn.close()).subscribe()))
            .block();
        Mono.from(cf.create())
            .flatMap(conn -> Mono.from(conn.createStatement(
                "INSERT INTO users (id, user_name, email) VALUES (2, 'Bob', 'bob@test.com')"
            ).execute()).doFinally(s -> Mono.from(conn.close()).subscribe()))
            .block();
        Mono.from(cf.create())
            .flatMap(conn -> Mono.from(conn.createStatement(
                "INSERT INTO users (id, user_name, email) VALUES (3, 'Charlie', 'charlie@test.com')"
            ).execute()).doFinally(s -> Mono.from(conn.close()).subscribe()))
            .block();
    }

    private ApiDefinition createTestApi() {
        return new ApiDefinition(
            "test-r2dbc", "Test R2DBC", "/api/test", "GET",
            List.of(),
            new ApiSource("r2dbc", DS_NAME, "SELECT 1"),
            new ApiResponse("list", List.of()),
            Map.of(), null
        );
    }

    @Test
    void getType_returnsR2dbc() {
        assertEquals("r2dbc", engine.getType());
    }

    @Test
    void executeQuery_returnsAllRows() {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users", Map.of());
        assertEquals(3, results.size());
    }

    @Test
    void executeQuery_convertsSnakeCaseToCamelCase() {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users WHERE id = 1", Map.of());
        assertEquals(1, results.size());
        assertTrue(results.get(0).containsKey("id"));
        assertTrue(results.get(0).containsKey("userName"));
        assertTrue(results.get(0).containsKey("email"));
        assertFalse(results.get(0).containsKey("user_name"));
    }

    @Test
    void executeQuery_withNamedParameters() {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(
            api, "SELECT * FROM users WHERE id = :id", Map.of("id", 1));
        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).get("userName"));
    }

    @Test
    void executeQuery_withMultipleNamedParameters() {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(
            api, "SELECT * FROM users WHERE id > :minId AND id < :maxId",
            Map.of("minId", 1, "maxId", 3));
        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).get("userName"));
    }

    @Test
    void executeQuery_handlesEmptyResult() {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(
            api, "SELECT * FROM users WHERE id = 999", Map.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void executeQuery_handlesNullParameters() {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users", null);
        assertEquals(3, results.size());
    }

    @Test
    void executeCount_returnsTotal() {
        var api = createTestApi();
        long count = engine.executeCount(api, "SELECT * FROM users", Map.of());
        assertEquals(3, count);
    }

    @Test
    void executeCount_filteredResults() {
        var api = createTestApi();
        long count = engine.executeCount(api, "SELECT * FROM users WHERE id = 1", Map.of());
        assertEquals(1, count);
    }

    @Test
    void executeCount_zeroForNoResults() {
        var api = createTestApi();
        long count = engine.executeCount(api, "SELECT * FROM users WHERE id = 999", Map.of());
        assertEquals(0, count);
    }

    @Test
    void executeCount_withNamedParameters() {
        var api = createTestApi();
        long count = engine.executeCount(
            api, "SELECT * FROM users WHERE id > :minId", Map.of("minId", 1));
        assertEquals(2, count);
    }

    @Test
    void executeInsertViaUpdate() {
        var api = createTestApi();
        QueryResult result = engine.execute(
            api, "INSERT INTO users (id, user_name, email) VALUES (4, 'Dave', 'dave@test.com')", Map.of());
        assertEquals(1, result.affectedRows());

        List<Map<String, Object>> check = engine.executeQuery(api, "SELECT * FROM users WHERE id = 4", Map.of());
        assertEquals(1, check.size());
        assertEquals("Dave", check.get(0).get("userName"));
    }

    @Test
    void executeDeleteViaUpdate() {
        var api = createTestApi();
        QueryResult result = engine.execute(api, "DELETE FROM users WHERE id = 3", Map.of());
        assertEquals(1, result.affectedRows());

        long remaining = engine.executeCount(api, "SELECT * FROM users", Map.of());
        assertEquals(2, remaining);
    }

    @Test
    void executePaginated_returnsDataAndTotal() {
        var api = createTestApi();
        PaginatedResult result = engine.executePaginated(
            api, "SELECT * FROM users ORDER BY id", "SELECT * FROM users", Map.of());
        assertEquals(3, result.data().size());
        assertEquals(3, result.total());
    }

    @Test
    void executeThrowsOnInvalidDatasource() {
        var api = new ApiDefinition(
            "bad-api", "Bad", "/api/bad", "GET",
            List.of(),
            new ApiSource("r2dbc", "nonexistent-ds", "SELECT 1"),
            new ApiResponse("list", List.of()),
            Map.of(), null
        );
        assertThrows(ConnectionFactoryRegistry.ConnectionFactoryNotFoundException.class,
            () -> engine.executeQuery(api, "SELECT 1", Map.of()));
    }

    @Test
    void buildCountSql_stripsOrderBy() {
        String sql = "SELECT * FROM users ORDER BY id";
        String countSql = engine.buildCountSql(sql);
        assertFalse(countSql.toUpperCase().contains("ORDER BY"));
        assertTrue(countSql.contains("SELECT COUNT(*)"));
    }

    @Test
    void buildCountSql_stripsOrderByWithPagination() {
        String sql = "SELECT * FROM users ORDER BY name OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY";
        String countSql = engine.buildCountSql(sql);
        assertFalse(countSql.toUpperCase().contains("ORDER BY"));
        assertFalse(countSql.toUpperCase().contains("OFFSET"));
        assertFalse(countSql.toUpperCase().contains("FETCH"));
    }

    @Test
    void buildCountSql_preservesQueryBodyWithoutOrderBy() {
        String sql = "SELECT * FROM users WHERE id > 1 ORDER BY id";
        String countSql = engine.buildCountSql(sql);
        assertTrue(countSql.contains("WHERE id > 1"));
        assertFalse(countSql.toUpperCase().contains("ORDER BY"));
    }

    @Test
    void buildCountSql_stripsLimitOffset() {
        String sql = "SELECT * FROM users ORDER BY name LIMIT 10 OFFSET 20";
        String countSql = engine.buildCountSql(sql);
        assertFalse(countSql.toUpperCase().contains("LIMIT"));
        assertFalse(countSql.toUpperCase().contains("OFFSET"));
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
    void executeQuery_withNullParameterBinding() {
        var api = createTestApi();
        engine.execute(
            api,
            "INSERT INTO users (id, user_name, email) VALUES (:id, :userName, :email)",
            Map.of("id", 50, "userName", "NullEmail"));

        List<Map<String, Object>> results = engine.executeQuery(
            api, "SELECT * FROM users WHERE id = :id", Map.of("id", 50));
        assertEquals(1, results.size());
        assertEquals("NullEmail", results.get(0).get("userName"));
    }

    @Test
    void executeQuery_resultsInOrder() {
        var api = createTestApi();
        List<Map<String, Object>> results = engine.executeQuery(api, "SELECT * FROM users ORDER BY id", Map.of());
        assertEquals(1, results.get(0).get("id"));
        assertEquals(2, results.get(1).get("id"));
        assertEquals(3, results.get(2).get("id"));
    }

    @Test
    void connectionFactoryRegistry_hasReturnsCorrectly() {
        assertTrue(registry.has(DS_NAME));
        assertFalse(registry.has("nonexistent"));
    }

    @Test
    void connectionFactoryRegistry_getThrowsOnMissing() {
        assertThrows(ConnectionFactoryRegistry.ConnectionFactoryNotFoundException.class,
            () -> registry.get("nonexistent"));
    }
}
