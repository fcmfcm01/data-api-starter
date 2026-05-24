package org.cafeng.openapi.scope;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.definition.ApiSource;
import org.cafeng.openapi.definition.ApiResponse;
import org.cafeng.openapi.definition.ResponseField;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScopeFilterTest {

    private final ScopeFilter filter = new ScopeFilter();

    private ApiDefinition createTestApi(List<ResponseField> fields) {
        return new ApiDefinition(
                "test-api", "Test", "/api/test", "GET",
                null,
                new ApiSource("jdbc", "dataSource", "SELECT 1"),
                new ApiResponse("list", fields),
                Map.of(),
                null
        );
    }

    private Map<String, Object> rowOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void shouldFilterSensitiveFieldsWhenNoScope() {
        List<ResponseField> fields = List.of(
                new ResponseField("id", "basic", false, null),
                new ResponseField("name", "basic", false, null),
                new ResponseField("salary", "sensitive", false, null)
        );
        var api = createTestApi(fields);
        
        List<Map<String, Object>> data = List.of(rowOf("id", 1, "name", "Alice", "salary", 50000));

        var result = filter.apply(data, Set.of(), api);

        assertEquals(1, result.size());
        assertTrue(result.get(0).containsKey("id"));
        assertTrue(result.get(0).containsKey("name"));
        assertFalse(result.get(0).containsKey("salary"));
    }

    @Test
    void shouldReturnBasicFieldsForBasicScope() {
        List<ResponseField> fields = List.of(
                new ResponseField("id", "basic", false, null),
                new ResponseField("name", "basic", false, null),
                new ResponseField("email", "detail", false, null)
        );
        var api = createTestApi(fields);

        List<Map<String, Object>> data = List.of(rowOf("id", 1, "name", "Alice", "email", "a@b.com"));

        var result = filter.apply(data, Set.of("basic"), api);

        assertTrue(result.get(0).containsKey("id"));
        assertTrue(result.get(0).containsKey("name"));
        assertFalse(result.get(0).containsKey("email"));
    }

    @Test
    void shouldReturnBasicAndDetailForDetailScope() {
        List<ResponseField> fields = List.of(
                new ResponseField("id", "basic", false, null),
                new ResponseField("name", "basic", false, null),
                new ResponseField("email", "detail", false, null),
                new ResponseField("salary", "sensitive", false, null)
        );
        var api = createTestApi(fields);

        List<Map<String, Object>> data = List.of(rowOf("id", 1, "name", "Alice", "email", "a@b.com", "salary", 50000));

        var result = filter.apply(data, Set.of("detail"), api);

        assertTrue(result.get(0).containsKey("id"));
        assertTrue(result.get(0).containsKey("name"));
        assertTrue(result.get(0).containsKey("email"));
        assertFalse(result.get(0).containsKey("salary"));
    }

    @Test
    void shouldReturnAllFieldsForSensitiveScope() {
        List<ResponseField> fields = List.of(
                new ResponseField("id", "basic", false, null),
                new ResponseField("email", "detail", false, null),
                new ResponseField("salary", "sensitive", false, null)
        );
        var api = createTestApi(fields);

        List<Map<String, Object>> data = List.of(rowOf("id", 1, "email", "a@b.com", "salary", 50000));

        var result = filter.apply(data, Set.of("sensitive"), api);

        assertEquals(3, result.get(0).size());
    }

    @Test
    void shouldHandleEmptyData() {
        List<ResponseField> fields = List.of(new ResponseField("id", "basic", false, null));
        var api = createTestApi(fields);

        var result = filter.apply(List.of(), Set.of("basic"), api);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPreserveNullFieldValues() {
        List<ResponseField> fields = List.of(
                new ResponseField("id", "basic", false, null),
                new ResponseField("name", "basic", false, null)
        );
        var api = createTestApi(fields);

        Map<String, Object> row = rowOf("id", 1, "name", null);

        var result = filter.apply(List.of(row), Set.of("basic"), api);

        assertTrue(result.get(0).containsKey("name"));
        assertNull(result.get(0).get("name"));
    }

    @Test
    void shouldHandleApiWithNullFields() {
        var api = new ApiDefinition(
                "test-api", "Test", "/api/test", "GET",
                null, new ApiSource("jdbc", "dataSource", "SELECT 1"),
                new ApiResponse("list", null), Map.of(), null
        );

        List<Map<String, Object>> data = List.of(rowOf("id", 1));
        var result = filter.apply(data, Set.of(), api);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).size());
    }
}
