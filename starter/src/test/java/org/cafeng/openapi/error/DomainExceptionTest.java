package org.cafeng.openapi.error;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionTest {

    @Test
    void authenticationFailedExceptionShouldExtendDataApiException() {
        AuthenticationFailedException ex = new AuthenticationFailedException("bad token");
        assertInstanceOf(DataApiException.class, ex);
        assertEquals("bad token", ex.getMessage());
    }

    @Test
    void rateLimitExceededExceptionShouldExtendDataApiException() {
        RateLimitExceededException ex = new RateLimitExceededException(60);
        assertInstanceOf(DataApiException.class, ex);
    }

    @Test
    void resourceNotFoundExceptionShouldExtendDataApiException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("not found");
        assertInstanceOf(DataApiException.class, ex);
        assertEquals("not found", ex.getMessage());
    }

    @Test
    void validationExceptionShouldExtendDataApiException() {
        ValidationException ex = new ValidationException("invalid input");
        assertInstanceOf(DataApiException.class, ex);
        assertEquals("invalid input", ex.getMessage());
    }

    @Test
    void dataApiExceptionShouldHaveCorrelationId() {
        DataApiException ex = new DataApiException("test error");
        assertNotNull(ex.getCorrelationId());
        assertDoesNotThrow(() -> UUID.fromString(ex.getCorrelationId()));
    }

    @Test
    void dataApiExceptionWithCauseShouldHaveCorrelationId() {
        DataApiException ex = new DataApiException("wrapped", new RuntimeException("cause"));
        assertNotNull(ex.getCorrelationId());
        assertInstanceOf(RuntimeException.class, ex);
        assertNotNull(ex.getCause());
        assertEquals("cause", ex.getCause().getMessage());
    }

    @Test
    void correlationIdShouldBeUnique() {
        DataApiException ex1 = new DataApiException("first");
        DataApiException ex2 = new DataApiException("second");
        assertNotEquals(ex1.getCorrelationId(), ex2.getCorrelationId());
    }

    @Test
    void rateLimitExceededExceptionShouldHaveRetryAfterSeconds() {
        RateLimitExceededException ex = new RateLimitExceededException(30);
        assertEquals(30, ex.getRetryAfterSeconds());
        assertEquals("Rate limit exceeded", ex.getMessage());
    }

    @Test
    void rateLimitExceededExceptionShouldPreserveCorrelationId() {
        RateLimitExceededException ex = new RateLimitExceededException(10);
        assertNotNull(ex.getCorrelationId());
        assertDoesNotThrow(() -> UUID.fromString(ex.getCorrelationId()));
    }

    @Test
    void authenticationFailedExceptionShouldPreserveCorrelationId() {
        AuthenticationFailedException ex = new AuthenticationFailedException("expired");
        assertNotNull(ex.getCorrelationId());
    }

    @Test
    void resourceNotFoundExceptionShouldPreserveCorrelationId() {
        ResourceNotFoundException ex = new ResourceNotFoundException("id=42");
        assertNotNull(ex.getCorrelationId());
    }

    @Test
    void validationExceptionShouldPreserveCorrelationId() {
        ValidationException ex = new ValidationException("bad");
        assertNotNull(ex.getCorrelationId());
    }
}
