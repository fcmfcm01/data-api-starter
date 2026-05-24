package org.cafeng.openapi.engine;

import org.cafeng.openapi.scope.ConfigScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DdlGuardTest {

    private DdlGuard guard;
    private ConfigScopeResolver scopeResolver;

    @BeforeEach
    void setUp() {
        scopeResolver = new ConfigScopeResolver("");
        guard = new DdlGuard(scopeResolver);
    }

    @Test
    void shouldDetectCreateTable() {
        assertTrue(guard.isDdlOperation("CREATE TABLE users (id INT)"));
    }

    @Test
    void shouldDetectAlterTable() {
        assertTrue(guard.isDdlOperation("ALTER TABLE users ADD COLUMN email VARCHAR(100)"));
    }

    @Test
    void shouldDetectDropTable() {
        assertTrue(guard.isDdlOperation("DROP TABLE users"));
    }

    @Test
    void shouldDetectTruncate() {
        assertTrue(guard.isDdlOperation("TRUNCATE TABLE users"));
    }

    @Test
    void shouldDetectRename() {
        assertTrue(guard.isDdlOperation("RENAME TABLE old_name TO new_name"));
    }

    @Test
    void shouldDetectGrant() {
        assertTrue(guard.isDdlOperation("GRANT SELECT ON users TO public"));
    }

    @Test
    void shouldDetectRevoke() {
        assertTrue(guard.isDdlOperation("REVOKE SELECT ON users FROM public"));
    }

    @Test
    void shouldNotDetectSelect() {
        assertFalse(guard.isDdlOperation("SELECT * FROM users"));
    }

    @Test
    void shouldNotDetectInsert() {
        assertFalse(guard.isDdlOperation("INSERT INTO users VALUES (1, 'Alice')"));
    }

    @Test
    void shouldNotDetectUpdate() {
        assertFalse(guard.isDdlOperation("UPDATE users SET name = 'Bob'"));
    }

    @Test
    void shouldNotDetectDelete() {
        assertFalse(guard.isDdlOperation("DELETE FROM users WHERE id = 1"));
    }

    @Test
    void shouldHandleNull() {
        assertFalse(guard.isDdlOperation(null));
    }

    @Test
    void shouldHandleEmpty() {
        assertFalse(guard.isDdlOperation(""));
    }

    @Test
    void shouldHandleCaseInsensitive() {
        assertTrue(guard.isDdlOperation("create table users (id int)"));
        assertTrue(guard.isDdlOperation("drop table users"));
    }

    @Test
    void shouldHandleLeadingWhitespace() {
        assertTrue(guard.isDdlOperation("  CREATE TABLE users (id INT)"));
    }

    @Test
    void shouldAllowDdlWithCorrectScope() {
        scopeResolver.registerScopeMapping("admin", java.util.Set.of("basic", "detail", "ddl"));
        assertDoesNotThrow(() -> guard.check("CREATE TABLE test (id INT)", "admin"));
    }

    @Test
    void shouldRejectDdlWithoutDdlScope() {
        scopeResolver.registerScopeMapping("user", java.util.Set.of("basic", "detail"));
        assertThrows(DdlGuard.DdlPermissionDeniedException.class,
                () -> guard.check("DROP TABLE users", "user"));
    }

    @Test
    void shouldRejectDdlWithUnknownCaller() {
        assertThrows(DdlGuard.DdlPermissionDeniedException.class,
                () -> guard.check("DROP TABLE users", "unknown-caller"));
    }

    @Test
    void shouldRejectDdlWithNullCaller() {
        assertThrows(DdlGuard.DdlPermissionDeniedException.class,
                () -> guard.check("DROP TABLE users", null));
    }

    @Test
    void shouldAllowNonDdlWithoutDdlScope() {
        assertDoesNotThrow(() -> guard.check("SELECT * FROM users", "user"));
        assertDoesNotThrow(() -> guard.check("SELECT * FROM users", null));
    }

    @Test
    void shouldAllowInsertWithoutDdlScope() {
        assertDoesNotThrow(() -> guard.check("INSERT INTO users VALUES (1, 'test')", "user"));
    }

    @Test
    void shouldReportCallerIdInError() {
        DdlGuard.DdlPermissionDeniedException ex = assertThrows(
                DdlGuard.DdlPermissionDeniedException.class,
                () -> guard.check("DROP TABLE users", "hacker"));
        assertTrue(ex.getMessage().contains("hacker"));
        assertTrue(ex.getMessage().contains("ddl"));
    }
}
