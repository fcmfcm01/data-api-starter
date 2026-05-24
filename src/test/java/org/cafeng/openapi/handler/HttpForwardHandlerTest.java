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
class HttpForwardHandlerTest {

    @Mock private SqlInjectionGuard sqlInjectionGuard;
    @Mock private ScopeFilter scopeFilter;
    @Mock private ScopeResolver scopeResolver;
    @Mock private SlaMonitor slaMonitor;
    @Mock private QueryEngine httpEngine;
    @Mock private HttpServletRequest request;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpForwardHandler handler;

    private ApiDefinition httpApiDef;
    private ApiDefinition singleApiDef;

    @BeforeEach
    void setUp() {
        handler = new HttpForwardHandler(sqlInjectionGuard, objectMapper, scopeFilter, scopeResolver, slaMonitor);

        httpApiDef = buildHttpApiDefinition("list");
        singleApiDef = buildHttpApiDefinition("single");

        lenient().when(httpEngine.getType()).thenReturn("http");
    }

    private ApiDefinition buildHttpApiDefinition(String responseType) {
        return new ApiDefinition(
                "test-http-api", "Test HTTP API", "/v1/proxy", "GET",
                List.of(), new ApiSource("http", null, null, "http://upstream/api", null, null, 0),
                new ApiResponse(responseType, List.of()), Map.of(), new ApiSla(5000, null)
        );
    }

    private RequestContext buildContext(ApiDefinition def) {
        return new RequestContext(def, Map.of("q", "test"),
                AuthResult.authenticated("caller1", Set.of("basic")), System.currentTimeMillis());
    }

    @Test
    void shouldForwardAndReturn200WithData() throws Exception {
        List<Map<String, Object>> data = List.of(Map.of("id", 1, "name", "Alice"));
        when(httpEngine.execute(any(), isNull(), anyMap()))
                .thenReturn(new QueryResult(data, 1, 0));
        when(scopeResolver.resolveScopes("caller1")).thenReturn(Set.of("basic"));
        when(scopeFilter.apply(anyList(), anySet(), any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, QueryEngine> engines = Map.of("http", httpEngine);
        RequestContext ctx = buildContext(httpApiDef);

        ResponseEntity<String> response = handler.handle(ctx, request, engines);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Alice"));
        verify(slaMonitor).recordSuccess("test-http-api");
        verify(slaMonitor).recordLatency(eq("test-http-api"), anyLong());
    }

    @Test
    void shouldThrowWhenNoHttpEngineAvailable() throws Exception {
        Map<String, QueryEngine> engines = Map.of();
        RequestContext ctx = buildContext(httpApiDef);

        DataApiException ex = assertThrows(DataApiException.class, () -> handler.handle(ctx, request, engines));
        assertEquals("No HTTP query engine available", ex.getMessage());
        verify(slaMonitor).recordLatency(eq("test-http-api"), anyLong());
    }

    @Test
    void shouldRecordLatencyEvenOnException() throws Exception {
        when(httpEngine.execute(any(), isNull(), anyMap())).thenThrow(new RuntimeException("Upstream down"));

        Map<String, QueryEngine> engines = Map.of("http", httpEngine);
        RequestContext ctx = buildContext(httpApiDef);

        assertThrows(RuntimeException.class, () -> handler.handle(ctx, request, engines));
        verify(slaMonitor).recordLatency(eq("test-http-api"), anyLong());
        verify(slaMonitor, never()).recordSuccess("test-http-api");
    }

    @Test
    void shouldWrapEngineExceptionAsDataApiException() throws Exception {
        RuntimeException upstreamError = new RuntimeException("Connection refused");
        when(httpEngine.execute(any(), isNull(), anyMap())).thenThrow(upstreamError);

        Map<String, QueryEngine> engines = Map.of("http", httpEngine);
        RequestContext ctx = buildContext(httpApiDef);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> handler.handle(ctx, request, engines));
        assertEquals("Connection refused", thrown.getMessage());
    }

    @Test
    void shouldApplyScopeFiltering() throws Exception {
        List<Map<String, Object>> rawData = new ArrayList<>(List.of(
                Map.of("name", "Alice", "secret", "hidden")
        ));
        when(httpEngine.execute(any(), isNull(), anyMap()))
                .thenReturn(new QueryResult(rawData, 1, 0));
        when(scopeResolver.resolveScopes("caller1")).thenReturn(Set.of("basic"));
        when(scopeFilter.apply(anyList(), eq(Set.of("basic")), any()))
                .thenReturn(List.of(Map.of("name", "Alice")));

        Map<String, QueryEngine> engines = Map.of("http", httpEngine);
        RequestContext ctx = buildContext(httpApiDef);

        ResponseEntity<String> response = handler.handle(ctx, request, engines);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("Alice"));
        assertFalse(body.contains("hidden"));
        verify(scopeFilter).apply(anyList(), eq(Set.of("basic")), eq(httpApiDef));
    }

    @Test
    void shouldReturnSingleObjectWhenResponseTypeIsSingle() throws Exception {
        List<Map<String, Object>> data = List.of(Map.of("id", 1, "name", "Bob"));
        when(httpEngine.execute(any(), isNull(), anyMap()))
                .thenReturn(new QueryResult(data, 1, 0));
        when(scopeResolver.resolveScopes("caller1")).thenReturn(Set.of("basic"));
        when(scopeFilter.apply(anyList(), anySet(), any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, QueryEngine> engines = Map.of("http", httpEngine);
        RequestContext ctx = buildContext(singleApiDef);

        ResponseEntity<String> response = handler.handle(ctx, request, engines);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertFalse(body.startsWith("["));
        assertTrue(body.startsWith("{"));
        assertTrue(body.contains("Bob"));
    }

    @Test
    void shouldValidateSqlInjectionGuardIsCalled() throws Exception {
        when(httpEngine.execute(any(), isNull(), anyMap()))
                .thenReturn(new QueryResult(List.of(), 0, 0));
        when(scopeResolver.resolveScopes("caller1")).thenReturn(Set.of("basic"));
        when(scopeFilter.apply(anyList(), anySet(), any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, QueryEngine> engines = Map.of("http", httpEngine);
        RequestContext ctx = buildContext(httpApiDef);

        handler.handle(ctx, request, engines);

        verify(sqlInjectionGuard).validate(ctx.sqlParams());
    }
}
