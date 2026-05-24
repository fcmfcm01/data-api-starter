package org.cafeng.openapi.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaginationBuilderTest {

    private final PaginationBuilder builder = new PaginationBuilder();

    @Test
    void shouldBuildPaginationForFirstPage() {
        String result = builder.build("SELECT * FROM orders", 1, 20);
        assertEquals("SELECT * FROM orders OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY", result);
    }

    @Test
    void shouldBuildPaginationForSecondPage() {
        String result = builder.build("SELECT * FROM orders", 2, 20);
        assertEquals("SELECT * FROM orders OFFSET 20 ROWS FETCH NEXT 20 ROWS ONLY", result);
    }

    @Test
    void shouldCalculateCorrectOffset() {
        String result = builder.build("SELECT * FROM orders", 5, 10);
        assertEquals("SELECT * FROM orders OFFSET 40 ROWS FETCH NEXT 10 ROWS ONLY", result);
    }

    @Test
    void shouldThrowOnZeroPage() {
        assertThrows(IllegalArgumentException.class, () -> builder.build("SELECT * FROM orders", 0, 20));
    }

    @Test
    void shouldThrowOnZeroSize() {
        assertThrows(IllegalArgumentException.class, () -> builder.build("SELECT * FROM orders", 1, 0));
    }

    @Test
    void shouldThrowOnNegativePage() {
        assertThrows(IllegalArgumentException.class, () -> builder.build("SELECT * FROM orders", -1, 20));
    }

    @Test
    void shouldHandleSizeOne() {
        String result = builder.build("SELECT * FROM orders", 1, 1);
        assertEquals("SELECT * FROM orders OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY", result);
    }

    @Test
    void shouldThrowOnPageExceedingMax() {
        assertThrows(IllegalArgumentException.class,
                () -> builder.build("SELECT * FROM orders", 10001, 20));
    }

    @Test
    void shouldAcceptPageAtMax() {
        String result = builder.build("SELECT * FROM orders", 10000, 10);
        assertEquals("SELECT * FROM orders OFFSET 99990 ROWS FETCH NEXT 10 ROWS ONLY", result);
    }

    @Test
    void shouldHandleMaxBoundaryOffset() {
        String result = builder.build("SELECT * FROM orders", 10000, 1000);
        assertEquals("SELECT * FROM orders OFFSET 9999000 ROWS FETCH NEXT 1000 ROWS ONLY", result);
    }

    @Test
    void build_mssqlDialect_usesOffsetFetch() {
        String result = builder.build("SELECT * FROM orders", 2, 10, SqlDialect.MSSQL);
        assertEquals("SELECT * FROM orders OFFSET 10 ROWS FETCH NEXT 10 ROWS ONLY", result);
    }

    @Test
    void build_mysqlDialect_usesLimitOffset() {
        String result = builder.build("SELECT * FROM orders", 2, 10, SqlDialect.MYSQL);
        assertEquals("SELECT * FROM orders LIMIT 10 OFFSET 10", result);
    }

    @Test
    void build_postgresqlDialect_usesLimitOffset() {
        String result = builder.build("SELECT * FROM orders", 3, 5, SqlDialect.POSTGRESQL);
        assertEquals("SELECT * FROM orders LIMIT 5 OFFSET 10", result);
    }

    @Test
    void build_h2Dialect_usesLimitOffset() {
        String result = builder.build("SELECT * FROM orders", 1, 20, SqlDialect.H2);
        assertEquals("SELECT * FROM orders LIMIT 20 OFFSET 0", result);
    }

    @Test
    void build_defaultOverload_usesMssqlSyntax() {
        String result = builder.build("SELECT * FROM orders", 1, 10);
        assertEquals("SELECT * FROM orders OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY", result);
    }
}
