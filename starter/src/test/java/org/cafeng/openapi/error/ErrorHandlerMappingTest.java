package org.cafeng.openapi.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlerMappingTest {

    private final DataApiExceptionHandler handler = new DataApiExceptionHandler();

    @Test
    void authenticationFailedExceptionShouldReturn401() {
        AuthenticationFailedException ex = new AuthenticationFailedException("Invalid credentials");
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleAuthFailed(ex);

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().status());
        assertEquals("Unauthorized", response.getBody().error());
        assertEquals("Invalid credentials", response.getBody().message());
        assertEquals(ex.getCorrelationId(), response.getBody().requestId());
    }

    @Test
    void rateLimitExceededExceptionShouldReturn429WithRetryAfterHeader() {
        RateLimitExceededException ex = new RateLimitExceededException(60);
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleRateLimit(ex);

        assertEquals(429, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(429, response.getBody().status());
        assertEquals("Rate limit exceeded", response.getBody().error());
        assertEquals("60", response.getHeaders().getFirst("Retry-After"));
        assertEquals(ex.getCorrelationId(), response.getBody().requestId());
    }

    @Test
    void resourceNotFoundExceptionShouldReturn404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order 123 not found");
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not found", response.getBody().error());
        assertEquals("Order 123 not found", response.getBody().message());
        assertEquals(ex.getCorrelationId(), response.getBody().requestId());
    }

    @Test
    void validationExceptionShouldReturn400() {
        ValidationException ex = new ValidationException("Missing required field: email");
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleValidation(ex);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Validation failed", response.getBody().error());
        assertEquals("Missing required field: email", response.getBody().message());
        assertEquals(ex.getCorrelationId(), response.getBody().requestId());
    }

    @Test
    void dataApiExceptionShouldReturn500() {
        DataApiException ex = new DataApiException("Unexpected DB error");
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleDataApi(ex);

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("Internal server error", response.getBody().error());
        assertEquals("Unexpected DB error", response.getBody().message());
        assertEquals(ex.getCorrelationId(), response.getBody().requestId());
    }

    @Test
    void rateLimitRetryAfterShouldMatchExceptionValue() {
        RateLimitExceededException ex = new RateLimitExceededException(120);
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleRateLimit(ex);

        assertEquals("120", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void authenticationFailedShouldUseCorrelationIdAsRequestId() {
        AuthenticationFailedException ex = new AuthenticationFailedException("token expired");
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleAuthFailed(ex);

        assertEquals(ex.getCorrelationId(), response.getBody().requestId());
    }

    @Test
    void validationExceptionShouldUseCorrelationIdAsRequestId() {
        ValidationException ex = new ValidationException("bad");
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleValidation(ex);

        assertEquals(ex.getCorrelationId(), response.getBody().requestId());
    }

    @Test
    void resourceNotFoundExceptionShouldUseCorrelationIdAsRequestId() {
        ResourceNotFoundException ex = new ResourceNotFoundException("gone");
        ResponseEntity<DataApiExceptionHandler.ErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(ex.getCorrelationId(), response.getBody().requestId());
    }
}
