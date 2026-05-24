package org.cafeng.openapi.engine;

import java.util.List;
import java.util.Map;

/**
 * Assembles a paginated response envelope with content, totals, and page metadata.
 *
 * <p>Returns a map with keys {@code content}, {@code totalElements},
 * {@code totalPages}, {@code page}, and {@code size}.</p>
 */
public class PageResponseBuilder {

    public Map<String, Object> build(
            List<Map<String, Object>> content,
            long totalElements,
            int page,
            int size) {
        
        long totalPages = (totalElements + size - 1) / size;
        
        return Map.of(
                "content", content,
                "totalElements", totalElements,
                "totalPages", totalPages,
                "page", page,
                "size", size
        );
    }
}