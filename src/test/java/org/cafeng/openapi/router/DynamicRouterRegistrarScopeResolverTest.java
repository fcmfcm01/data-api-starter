package org.cafeng.openapi.router;

import org.cafeng.openapi.definition.*;
import org.cafeng.openapi.engine.*;
import org.cafeng.openapi.param.RequestParameterMapper;
import org.cafeng.openapi.scope.ConfigScopeResolver;
import org.cafeng.openapi.scope.ScopeFilter;
import org.cafeng.openapi.scope.ScopeResolver;
import org.cafeng.openapi.sla.SlaMonitor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD RED-phase tests for F-BUG-01: DynamicRouterRegistrar should inject
 * and call ScopeResolver instead of hardcoding Set.of().
 *
 * These tests are expected to FAIL at compile time because:
 *   - DynamicRouterRegistrar constructor does not yet accept ScopeResolver
 * Once the constructor is updated, tests will still fail because:
 *   - handle() does not yet call scopeResolver.resolveScopes()
 *   - handle() still passes Set.of() to scopeFilter.apply()
 */
@ExtendWith(MockitoExtension.class)
class DynamicRouterRegistrarScopeResolverTest {

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    @Mock
    private QueryEngine queryEngine;

    @Mock
    private ConditionBuilder conditionBuilder;

    @Mock
    private PaginationBuilder paginationBuilder;

    @Mock
    private PageResponseBuilder pageResponseBuilder;

    @Mock
    private RequestParameterMapper parameterMapper;

    @Mock
    private ScopeFilter scopeFilter;

    @Mock
    private ScopeResolver scopeResolver;

    @Mock
    private SlaMonitor slaMonitor;

    @Mock
    private HttpServletRequest request;

    @Captor
    private ArgumentCaptor<Set<String>> scopesCaptor;

    private DynamicRouterRegistrar registrar;

    private ApiDefinition testApiDefinition;

    @BeforeEach
    void setUp() throws Exception {
        when(queryEngine.getType()).thenReturn("jdbc");
        registrar = new DynamicRouterRegistrar(
                handlerMapping,
                List.of(queryEngine),
                conditionBuilder,
                paginationBuilder,
                pageResponseBuilder,
                parameterMapper,
                scopeFilter,
                scopeResolver,
                slaMonitor,
                new SqlInjectionGuard(),
                new DdlGuard(scopeResolver),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        testApiDefinition = new ApiDefinition(
                "test-api",
                "Test API",
                "/api/test",
                "GET",
                List.of(),
                new ApiSource("jdbc", "testdb", "SELECT * FROM test_table"),
                new ApiResponse("list", List.of(
                        new ResponseField("id", "basic", false, "ID"),
                        new ResponseField("name", "basic", false, "Name"),
                        new ResponseField("salary", "sensitive", true, "Salary")
                )),
                Map.of(),
                new ApiSla(5000, 100)
        );
    }

    /**
     * Helper: register an API and invoke the handler's handle() method via reflection.
     * registerApi() creates a DynamicApiHandler and registers it with handlerMapping.
     * We capture that handler and invoke its handle() method directly.
     */
    private ResponseEntity<String> invokeHandle(HttpServletRequest request) throws Exception {
        // Capture the handler object that registerApi() passes to handlerMapping
        ArgumentCaptor<Object> handlerCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Method> methodCaptor = ArgumentCaptor.forClass(Method.class);

        registrar.registerApi(testApiDefinition);

        // Verify registerMapping was called and capture the handler
        verify(handlerMapping).registerMapping(
                any(),
                handlerCaptor.capture(),
                methodCaptor.capture()
        );

        // The registered method is "handle" on the DynamicApiHandler
        Method handleMethod = methodCaptor.getValue();
        Object handler = handlerCaptor.getValue();

        // Invoke handle(HttpServletRequest) via reflection
        handleMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        ResponseEntity<String> response = (ResponseEntity<String>) handleMethod.invoke(handler, request);
        return response;
    }

    private void setupReadQueryMocks() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(parameterMapper.mapParameters(eq(testApiDefinition), eq(request), anyMap()))
                .thenReturn(Map.of());

        ConditionBuilder.ConditionResult conditionResult = new ConditionBuilder.ConditionResult(
                "SELECT * FROM test_table", Map.of());
        when(conditionBuilder.build(anyString(), anyMap())).thenReturn(conditionResult);

        List<Map<String, Object>> queryResults = List.of(
                Map.of("id", 1, "name", "Alice", "salary", 50000)
        );
        when(queryEngine.execute(eq(testApiDefinition), anyString(), anyMap()))
                .thenReturn(new QueryResult(queryResults, 1, 0));
    }

