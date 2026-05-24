package org.cafeng.openapi.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PageResponseBuilderTest {

    private final PageResponseBuilder builder = new PageResponseBuilder();

    @Test
    void shouldBuildPageResponse() {
        List<Map<String, Object>> content = List.of(
                new java.util.LinkedHashMap<>(Map.of("id", 1)),
                new java.util.LinkedHashMap<>(Map.of("id", 2)));
        var result = builder.build(content, 50, 1, 20);

        assertEquals(content, result.get("content"));
        assertEquals(50L, result.get("totalElements"));
        assertEquals(3L, result.get("totalPages"));
        assertEquals(1, result.get("page"));
        assertEquals(20, result.get("size"));
    }

    @Test
    void shouldCalculateTotalPagesCorrectly() {
        var result = builder.build(List.of(), 21, 1, 10);
        assertEquals(3L, result.get("totalPages"));

        var result2 = builder.build(List.of(), 20, 1, 10);
        assertEquals(2L, result2.get("totalPages"));

        var result3 = builder.build(List.of(), 1, 1, 10);
        assertEquals(1L, result3.get("totalPages"));
    }

    @Test
    void shouldHandleEmptyContent() {
        var result = builder.build(List.of(), 0, 1, 20);

        assertEquals(List.of(), result.get("content"));
        assertEquals(0L, result.get("totalElements"));
        assertEquals(0L, result.get("totalPages"));
    }

    @Test
    void shouldHandleLastPagePartial() {
        List<Map<String, Object>> content = List.of(
                new java.util.LinkedHashMap<>(Map.of("id", 1)));
        var result = builder.build(content, 11, 2, 10);

        assertEquals(1, ((List<?>) result.get("content")).size());
        assertEquals(2L, result.get("totalPages"));
        assertEquals(2, result.get("page"));
    }
}
