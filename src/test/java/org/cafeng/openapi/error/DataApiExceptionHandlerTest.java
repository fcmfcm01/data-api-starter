package org.cafeng.openapi.error;

import org.junit.jupiter.api.Test;

import java.sql.SQLTimeoutException;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DataApiExceptionHandlerTest {

    private final DataApiExceptionHandler handler = new DataApiExceptionHandler();

    @Test
    void shouldReturn503ForSqlTimeout() {
        var ex = new SQLTimeoutException("Query took too long");
        var response = handler.handleTimeout(ex);

        assertEquals(503, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.status());
        assertEquals("Query timeout", body.error());
        assertEquals("Query took too long", body.message());
        assertNotNull(body.requestId());
    }

    @Test
    void shouldReturn400ForIllegalArgument() {
        var ex = new IllegalArgumentException("Missing required param: status");
        var response = handler.handleBadRequest(ex);

        assertEquals(400, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.status());
        assertEquals("Bad request", body.error());
        assertEquals("Missing required param: status", body.message());
    }

    @Test
    void shouldReturn500ForGenericException() {
        var ex = new RuntimeException("Unexpected failure");
        var response = handler.handleGeneric(ex);

        assertEquals(500, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.status());
        assertEquals("Internal server error", body.error());
        assertEquals("Unexpected failure", body.message());
    }

    @Test
    void shouldGenerateUniqueRequestIdPerCall() {
        var response1 = handler.handleBadRequest(new IllegalArgumentException("test1"));
        var response2 = handler.handleBadRequest(new IllegalArgumentException("test2"));

        assertNotEquals(response1.getBody().requestId(), response2.getBody().requestId());
    }

    @Test
    void shouldIncludeExceptionMessage() {
        String msg = "Connection pool exhausted";
        var response = handler.handleGeneric(new RuntimeException(msg));

        assertEquals(msg, response.getBody().message());
    }

    @Test
    void shouldReturnGenericMessageForNullMessage() {
        var response = handler.handleGeneric(new RuntimeException((String) null));

        var body = response.getBody();
        assertNotNull(body);
        assertEquals("An internal error occurred", body.message());
    }

    @Test
    void shouldGenerateValidUuidRequestId() {
        var response = handler.handleBadRequest(new IllegalArgumentException("test"));

        assertDoesNotThrow(() -> UUID.fromString(response.getBody().requestId()));
    }

    @Test
    void shouldReturnGenericMessageForSqlTimeoutWithNullMessage() {
        var response = handler.handleTimeout(new SQLTimeoutException());

        assertEquals(503, response.getStatusCode().value());
        assertEquals("An internal error occurred", response.getBody().message());
    }
}
