package org.cafeng.openapi.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorMessageSanitizerTest {

    @Test
    void shouldReturnGenericForNull() {
        assertEquals("An internal error occurred", ErrorMessageSanitizer.sanitize(null));
    }

    @Test
    void shouldReturnGenericForBlank() {
        assertEquals("An internal error occurred", ErrorMessageSanitizer.sanitize("   "));
    }

    @Test
    void shouldStripSqlKeywords() {
        String result = ErrorMessageSanitizer.sanitize("SELECT * FROM users WHERE id = 1");
        assertFalse(result.contains("SELECT"));
        assertFalse(result.contains("FROM"));
        assertFalse(result.contains("WHERE"));
    }

    @Test
    void shouldStripQuotedIdentifiers() {
        String result = ErrorMessageSanitizer.sanitize("Table \"orders\" not found");
        assertFalse(result.contains("\"orders\""));
        assertFalse(result.contains("\""));
    }

    @Test
    void shouldStripColumnRefs() {
        String result = ErrorMessageSanitizer.sanitize("Column users.name not found");
        assertFalse(result.contains("users.name"));
    }

    @Test
    void shouldReturnGenericForShortSanitized() {
        assertEquals("An internal error occurred", ErrorMessageSanitizer.sanitize("ab"));
    }

    @Test
    void shouldPassThroughCleanMessage() {
        assertEquals("Connection pool exhausted", ErrorMessageSanitizer.sanitize("Connection pool exhausted"));
    }

    @Test
    void genericErrorReturnsConstant() {
        assertEquals("An internal error occurred", ErrorMessageSanitizer.genericError());
    }
}
