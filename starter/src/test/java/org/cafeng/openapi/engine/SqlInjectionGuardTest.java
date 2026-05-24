package org.cafeng.openapi.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlInjectionGuardTest {

    private SqlInjectionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SqlInjectionGuard();
    }

    @Test
    void shouldAcceptNormalValues() {
        assertDoesNotThrow(() -> guard.validate(Map.of("name", "Alice", "age", 30)));
    }

    @Test
    void shouldAcceptNullMap() {
        assertDoesNotThrow(() -> guard.validate(null));
    }

    @Test
    void shouldRejectSqlComment() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("name", "Alice -- DROP TABLE users")));
    }

    @Test
    void shouldRejectBlockComment() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("name", "Alice /* comment */")));
    }

    @Test
    void shouldRejectUnionInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("id", "1 UNION SELECT * FROM passwords")));
    }

    @Test
    void shouldRejectBooleanInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("id", "' OR 1=1")));
    }

    @Test
    void shouldRejectStatementTermination() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("id", "1; DROP TABLE users")));
    }

    @Test
    void shouldRejectTimeBasedInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("id", "BENCHMARK(1000000, SHA1('test'))")));
    }

    @Test
    void shouldRejectHexEncodedPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("name", "0x73656c656374")));
    }

    @Test
    void shouldAcceptIntegerValues() {
        assertDoesNotThrow(() -> guard.validate(Map.of("id", 42, "count", 0)));
    }

    @Test
    void shouldRejectFileOperations() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("path", "LOAD_FILE('/etc/passwd')")));
    }

    @Test
    void shouldReportParameterNameInError() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.validate(Map.of("searchTerm", "test; DROP TABLE users")));
        assertTrue(ex.getMessage().contains("searchTerm"));
    }

    @Test
    void shouldAcceptSpecialButSafeCharacters() {
        assertDoesNotThrow(() -> guard.validate(Map.of(
                "email", "user@example.com",
                "name", "O'Brien",
                "desc", "100% guaranteed"
        )));
    }
}
