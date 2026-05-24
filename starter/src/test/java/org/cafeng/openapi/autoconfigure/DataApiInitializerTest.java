package org.cafeng.openapi.autoconfigure;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.definition.ApiResponse;
import org.cafeng.openapi.definition.ApiSource;
import org.cafeng.openapi.openapi.OpenApiGenerator;
import org.cafeng.openapi.parser.YamlLint;
import org.cafeng.openapi.parser.YamlParser;
import org.cafeng.openapi.parser.YamlDiscovery;
import org.cafeng.openapi.registry.ApiDefinitionRegistry;
import org.cafeng.openapi.router.DynamicRouterRegistrar;
import org.cafeng.openapi.router.PathConflictDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataApiInitializerTest {

    @Mock DataApiProperties properties;
    @Mock DynamicRouterRegistrar routerRegistrar;
    @Mock OpenApiGenerator openApiGenerator;
    @Mock YamlDiscovery yamlDiscovery;
    @Mock YamlParser yamlParser;
    @Mock YamlLint yamlLint;
    @Mock PathConflictDetector pathConflictDetector;
    @Mock Resource resource;

    private DataApiInitializer initializer;
    private ApiDefinitionRegistry apiDefinitionRegistry;

    private ApiDefinition testApi;

    @BeforeEach
    void setUp() {
        apiDefinitionRegistry = new ApiDefinitionRegistry();
        initializer = new DataApiInitializer(
                properties, routerRegistrar, openApiGenerator,
                apiDefinitionRegistry, yamlDiscovery, yamlParser, yamlLint, pathConflictDetector);

        testApi = new ApiDefinition(
                "test-api", "Test", "/api/test", "GET",
                List.of(),
                new ApiSource("jdbc", "ds", "SELECT * FROM t"),
                new ApiResponse("list", List.of()),
                Map.of(), null);
    }

    @Test
    void shouldParseLintCheckConflictsAndRegister() throws Exception {
        // Discovery returns one YAML
        when(yamlDiscovery.discover()).thenReturn(List.of(resource));
        when(yamlParser.parse(resource)).thenReturn(testApi);
        when(yamlLint.lint(anyList())).thenReturn(Collections.emptyList());
        when(pathConflictDetector.detectConflicts(anySet())).thenReturn(Collections.emptySet());

        initializer.initialize();

        verify(yamlLint, times(1)).lint(anyList());
        verify(pathConflictDetector, times(1)).detectConflicts(anySet());
        verify(routerRegistrar, times(1)).registerApi(testApi);
        assertEquals(1, apiDefinitionRegistry.getAll().size());
    }

    @Test
    void shouldFailOnLintError() throws Exception {
        when(yamlDiscovery.discover()).thenReturn(List.of(resource));
        when(yamlParser.parse(resource)).thenReturn(testApi);
        when(yamlLint.lint(anyList())).thenReturn(List.of("Missing required field: id"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> initializer.initialize());
        // Exception is wrapped: "Data API Starter initialization failed" -> cause has lint message
        String msg = ex.getMessage() + (ex.getCause() != null ? " " + ex.getCause().getMessage() : "");
        assertTrue(msg.toLowerCase().contains("lint"),
                "Exception should mention lint failure, got: " + msg);
        verify(routerRegistrar, never()).registerApi(any());
    }

    @Test
    void shouldFailOnPathConflict() throws Exception {
        when(yamlDiscovery.discover()).thenReturn(List.of(resource));
        when(yamlParser.parse(resource)).thenReturn(testApi);
        when(yamlLint.lint(anyList())).thenReturn(Collections.emptyList());

        PathConflictDetector.ConflictInfo conflict = new PathConflictDetector.ConflictInfo(
                "/api/existing", "/api/test", "GET", "Conflicts with TestController.getTest()");
        when(pathConflictDetector.detectConflicts(anySet())).thenReturn(Set.of(conflict));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> initializer.initialize());
        String msg = ex.getMessage() + (ex.getCause() != null ? " " + ex.getCause().getMessage() : "");
        assertTrue(msg.toLowerCase().contains("conflict"),
                "Exception should mention conflict, got: " + msg);
        verify(routerRegistrar, never()).registerApi(any());
    }

    @Test
    void shouldRegisterMultipleApis() throws Exception {
        ApiDefinition api2 = new ApiDefinition(
                "api-2", "API 2", "/api/orders", "GET",
                List.of(),
                new ApiSource("jdbc", "ds", "SELECT * FROM orders"),
                new ApiResponse("list", List.of()),
                Map.of(), null);

        Resource r2 = mock(Resource.class);
        when(yamlDiscovery.discover()).thenReturn(List.of(resource, r2));
        when(yamlParser.parse(resource)).thenReturn(testApi);
        when(yamlParser.parse(r2)).thenReturn(api2);
        when(yamlLint.lint(anyList())).thenReturn(Collections.emptyList());
        when(pathConflictDetector.detectConflicts(anySet())).thenReturn(Collections.emptySet());

        initializer.initialize();

        verify(routerRegistrar, times(2)).registerApi(any());
        assertEquals(2, apiDefinitionRegistry.getAll().size());
    }
}
