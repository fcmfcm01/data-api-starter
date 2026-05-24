package com.example.dialectspike;

import org.cafeng.openapi.engine.PaginationBuilder;
import org.cafeng.openapi.engine.SqlDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialectDetectionTest {

    private final PaginationBuilder paginationBuilder = new PaginationBuilder();

    @Test
    @DisplayName("Detect MSSQL from jdbc:sqlserver URL")
    void detectMssqlFromSqlserverUrl() {
        SqlDialect dialect = SqlDialect.fromUrl("jdbc:sqlserver://localhost:1433;databaseName=test");
        assertEquals(SqlDialect.MSSQL, dialect);
    }

    @Test
    @DisplayName("Detect MySQL from jdbc:mysql URL")
    void detectMysqlFromMysqlUrl() {
        SqlDialect dialect = SqlDialect.fromUrl("jdbc:mysql://localhost:3306/test");
        assertEquals(SqlDialect.MYSQL, dialect);
    }

    @Test
    @DisplayName("Detect PostgreSQL from jdbc:postgresql URL")
    void detectPostgresqlFromUrl() {
        SqlDialect dialect = SqlDialect.fromUrl("jdbc:postgresql://localhost:5432/test");
        assertEquals(SqlDialect.POSTGRESQL, dialect);
    }

    @Test
    @DisplayName("Detect H2 from jdbc:h2 URL")
    void detectH2FromUrl() {
        SqlDialect dialect = SqlDialect.fromUrl("jdbc:h2:mem:test");
        assertEquals(SqlDialect.H2, dialect);
    }

    @Test
    @DisplayName("H2 MODE=MSSQLServer overrides to MSSQL dialect")
    void h2MssqlCompatModeDetectedAsMssql() {
        SqlDialect dialect = SqlDialect.fromUrl("jdbc:h2:mem:test;MODE=MSSQLServer");
        assertEquals(SqlDialect.MSSQL, dialect);
    }

    @Test
    @DisplayName("MSSQL pagination uses OFFSET/FETCH syntax")
    void mssqlPaginationUsesOffsetFetch() {
        String sql = paginationBuilder.build("SELECT * FROM orders", 1, 10, SqlDialect.MSSQL);
        assertTrue(sql.contains("OFFSET"), "MSSQL should use OFFSET");
        assertTrue(sql.contains("FETCH NEXT"), "MSSQL should use FETCH NEXT");
        assertTrue(sql.contains("ROWS ONLY"), "MSSQL should use ROWS ONLY");
    }

    @Test
    @DisplayName("MySQL pagination uses LIMIT/OFFSET syntax")
    void mysqlPaginationUsesLimitOffset() {
        String sql = paginationBuilder.build("SELECT * FROM products", 1, 20, SqlDialect.MYSQL);
        assertTrue(sql.contains("LIMIT"), "MySQL should use LIMIT");
        assertTrue(sql.contains("OFFSET"), "MySQL should use OFFSET");
    }

    @Test
    @DisplayName("PostgreSQL pagination uses LIMIT/OFFSET syntax")
    void postgresqlPaginationUsesLimitOffset() {
        String sql = paginationBuilder.build("SELECT * FROM items", 3, 5, SqlDialect.POSTGRESQL);
        assertTrue(sql.contains("LIMIT"), "PostgreSQL should use LIMIT");
        assertTrue(sql.contains("OFFSET"), "PostgreSQL should use OFFSET");
    }

    @Test
    @DisplayName("H2 pagination uses LIMIT/OFFSET syntax")
    void h2PaginationUsesLimitOffset() {
        String sql = paginationBuilder.build("SELECT * FROM data", 1, 50, SqlDialect.H2);
        assertTrue(sql.contains("LIMIT"), "H2 should use LIMIT");
        assertTrue(sql.contains("OFFSET"), "H2 should use OFFSET");
    }
}
