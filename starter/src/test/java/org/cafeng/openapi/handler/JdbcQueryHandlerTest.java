package org.cafeng.openapi.handler;

import org.cafeng.openapi.definition.*;
import org.cafeng.openapi.engine.*;
import org.cafeng.openapi.error.DataApiException;
import org.cafeng.openapi.scope.ScopeFilter;
import org.cafeng.openapi.scope.ScopeResolver;
import org.cafeng.openapi.sla.SlaMonitor;
import org.cafeng.openapi.security.AuthResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JdbcQueryHandlerTest {

    @Mock private ConditionBuilder conditionBuilder;
    @Mock private PaginationBuilder paginationBuilder;
    @Mock private PageResponseBuilder pageResponseBuilder;
    @Mock private SqlInjectionGuard sqlInjectionGuard;
    @Mock private DdlGuard ddlGuard;
    @Mock private ScopeFilter scopeFilter;
    @Mock private ScopeResolver scopeResolver;
    @Mock private SlaMonitor slaMonitor;
    @Mock private QueryEngine queryEngine;
    @Mock private HttpServletRequest request;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcQueryHandler handler;

    private ApiDefinition listApiDef;
    private ApiDefinition singleApiDef;
    private ApiDefinition pageApiDef;

    @BeforeEach
    void setUp() {
        handler = new JdbcQueryHandler(conditionBuilder, paginationBuilder, pageResponseBuilder,
                sqlInjectionGuard, ddlGuard, objectMapper, scopeFilter, scopeResolver, slaMonitor);

        listApiDef = buildApiDefinition("list");
        singleApiDef = buildApiDefinition("single");
        pageApiDef = buildApiDefinition("page");
    }

    private ApiDefinition buildApiDefinition(String responseType) {
        return new ApiDefinition(
                "test-api", "Test API", "/v1/test", "GET",
                List.of(), new ApiSource("jdbc", "dataSource", "SELECT * FROM test"), new ApiResponse(responseType, List.of()),
                Map.of(), new ApiSla(5000, null)
        );
    }

    private RequestContext buildContext(ApiDefinition def) {
        return new RequestContext(def, Map.of(),
                AuthResult.authenticated("caller1", Set.of("basic")), System.currentTimeMillis());
    }

    @Test
    void shouldExecuteReadQuery() throws Exception {
        ConditionBuilder.ConditionResult cr = new ConditionBuilder.ConditionResult("SELECT * FROM test", Map.of());
        when(conditionBuilder.build(anyString(), anyMap())).thenReturn(cr);
        when(queryEngine.execute(any(), anyString(), anyMap()))
                .thenReturn(new QueryResult(List.of(Map.of("id", 1, "name", "foo")), 1, 0));
        when(scopeResolver.resolveScopes("caller1")).thenReturn(Set.of("basic"));
        when(scopeFilter.apply(anyList(), anySet(), any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, QueryEngine> engines = Map.of("jdbc", queryEngine);
        RequestContext ctx = buildContext(listApiDef);

        ResponseEntity<String> response = handler.handle(ctx, request, engines);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("foo"));
        verify(slaMonitor).recordSuccess("test-api");
        verify(slaMonitor).recordLatency(eq("test-api"), anyLong());
    }

    @Test
    void shouldExecuteWriteQuery() throws Exception {
        ApiDefinition writeApi = new ApiDefinition("write-api", "Write", "/v1/test", "POST",
                List.of(), new ApiSource("jdbc", "dataSource", "INSERT INTO test VALUES(:id)"), new ApiResponse("list", List.of()),
                Map.of(), new ApiSla(5000, null));
        ConditionBuilder.ConditionResult cr = new ConditionBuilder.ConditionResult("INSERT INTO test VALUES(:id)", Map.of("id", 42));
        when(conditionBuilder.build(anyString(), anyMap())).thenReturn(cr);
        when(queryEngine.execute(any(), anyString(), anyMap()))
                .thenReturn(new QueryResult(List.of(), 0, 3));

        Map<String, QueryEngine> engines = Map.of("jdbc", queryEngine);
        RequestContext ctx = buildContext(writeApi);

        ResponseEntity<String> response = handler.handle(ctx, request, engines);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("affectedRows"));
        assertTrue(response.getBody().contains("3"));
    }

    @Test
    void shouldPaginateResults() throws Exception {
        ConditionBuilder.ConditionResult cr = new ConditionBuilder.ConditionResult("SELECT * FROM test", Map.of());
        when(conditionBuilder.build(anyString(), anyMap())).thenReturn(cr);
        List<Map<String, Object>> pageData = List.of(Map.of("id", 1));
        when(queryEngine.execute(any(), anyString(), anyMap()))
                .thenReturn(new QueryResult(pageData, 1, 0));
        when(queryEngine.executeCount(any(), anyString(), anyMap())).thenReturn(100L);
        when(queryEngine.getDialect(anyString())).thenReturn(SqlDialect.MSSQL);
        when(paginationBuilder.build(anyString(), anyInt(), anyInt(), any(SqlDialect.class))).thenReturn("SELECT * FROM test OFFSET 0 FETCH NEXT 20");
        when(pageResponseBuilder.build(anyList(), anyLong(), anyInt(), anyInt()))
                .thenReturn(Map.of("data", pageData, "total", 100, "page", 1, "size", 20));

        Map<String, QueryEngine> engines = Map.of("jdbc", queryEngine);
        RequestContext ctx = buildContext(pageApiDef);

        ResponseEntity<String> response = handler.handle(ctx, request, engines);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("total"));
        assertTrue(body.contains("100"));
    }

    @Test
    void shouldApplyScopeFiltering() throws Exception {
        ConditionBuilder.ConditionResult cr = new ConditionBuilder.ConditionResult("SELECT * FROM test", Map.of());
        when(conditionBuilder.build(anyString(), anyMap())).thenReturn(cr);
        List<Map<String, Object>> rawData = List.of(Map.of("name", "Alice", "secret", "hidden"));
        when(queryEngine.execute(any(), anyString(), anyMap()))
                .thenReturn(new QueryResult(rawData, 1, 0));
        when(scopeResolver.resolveScopes("caller1")).thenReturn(Set.of("basic"));
        when(scopeFilter.apply(anyList(), eq(Set.of("basic")), any()))
                .thenReturn(List.of(Map.of("name", "Alice")));

        Map<String, QueryEngine> engines = Map.of("jdbc", queryEngine);
        RequestContext ctx = buildContext(listApiDef);

        ResponseEntity<String> response = handler.handle(ctx, request, engines);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("Alice"));
        assertFalse(body.contains("hidden"));
        verify(scopeFilter).apply(anyList(), eq(Set.of("basic")), eq(listApiDef));
    }

    @Test
    void shouldReturnNotFoundForEmptySingleResult() throws Exception {
        ConditionBuilder.ConditionResult cr = new ConditionBuilder.ConditionResult("SELECT * FROM test WHERE id=:id", Map.of());
        when(conditionBuilder.build(anyString(), anyMap())).thenReturn(cr);
        when(queryEngine.execute(any(), anyString(), anyMap()))
                .thenReturn(new QueryResult(List.of(), 0, 0));

        Map<String, QueryEngine> engines = Map.of("jdbc", queryEngine);
        RequestContext ctx = buildContext(singleApiDef);

        ResponseEntity<String> response = handler.handle(ctx, request, engines);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("Not found"));
    }

    @Test
    void shouldThrowWhenNoJdbcEngine() throws Exception {
        ConditionBuilder.ConditionResult cr = new ConditionBuilder.ConditionResult("SELECT 1", Map.of());
        when(conditionBuilder.build(anyString(), anyMap())).thenReturn(cr);

        Map<String, QueryEngine> engines = Map.of();
        RequestContext ctx = buildContext(listApiDef);

        DataApiException ex = assertThrows(DataApiException.class, () -> handler.handle(ctx, request, engines));
        assertEquals("No JDBC query engine available", ex.getMessage());
        verify(slaMonitor).recordLatency(eq("test-api"), anyLong());
    }

    @Test
    void shouldRecordLatencyEvenOnError() throws Exception {
        ConditionBuilder.ConditionResult cr = new ConditionBuilder.ConditionResult("SELECT 1", Map.of());
        when(conditionBuilder.build(anyString(), anyMap())).thenReturn(cr);
        when(queryEngine.execute(any(), anyString(), anyMap())).thenThrow(new RuntimeException("DB down"));

        Map<String, QueryEngine> engines = Map.of("jdbc", queryEngine);
        RequestContext ctx = buildContext(listApiDef);

        assertThrows(RuntimeException.class, () -> handler.handle(ctx, request, engines));
        verify(slaMonitor).recordLatency(eq("test-api"), anyLong());
        verify(slaMonitor, never()).recordSuccess("test-api");
    }
}
