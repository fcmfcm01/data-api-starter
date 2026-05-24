package org.cafeng.openapi.definition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionValidationTest {

    @Test
    void apiDefinition_shouldRejectNullId() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiDefinition(null, "name", "/path", "GET",
                        List.of(), new ApiSource("jdbc", "ds", "SELECT 1"), new ApiResponse("list", List.of()),
                        Map.of(), new ApiSla(5000, null)));
    }

    @Test
    void apiDefinition_shouldRejectBlankId() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiDefinition("   ", "name", "/path", "GET",
                        List.of(), new ApiSource("jdbc", "ds", "SELECT 1"), new ApiResponse("list", List.of()),
                        Map.of(), new ApiSla(5000, null)));
    }

    @Test
    void apiDefinition_shouldRejectNullPath() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiDefinition("id", "name", null, "GET",
                        List.of(), new ApiSource("jdbc", "ds", "SELECT 1"), new ApiResponse("list", List.of()),
                        Map.of(), new ApiSla(5000, null)));
    }

    @Test
    void apiDefinition_shouldRejectNullMethod() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiDefinition("id", "name", "/path", null,
                        List.of(), new ApiSource("jdbc", "ds", "SELECT 1"), new ApiResponse("list", List.of()),
                        Map.of(), new ApiSla(5000, null)));
    }

    @Test
    void apiDefinition_shouldRejectNullSource() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiDefinition("id", "name", "/path", "GET",
                        List.of(), null, new ApiResponse("list", List.of()),
                        Map.of(), new ApiSla(5000, null)));
    }

    @Test
    void apiDefinition_shouldRejectNullResponse() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiDefinition("id", "name", "/path", "GET",
                        List.of(), new ApiSource("jdbc", "ds", "SELECT 1"), null,
                        Map.of(), new ApiSla(5000, null)));
    }

    @Test
    void apiDefinition_shouldAcceptValidConstruction() {
        ApiDefinition def = new ApiDefinition("id", "name", "/path", "GET",
                List.of(), new ApiSource("jdbc", "ds", "SELECT 1"), new ApiResponse("list", List.of()),
                Map.of(), new ApiSla(5000, null));
        assertEquals("id", def.id());
        assertEquals("/path", def.path());
        assertEquals("GET", def.method());
    }

    @Test
    void apiParameter_shouldRejectNullName() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiParameter(null, "query", "string", false, null, null, null, null, null, null));
    }

    @Test
    void apiParameter_shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiParameter("  ", "query", "string", false, null, null, null, null, null, null));
    }

    @Test
    void apiParameter_shouldDefaultInToQuery() {
        ApiParameter param = new ApiParameter("p", null, "string", false, null, null, null, null, null, null);
        assertEquals("query", param.in());
    }

    @Test
    void apiParameter_shouldDefaultTypeToString() {
        ApiParameter param = new ApiParameter("p", "query", null, false, null, null, null, null, null, null);
        assertEquals("string", param.type());
    }

    @Test
    void apiResponse_shouldDefaultTypeToList() {
        ApiResponse resp = new ApiResponse(null, List.of());
        assertEquals(ResponseType.LIST.yamlValue(), resp.type());
    }

    @Test
    void apiResponse_shouldAcceptPageType() {
        ApiResponse resp = new ApiResponse("page", List.of());
        assertEquals("page", resp.type());
    }

    @Test
    void apiResponse_shouldAcceptSingleType() {
        ApiResponse resp = new ApiResponse("single", List.of());
        assertEquals("single", resp.type());
    }

    @Test
    void apiResponse_shouldRejectInvalidType() {
        assertThrows(IllegalArgumentException.class, () -> new ApiResponse("invalid", List.of()));
    }

    @Test
    void responseField_shouldRejectNullName() {
        assertThrows(IllegalArgumentException.class, () -> new ResponseField(null, "basic", false, null));
    }

    @Test
    void responseField_shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new ResponseField("  ", "basic", false, null));
    }

    @Test
    void responseField_shouldDefaultScopeToBasic() {
        ResponseField field = new ResponseField("name", null, false, null);
        assertEquals("basic", field.scope());
    }

    @Test
    void responseField_shouldAcceptExplicitScope() {
        ResponseField field = new ResponseField("name", "detail", false, null);
        assertEquals("detail", field.scope());
    }

    @Test
    void apiSource_shouldDefaultTypeToJdbc() {
        ApiSource src = new ApiSource(null, "ds", "SELECT 1", null, null, null, 0);
        assertEquals("jdbc", src.type());
    }

    @Test
    void apiSource_jdbcShouldRejectNullDatasource() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiSource("jdbc", null, "SELECT 1", null, null, null, 0));
    }

    @Test
    void apiSource_jdbcShouldRejectNullQuery() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiSource("jdbc", "ds", null, null, null, null, 0));
    }

    @Test
    void apiSource_httpShouldRejectNullUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApiSource("http", null, null, null, null, null, 0));
    }

    @Test
    void apiSource_httpShouldAcceptValidUrl() {
        ApiSource src = new ApiSource("http", null, null, "http://upstream/api", null, null, 0);
        assertEquals("http", src.type());
        assertEquals("http://upstream/api", src.url());
    }

    @Test
    void apiSource_jdbcCompactConstructorShouldWork() {
        ApiSource src = new ApiSource("jdbc", "ds", "SELECT 1");
        assertEquals("jdbc", src.type());
        assertEquals("ds", src.datasource());
        assertEquals("SELECT 1", src.query());
    }

    @Test
    void apiSla_shouldDefaultTimeoutTo5000() {
        ApiSla sla = new ApiSla(null, null);
        assertEquals(5000, sla.timeout());
    }

    @Test
    void apiSla_shouldAcceptExplicitTimeout() {
        ApiSla sla = new ApiSla(3000, 100);
        assertEquals(3000, sla.timeout());
        assertEquals(100, sla.rateLimit());
    }
}
