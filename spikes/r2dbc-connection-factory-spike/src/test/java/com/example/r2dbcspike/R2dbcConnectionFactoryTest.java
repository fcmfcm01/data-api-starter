package com.example.r2dbcspike;

import io.r2dbc.spi.ConnectionFactory;
import org.cafeng.openapi.autoconfigure.DataApiAutoConfiguration;
import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.definition.ApiResponse;
import org.cafeng.openapi.definition.ApiSource;
import org.cafeng.openapi.engine.R2dbcQueryEngine;
import org.cafeng.openapi.engine.SqlDialect;
import org.cafeng.openapi.r2dbc.ConnectionFactoryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {DataApiAutoConfiguration.class})
class R2dbcConnectionFactoryTest {

    @Autowired(required = false)
    private ConnectionFactory connectionFactory;

    @Test
    void connectionFactoryIsAutoConfigured() {
        assertNotNull(connectionFactory, "ConnectionFactory should be auto-configured from spring.r2dbc.*");
    }

    @Test
    void sqlDialectDetectsH2FromR2dbcUrl() {
        SqlDialect dialect = SqlDialect.fromUrl("r2dbc:h2:mem:///testdb");
        assertEquals(SqlDialect.H2, dialect);
    }

    @Test
    void sqlDialectDetectsMssqlFromH2Mode() {
        SqlDialect dialect = SqlDialect.fromUrl("r2dbc:h2:mem:///testdb;MODE=MSSQLServer");
        assertEquals(SqlDialect.MSSQL, dialect);
    }

    @Test
    void sqlDialectDetectsMysqlFromR2dbcUrl() {
        SqlDialect dialect = SqlDialect.fromUrl("r2dbc:mysql://localhost:3306/mydb");
        assertEquals(SqlDialect.MYSQL, dialect);
    }

    @Test
    void r2dbcQueryEngineCanExecuteSimpleQuery() {
        assertNotNull(connectionFactory);

        // Register the auto-configured ConnectionFactory into the registry
        ConnectionFactoryRegistry registry = new ConnectionFactoryRegistry();
        registry.register("default", connectionFactory);

        R2dbcQueryEngine engine = new R2dbcQueryEngine(registry);

        // Build a minimal ApiDefinition for the query
        ApiSource source = new ApiSource("r2dbc", "default", "SELECT 1 AS val");
        ApiResponse response = new ApiResponse("list", List.of());
        ApiDefinition apiDef = new ApiDefinition(
            "test-query", "Test Query", "/test", "GET",
            List.of(), source, response, Map.of(), null
        );

        List<Map<String, Object>> results = engine.executeQuery(apiDef, "SELECT 1 AS val", Map.of());
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals(1, results.get(0).get("val"));
    }
}
