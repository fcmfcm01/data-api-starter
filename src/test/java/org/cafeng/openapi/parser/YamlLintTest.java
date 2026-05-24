package org.cafeng.openapi.parser;

import org.cafeng.openapi.definition.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlLintTest {

    private final YamlLint lint = new YamlLint();

    private ApiDefinition validApi() {
        return new ApiDefinition(
                "test-api", "Test", "/api/test", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT * FROM t"),
                new ApiResponse("list", null), Map.of(), null
        );
    }

    @Test
    void shouldPassForValidApi() {
        var errors = lint.lint(List.of(validApi()));
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldDetectDuplicateIds() {
        var api1 = validApi();
        var api2 = new ApiDefinition(
                "test-api", "Dup", "/api/dup", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", null), Map.of(), null
        );
        var errors = lint.lint(List.of(api1, api2));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Duplicate api.id"));
        assertTrue(errors.get(0).contains("test-api"));
    }

    @Test
    void shouldDetectPathMethodConflict() {
        var api1 = validApi();
        var api2 = new ApiDefinition(
                "test-api-2", "Dup", "/api/test", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", null), Map.of(), null
        );
        var errors = lint.lint(List.of(api1, api2));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate path+method")));
    }

    @Test
    void shouldDetectSqlInjectionPattern() {
        var api = new ApiDefinition(
                "inject-api", "Inject", "/api/inject", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT * FROM t WHERE id = ${input + ' OR 1=1'}"),
                new ApiResponse("list", null), Map.of(), null
        );
        var errors = lint.lint(List.of(api));
        assertTrue(errors.stream().anyMatch(e -> e.contains("SQL injection")));
    }

    @Test
    void shouldDetectEmptyIdField() {
        // ApiDefinition record enforces non-blank id at construction time.
        // YamlLint's checkRequiredFields is a defense-in-depth for raw YAML maps,
        // which are checked before record construction in the parser pipeline.
        // This test verifies that the record itself catches empty id.
        assertThrows(IllegalArgumentException.class, () -> new ApiDefinition(
                "", "Test", "/api/test", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", null), Map.of(), null
        ));
    }

    @Test
    void shouldDetectConditionWithoutBinding() {
        var api = new ApiDefinition(
                "bad-condition", "Bad", "/api/bad", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT * FROM t WHERE 1=1 ${status: AND status = 'ACTIVE'}"),
                new ApiResponse("list", null), Map.of(), null
        );
        var errors = lint.lint(List.of(api));
        assertTrue(errors.stream().anyMatch(e -> e.contains("must contain binding :status")));
    }

    @Test
    void shouldPassForValidConditionBinding() {
        var api = new ApiDefinition(
                "good-condition", "Good", "/api/good", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT * FROM t WHERE 1=1 ${status: AND status = :status}"),
                new ApiResponse("list", null), Map.of(), null
        );
        var errors = lint.lint(List.of(api));
        assertFalse(errors.stream().anyMatch(e -> e.contains("binding")));
    }

    @Test
    void shouldCollectMultipleErrors() {
        var api1 = validApi();
        var api2 = new ApiDefinition(
                "test-api", "Dup", "/api/test", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", null), Map.of(), null
        );
        var errors = lint.lint(List.of(api1, api2));
        assertTrue(errors.size() >= 2);
    }

    @Test
    void shouldThrowOnLintErrors() {
        var api1 = validApi();
        var api2 = new ApiDefinition(
                "test-api", "Dup", "/api/test", "GET",
                null, new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", null), Map.of(), null
        );
        assertThrows(IllegalStateException.class, () -> lint.lintAndThrow(List.of(api1, api2)));
    }
}
