package org.cafeng.openapi.integration;

import org.cafeng.openapi.datasource.DataSourceRegistry;
import org.cafeng.openapi.definition.*;
import org.cafeng.openapi.engine.*;
import org.cafeng.openapi.openapi.OpenApiGenerator;
import org.cafeng.openapi.param.RequestParameterMapper;
import org.cafeng.openapi.router.DynamicRouterRegistrar;
import org.cafeng.openapi.scope.ScopeFilter;
import org.cafeng.openapi.scope.ConfigScopeResolver;
import org.cafeng.openapi.scope.ScopeResolver;
import org.cafeng.openapi.sla.SlaMonitor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SpringContextIntegrationTest.Config.class)
@AutoConfigureMockMvc
class SpringContextIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.cafeng.openapi.autoconfigure.DataApiAutoConfiguration.class
    })
    static class Config {

        private static final String H2_URL = "jdbc:h2:mem:integctx;DB_CLOSE_DELAY=-1";

        @Bean
        DataSource dataSource() throws SQLException {
            DataSource ds = createH2();
            initSchema(ds);
            return ds;
        }

        private DataSource createH2() {
            return new javax.sql.DataSource() {
                @Override public Connection getConnection() throws SQLException {
                    return java.sql.DriverManager.getConnection(H2_URL, "sa", "");
                }
                @Override public Connection getConnection(String u, String p) throws SQLException {
                    return java.sql.DriverManager.getConnection(H2_URL, u, p);
                }
                @Override public java.io.PrintWriter getLogWriter() { return null; }
                @Override public void setLogWriter(java.io.PrintWriter out) {}
                @Override public void setLoginTimeout(int seconds) {}
                @Override public int getLoginTimeout() { return 0; }
                @Override public java.util.logging.Logger getParentLogger() { return null; }
                @Override public <T> T unwrap(Class<T> iface) { throw new RuntimeException(); }
                @Override public boolean isWrapperFor(Class<?> iface) { return false; }
            };
        }

        private void initSchema(DataSource ds) throws SQLException {
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS items (id INT PRIMARY KEY, name VARCHAR(100), qty INT)");
                stmt.execute("DELETE FROM items");
                stmt.execute("INSERT INTO items VALUES (1, 'Widget', 10)");
                stmt.execute("INSERT INTO items VALUES (2, 'Gadget', 25)");
                stmt.execute("INSERT INTO items VALUES (3, 'Doohickey', 5)");
            }
        }

        @Bean
        DataSourceRegistry dataSourceRegistry(DataSource dataSource) {
            DataSourceRegistry reg = new DataSourceRegistry();
            reg.registerDataSource("dataSource", dataSource);
            return reg;
        }

        @Bean JdbcQueryEngine jdbcQueryEngine(DataSourceRegistry r) { return new JdbcQueryEngine(r); }
        @Bean ConditionBuilder conditionBuilder() { return new ConditionBuilder(); }
        @Bean PaginationBuilder paginationBuilder() { return new PaginationBuilder(); }
        @Bean PageResponseBuilder pageResponseBuilder() { return new PageResponseBuilder(); }
        @Bean RequestParameterMapper requestParameterMapper() { return new RequestParameterMapper(new com.fasterxml.jackson.databind.ObjectMapper()); }
        @Bean ScopeFilter scopeFilter() { return new ScopeFilter(); }
        @Bean ScopeResolver scopeResolver() { return new ConfigScopeResolver("internal:basic"); }
        @Bean OpenApiGenerator openApiGenerator() { return new OpenApiGenerator(); }
        @Bean SlaMonitor slaMonitor() { return new SlaMonitor(new SimpleMeterRegistry()); }
        @Bean DdlGuard ddlGuard(ScopeResolver scopeResolver) { return new DdlGuard(scopeResolver); }

        @Bean
        DynamicRouterRegistrar dynamicRouterRegistrar(
                @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
                JdbcQueryEngine queryEngine,
                OpenApiGenerator openApiGenerator) throws Exception {

            DynamicRouterRegistrar registrar = new DynamicRouterRegistrar(
                    handlerMapping, List.of(queryEngine), conditionBuilder(),
                    paginationBuilder(), pageResponseBuilder(), requestParameterMapper(),
                    scopeFilter(), scopeResolver(), slaMonitor(), new SqlInjectionGuard(),
                    ddlGuard(scopeResolver()), new com.fasterxml.jackson.databind.ObjectMapper());

            registrar.registerApi(new ApiDefinition(
                    "list-items", "List Items", "/v1/items", "GET", null,
                    new ApiSource("jdbc", "dataSource", "SELECT * FROM items"),
                    new ApiResponse("list", List.of(
                            new ResponseField("id", "basic", false, null),
                            new ResponseField("name", "basic", false, null)
                    )),
                    Map.of(), null
            ));

            registrar.registerApi(new ApiDefinition(
                    "create-item", "Create Item", "/v1/items", "POST",
                    List.of(
                            new ApiParameter("name", "body", "string", true, null, null, null, null, null, null),
                            new ApiParameter("qty", "body", "integer", true, null, null, null, null, null, null)
                    ),
                    // H2 doesn't support :namedParams, but isWriteOperation detects INSERT prefix
                    // In production with MSSQL, the actual :paramName SQL would be used
                    // Use auto-increment compatible INSERT (no fixed id)
                    new ApiSource("jdbc", "dataSource", "INSERT INTO items (id, name, qty) SELECT MAX(id)+1, 'PostedItem', 77 FROM items"),
                    new ApiResponse("single", null),
                    Map.of(), null
            ));

            openApiGenerator.registerApi(new ApiDefinition(
                    "list-items", "List Items", "/v1/items", "GET", null,
                    new ApiSource("jdbc", "dataSource", "SELECT 1"),
                    new ApiResponse("list", null), Map.of(), null));
            openApiGenerator.registerApi(new ApiDefinition(
                    "create-item", "Create Item", "/v1/items", "POST", null,
                    new ApiSource("jdbc", "dataSource", "SELECT 1"),
                    new ApiResponse("single", null), Map.of(), null));

            return registrar;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListAllItems() throws Exception {
        mockMvc.perform(get("/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    void shouldReturnItemFields() throws Exception {
        mockMvc.perform(get("/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void shouldCreateItemViaPost() throws Exception {
        mockMvc.perform(post("/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NewThing\",\"qty\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedRows").value(1));
    }

    @Test
    void shouldReturn404ForUnknownRoute() throws Exception {
        mockMvc.perform(get("/v1/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSupportGetAndPostOnSamePath() throws Exception {
        mockMvc.perform(get("/v1/items"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TestItem\",\"qty\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedRows").value(1));
    }
}
