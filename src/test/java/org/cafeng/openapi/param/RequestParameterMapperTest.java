package org.cafeng.openapi.param;

import org.cafeng.openapi.definition.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RequestParameterMapperTest {

    private final RequestParameterMapper mapper = new RequestParameterMapper(new com.fasterxml.jackson.databind.ObjectMapper());

    private ApiDefinition createTestApi(List<ApiParameter> parameters) {
        return new ApiDefinition(
                "test-api", "Test", "/api/test", "GET",
                parameters,
                new ApiSource("jdbc", "dataSource", "SELECT 1"),
                new ApiResponse("list", null),
                Map.of(), null
        );
    }

    @Test
    void shouldExtractQueryParams() {
        var api = createTestApi(List.of(
                new ApiParameter("status", "query", "string", false, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "ACTIVE");
        var result = mapper.mapParameters(api, request, Map.of());

        assertEquals("ACTIVE", result.get("status"));
    }

    @Test
    void shouldSkipEmptyParams() {
        var api = createTestApi(List.of(
                new ApiParameter("status", "query", "string", false, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "");
        var result = mapper.mapParameters(api, request, Map.of());

        assertFalse(result.containsKey("status"));
    }

    @Test
    void shouldConvertIntegerParam() {
        var api = createTestApi(List.of(
                new ApiParameter("page", "query", "integer", false, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("page", "3");
        var result = mapper.mapParameters(api, request, Map.of());

        assertEquals(3, result.get("page"));
        assertInstanceOf(Integer.class, result.get("page"));
    }

    @Test
    void shouldConvertBooleanParam() {
        var api = createTestApi(List.of(
                new ApiParameter("active", "query", "boolean", false, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("active", "true");
        var result = mapper.mapParameters(api, request, Map.of());

        assertEquals(true, result.get("active"));
    }

    @Test
    void shouldExtractBodyParams() {
        var api = createTestApi(List.of(
                new ApiParameter("name", "body", "string", true, null, null, null, null, null, null),
                new ApiParameter("status", "body", "string", true, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        var bodyParams = Map.<String, Object>of("name", "TestOrder", "status", "ACTIVE");
        var result = mapper.mapParameters(api, request, bodyParams);

        assertEquals("TestOrder", result.get("name"));
        assertEquals("ACTIVE", result.get("status"));
    }

    @Test
    void shouldHandleNullParameters() {
        var api = createTestApi(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "ACTIVE");
        var result = mapper.mapParameters(api, request, Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRejectInvalidEnumValue() {
        var api = createTestApi(List.of(
                new ApiParameter("status", "query", "string", false, null,
                        List.of("ACTIVE", "PENDING"), null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "INVALID");

        assertThrows(IllegalArgumentException.class, () ->
                mapper.mapParameters(api, request, Map.of()));
    }

    @Test
    void shouldAcceptValidEnumValue() {
        var api = createTestApi(List.of(
                new ApiParameter("status", "query", "string", false, null,
                        List.of("ACTIVE", "PENDING"), null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "ACTIVE");
        var result = mapper.mapParameters(api, request, Map.of());

        assertEquals("ACTIVE", result.get("status"));
    }

    @Test
    void shouldThrowOnMissingRequiredParam() {
        var api = createTestApi(List.of(
                new ApiParameter("orderId", "query", "string", true, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();

        var ex = assertThrows(IllegalArgumentException.class, () ->
                mapper.mapParameters(api, request, Map.of()));
        assertTrue(ex.getMessage().contains("orderId"));
    }

    @Test
    void shouldThrowOnEmptyRequiredParam() {
        var api = createTestApi(List.of(
                new ApiParameter("orderId", "query", "string", true, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("orderId", "");

        var ex = assertThrows(IllegalArgumentException.class, () ->
                mapper.mapParameters(api, request, Map.of()));
        assertTrue(ex.getMessage().contains("orderId"));
    }

    @Test
    void shouldSkipMissingOptionalParam() {
        var api = createTestApi(List.of(
                new ApiParameter("status", "query", "string", false, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        var result = mapper.mapParameters(api, request, Map.of());

        assertFalse(result.containsKey("status"));
    }

    @Test
    void shouldIncludePresentRequiredParam() {
        var api = createTestApi(List.of(
                new ApiParameter("orderId", "query", "string", true, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("orderId", "ORD-123");
        var result = mapper.mapParameters(api, request, Map.of());

        assertEquals("ORD-123", result.get("orderId"));
    }

    @Test
    void shouldTreatZeroAsPresent() {
        var api = createTestApi(List.of(
                new ApiParameter("count", "query", "integer", false, null, null, null, null, null, null)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("count", "0");
        var result = mapper.mapParameters(api, request, Map.of());

        assertEquals(0, result.get("count"));
    }
}
