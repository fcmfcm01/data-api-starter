package org.cafeng.openapi.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlDialectTest {

    @Test
    void shouldDetectMssqlFromJdbcUrl() {
        assertEquals(SqlDialect.MSSQL, SqlDialect.fromUrl("jdbc:sqlserver://host:1433/db"));
    }

    @Test
    void shouldDetectPostgresqlFromJdbcUrl() {
        assertEquals(SqlDialect.POSTGRESQL, SqlDialect.fromUrl("jdbc:postgresql://host:5432/db"));
    }

    @Test
    void shouldDetectMysqlFromJdbcUrl() {
        assertEquals(SqlDialect.MYSQL, SqlDialect.fromUrl("jdbc:mysql://host:3306/db"));
    }

    @Test
    void shouldDetectH2FromJdbcUrl() {
        assertEquals(SqlDialect.H2, SqlDialect.fromUrl("jdbc:h2:mem:test"));
    }

    @Test
    void shouldDetectH2WithMssqlModeAsMssql() {
        assertEquals(SqlDialect.MSSQL, SqlDialect.fromUrl("jdbc:h2:mem:test;MODE=MSSQLServer"));
    }

    @Test
    void shouldDetectPostgresqlFromR2dbcUrl() {
        assertEquals(SqlDialect.POSTGRESQL, SqlDialect.fromUrl("r2dbc:postgresql://host/db"));
    }

    @Test
    void shouldDetectMysqlFromR2dbcUrl() {
        assertEquals(SqlDialect.MYSQL, SqlDialect.fromUrl("r2dbc:mysql://host/db"));
    }

    @Test
    void shouldDetectH2FromR2dbcUrl() {
        assertEquals(SqlDialect.H2, SqlDialect.fromUrl("r2dbc:h2:mem:///test"));
    }

    @Test
    void shouldReturnH2ForNullOrBlankUrl() {
        assertEquals(SqlDialect.H2, SqlDialect.fromUrl(null));
        assertEquals(SqlDialect.H2, SqlDialect.fromUrl(""));
        assertEquals(SqlDialect.H2, SqlDialect.fromUrl("   "));
    }

    @Test
    void shouldReturnH2ForUnknownUrl() {
        assertEquals(SqlDialect.H2, SqlDialect.fromUrl("jdbc:oracle:thin:@host:1521:db"));
    }

    @Test
    void shouldHandleCaseInsensitiveDetection() {
        assertEquals(SqlDialect.MSSQL, SqlDialect.fromUrl("JDBC:SQLSERVER://host:1433/db"));
        assertEquals(SqlDialect.POSTGRESQL, SqlDialect.fromUrl("JDBC:POSTGRESQL://host/db"));
        assertEquals(SqlDialect.MYSQL, SqlDialect.fromUrl("JDBC:MYSQL://host/db"));
    }
}
