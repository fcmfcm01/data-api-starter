package com.example.h2mssql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class H2MssqlCompatSpikeApplicationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS orders");
        jdbcTemplate.execute("""
            CREATE TABLE orders (
                id INT IDENTITY PRIMARY KEY,
                order_no VARCHAR(50) NOT NULL,
                status VARCHAR(20) DEFAULT 'PENDING',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                amount DECIMAL(10,2)
            )
        """);
        jdbcTemplate.execute("INSERT INTO orders (order_no, status, amount) VALUES ('ORD001', 'ACTIVE', 100.50)");
        jdbcTemplate.execute("INSERT INTO orders (order_no, status, amount) VALUES ('ORD002', 'PENDING', 200.00)");
        jdbcTemplate.execute("INSERT INTO orders (order_no, status, amount) VALUES ('ORD003', 'ACTIVE', 300.75)");
    }

    @Test
    void shouldConnectToH2Database() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertEquals(3, count);
    }

    @Test
    void shouldSupportOffsetFetchSyntax() {
        String sql = """
            SELECT order_no, status, amount 
            FROM orders 
            WHERE status = :status 
            ORDER BY order_no 
            OFFSET :offset ROWS 
            FETCH NEXT :size ROWS ONLY
        """;

        List<Map<String, Object>> results = namedParameterJdbcTemplate.queryForList(
            sql,
            Map.of("status", "ACTIVE", "offset", 0, "size", 10)
        );

        assertEquals(2, results.size());
        assertEquals("ORD001", results.get(0).get("ORDER_NO"));
        assertEquals("ORD003", results.get(1).get("ORDER_NO"));
    }

    @Test
    void shouldSupportParameterizedQuery() {
        String sql = "SELECT COUNT(*) FROM orders WHERE status = :status";
        Integer count = namedParameterJdbcTemplate.queryForObject(
            sql,
            Map.of("status", "ACTIVE"),
            Integer.class
        );
        assertEquals(2, count);
    }

    @Test
    void shouldSupportPaginationWithOffset() {
        String sql = """
            SELECT order_no, status 
            FROM orders 
            ORDER BY order_no 
            OFFSET 1 ROWS 
            FETCH NEXT 1 ROWS ONLY
        """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        assertEquals(1, results.size());
        assertEquals("ORD002", results.get(0).get("ORDER_NO"));
    }

    @Test
    void shouldSupportCountSubquery() {
        String baseQuery = "SELECT * FROM orders WHERE status = 'ACTIVE'";
        String countSql = "SELECT COUNT(*) FROM (" + baseQuery + ") AS _count";
        
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
        assertEquals(2, count);
    }

    @Test
    void shouldConvertSnakeCaseToCamelCase() {
        String sql = "SELECT order_no, created_at FROM orders WHERE id = 1";
        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        
        assertTrue(result.containsKey("ORDER_NO"));
        assertTrue(result.containsKey("CREATED_AT"));
    }
}
