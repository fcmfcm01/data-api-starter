package org.cafeng.openapi.router;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathConflictDetectorTest {

    @Test
    void shouldCreateApiPathRecord() {
        var apiPath = new PathConflictDetector.ApiPath("/api/test", Set.of("GET", "POST"));
        assertEquals("/api/test", apiPath.path());
        assertEquals(2, apiPath.methods().size());
        assertTrue(apiPath.methods().contains("GET"));
        assertTrue(apiPath.methods().contains("POST"));
    }

    @Test
    void shouldCreateConflictInfoRecord() {
        var conflict = new PathConflictDetector.ConflictInfo(
                "/api/test", "/api/test", "GET", "Path conflict detected");
        assertEquals("/api/test", conflict.existingPath());
        assertEquals("/api/test", conflict.yamlPath());
        assertEquals("GET", conflict.method());
        assertEquals("Path conflict detected", conflict.message());
    }

    @Test
    void shouldSupportEqualityOnConflictInfo() {
        var c1 = new PathConflictDetector.ConflictInfo("/a", "/b", "GET", "msg");
        var c2 = new PathConflictDetector.ConflictInfo("/a", "/b", "GET", "msg");
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void shouldDetectDifferentConflictInfo() {
        var c1 = new PathConflictDetector.ConflictInfo("/a", "/b", "GET", "msg1");
        var c2 = new PathConflictDetector.ConflictInfo("/a", "/b", "POST", "msg2");
        assertNotEquals(c1, c2);
    }

    @Test
    void shouldCreateApiPathWithSingleMethod() {
        var apiPath = new PathConflictDetector.ApiPath("/api/orders", Set.of("GET"));
        assertEquals(1, apiPath.methods().size());
        assertEquals("GET", apiPath.methods().iterator().next());
    }

    @Test
    void shouldCreateApiPathWithEmptyMethods() {
        var apiPath = new PathConflictDetector.ApiPath("/api/test", Set.of());
        assertTrue(apiPath.methods().isEmpty());
    }
}