    // ---------------------------------------------------------------
    // Test 1: scopeResolver.resolveScopes(callerId) is called exactly once
    // ---------------------------------------------------------------
    @Test
    void handle_shouldCallScopeResolverResolveScopes_once() throws Exception {
        // Arrange
        String callerId = "caller-123";
        Set<String> expectedScopes = Set.of("basic", "detail", "sensitive");

        setupReadQueryMocks();
        when(request.getHeader("X-Caller-Id")).thenReturn(callerId);
        when(scopeResolver.resolveScopes(callerId)).thenReturn(expectedScopes);
        when(scopeFilter.apply(anyList(), anySet(), eq(testApiDefinition)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        ResponseEntity<String> response = invokeHandle(request);

        // Assert
        assertNotNull(response);
        verify(scopeResolver, times(1)).resolveScopes(callerId);
    }

    // ---------------------------------------------------------------
    // Test 2: scopeFilter.apply() receives the resolved scopes, not Set.of()
    // ---------------------------------------------------------------
    @Test
    void handle_shouldPassResolvedScopesToScopeFilter_notSetOfEmpty() throws Exception {
        // Arrange
        String callerId = "caller-456";
        Set<String> resolvedScopes = Set.of("basic", "detail");

        setupReadQueryMocks();
        when(request.getHeader("X-Caller-Id")).thenReturn(callerId);
        when(scopeResolver.resolveScopes(callerId)).thenReturn(resolvedScopes);
        when(scopeFilter.apply(anyList(), anySet(), eq(testApiDefinition)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        ResponseEntity<String> response = invokeHandle(request);

        // Assert: scopeFilter.apply() should receive scopes from scopeResolver
        verify(scopeFilter).apply(anyList(), scopesCaptor.capture(), eq(testApiDefinition));

        Set<String> capturedScopes = scopesCaptor.getValue();
        assertEquals(resolvedScopes, capturedScopes,
                "scopeFilter.apply() should receive scopes from scopeResolver.resolveScopes(), not Set.of()");

        // Extra: make sure it's NOT the empty set that the current bug passes
        assertFalse(capturedScopes.isEmpty(),
                "Scopes passed to scopeFilter should NOT be empty when resolver returns non-empty scopes");
    }

    // ---------------------------------------------------------------
    // Test 3: X-Caller-Id header value is forwarded to scopeResolver
    // ---------------------------------------------------------------
    @Test
    void handle_shouldForwardXCallerIdHeaderToScopeResolver() throws Exception {
        // Arrange
        String callerId = "financial-user";
        Set<String> financialScopes = Set.of("basic", "detail", "financial");

        setupReadQueryMocks();
        when(request.getHeader("X-Caller-Id")).thenReturn(callerId);
        when(scopeResolver.resolveScopes(callerId)).thenReturn(financialScopes);
        when(scopeFilter.apply(anyList(), anySet(), eq(testApiDefinition)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        ResponseEntity<String> response = invokeHandle(request);

        // Assert: the exact callerId from X-Caller-Id header is passed to resolveScopes
        ArgumentCaptor<String> callerIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(scopeResolver).resolveScopes(callerIdCaptor.capture());

        assertEquals(callerId, callerIdCaptor.getValue(),
                "The value from X-Caller-Id header should be forwarded to scopeResolver.resolveScopes()");
    }
}
