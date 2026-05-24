package org.cafeng.openapi.capability;

import org.cafeng.openapi.definition.*;
import org.cafeng.openapi.registry.ApiDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityEndpointTest {

    private ApiDefinitionRegistry registry;
    private CapabilityEndpoint endpoint;

    @BeforeEach
    void setUp() {
        registry = new ApiDefinitionRegistry();
        endpoint = new CapabilityEndpoint(registry);
    }

    private ApiDefinition createApi(String id, String path, String method, List<ResponseField> fields) {
        return new ApiDefinition(
                id, "Test " + id, path, method, null,
                new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", fields),
                Map.of("read", "basic"), new ApiSla(3000, 100)
        );
    }

    @Test
    void shouldReturnEmptyListWhenNoApis() {
        var caps = endpoint.getCapabilities();
        assertNotNull(caps);
        assertTrue(caps.isEmpty());
    }

    @Test
    void shouldReturnCapabilityForSingleApi() {
        registry.register(createApi("list-orders", "/v1/orders", "GET",
                List.of(new ResponseField("orderNo", "basic", false, null))));

        var caps = endpoint.getCapabilities();
        assertEquals(1, caps.size());

        var cap = caps.get(0);
        assertEquals("list-orders", cap.get("id"));
        assertEquals("Test list-orders", cap.get("name"));
        assertEquals("/v1/orders", cap.get("path"));
        assertEquals("GET", cap.get("method"));
    }

    @Test
    void shouldIncludeFieldsWithScope() {
        registry.register(createApi("test-api", "/api/test", "GET", List.of(
                new ResponseField("id", "basic", false, null),
                new ResponseField("email", "detail", false, null),
                new ResponseField("phone", "sensitive", true, "PII phone number")
        )));

        var caps = endpoint.getCapabilities();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> fields = (List<Map<String, String>>) caps.get(0).get("fields");

        assertEquals(3, fields.size());
        assertEquals("id", fields.get(0).get("name"));
        assertEquals("basic", fields.get(0).get("scope"));

        assertEquals("email", fields.get(1).get("name"));
        assertEquals("detail", fields.get(1).get("scope"));

        // PII field should have pii marker
        assertEquals("phone", fields.get(2).get("name"));
        assertEquals("true", fields.get(2).get("pii"));
    }

    @Test
    void shouldIncludeScopes() {
        registry.register(createApi("test-api", "/api/test", "GET",
                List.of(new ResponseField("id", "basic", false, null))));

        var caps = endpoint.getCapabilities();
        @SuppressWarnings("unchecked")
        Map<String, String> scopes = (Map<String, String>) caps.get(0).get("scopes");

        assertNotNull(scopes);
        assertEquals("basic", scopes.get("read"));
    }

    @Test
    void shouldHandleApiWithNullFields() {
        var api = new ApiDefinition(
                "no-fields", "No Fields", "/api/nf", "GET", null,
                new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", null),
                Map.of(), null
        );
        registry.register(api);

        var caps = endpoint.getCapabilities();
        assertEquals(1, caps.size());

        @SuppressWarnings("unchecked")
        List<Map<String, String>> fields = (List<Map<String, String>>) caps.get(0).get("fields");
        assertTrue(fields.isEmpty());
    }

    @Test
    void shouldReturnMultipleCapabilities() {
        registry.register(createApi("api-1", "/a", "GET", List.of(new ResponseField("id", "basic", false, null))));
        registry.register(createApi("api-2", "/b", "POST", List.of(new ResponseField("id", "basic", false, null))));
        registry.register(createApi("api-3", "/c", "GET", List.of(new ResponseField("id", "basic", false, null))));

        var caps = endpoint.getCapabilities();
        assertEquals(3, caps.size());
    }

    @Test
    void shouldHandleApiWithEmptyScopes() {
        var api = new ApiDefinition(
                "no-scopes", "No Scopes", "/api/ns", "GET", null,
                new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", List.of(new ResponseField("id", "basic", false, null))),
                Map.of(), null
        );
        registry.register(api);

        var caps = endpoint.getCapabilities();
        @SuppressWarnings("unchecked")
        Map<String, String> scopes = (Map<String, String>) caps.get(0).get("scopes");
        assertNotNull(scopes);
        assertTrue(scopes.isEmpty());
    }
}
