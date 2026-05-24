package org.cafeng.openapi.router;

import org.cafeng.openapi.definition.*;
import org.cafeng.openapi.engine.*;
import org.cafeng.openapi.param.RequestParameterMapper;
import org.cafeng.openapi.scope.ConfigScopeResolver;
import org.cafeng.openapi.scope.ScopeFilter;
import org.cafeng.openapi.scope.ScopeResolver;
import org.cafeng.openapi.sla.SlaMonitor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicRouterRegistrarSlaTest {

    @Mock RequestMappingHandlerMapping handlerMapping;
    @Mock QueryEngine queryEngine;
    @Mock ConditionBuilder conditionBuilder;
    @Mock PaginationBuilder paginationBuilder;
    @Mock PageResponseBuilder pageResponseBuilder;
    @Mock RequestParameterMapper parameterMapper;
    @Mock ScopeFilter scopeFilter;
    @Mock ScopeResolver scopeResolver;
    @Mock SlaMonitor slaMonitor;
    @Mock HttpServletRequest request;

    private DynamicRouterRegistrar registrar;
    private ApiDefinition testApi;

    @BeforeEach
    void setUp() throws Exception {
        when(queryEngine.getType()).thenReturn("jdbc");
        registrar = new DynamicRouterRegistrar(
            handlerMapping, List.of(queryEngine), conditionBuilder,
            paginationBuilder, pageResponseBuilder, parameterMapper,
            scopeFilter, scopeResolver, slaMonitor, new SqlInjectionGuard(),
            new DdlGuard(new ConfigScopeResolver("")),
            new com.fasterxml.jackson.databind.ObjectMapper()
        );

        testApi = new ApiDefinition(
            "test-api", "Test", "/api/test", "GET",
            List.of(),
            new ApiSource("jdbc", "testdb", "SELECT * FROM test_table"),
            new ApiResponse("list", List.of(
                new ResponseField("id", "basic", false, "ID")
            )),
            Map.of(), new ApiSla(5000, 100)
        );
    }

    // Helper: invoke handle via reflection
    private ResponseEntity<String> invokeHandle(HttpServletRequest req) throws Exception {
        registrar.registerApi(testApi);
        ArgumentCaptor<Object> handlerCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Method> methodCaptor = ArgumentCaptor.forClass(Method.class);
        verify(handlerMapping).registerMapping(any(), handlerCaptor.capture(), methodCaptor.capture());
        Method handleMethod = methodCaptor.getValue();
        handleMethod.setAccessible(true);
        return (ResponseEntity<String>) handleMethod.invoke(handlerCaptor.getValue(), req);
    }

    private void setupReadQueryMocks() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(parameterMapper.mapParameters(eq(testApi), eq(request), anyMap())).thenReturn(Map.of());
        when(conditionBuilder.build(anyString(), anyMap()))
            .thenReturn(new ConditionBuilder.ConditionResult("SELECT * FROM test_table", Map.of()));
        when(queryEngine.execute(eq(testApi), anyString(), anyMap()))
            .thenReturn(new QueryResult(List.of(Map.of("id", 1)), 1, 0));
        when(scopeResolver.resolveScopes(any())).thenReturn(Set.of("basic"));
        when(scopeFilter.apply(anyList(), anySet(), eq(testApi))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void handle_shouldRecordQueryCount() throws Exception {
        setupReadQueryMocks();
        invokeHandle(request);
        verify(slaMonitor, times(1)).recordQuery("test-api");
    }

    @Test
    void handle_shouldRecordLatencyOnSuccess() throws Exception {
        setupReadQueryMocks();
        invokeHandle(request);
        verify(slaMonitor, times(1)).recordLatency(eq("test-api"), anyLong());
        verify(slaMonitor, times(1)).recordSuccess("test-api");
    }

    @Test
    void handle_shouldRecordErrorOnException() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(parameterMapper.mapParameters(eq(testApi), eq(request), anyMap()))
            .thenThrow(new RuntimeException("DB error"));
        invokeHandle(request);
        verify(slaMonitor, times(1)).recordError(eq("test-api"), anyString());
        verify(slaMonitor, never()).recordSuccess("test-api");
    }
}
