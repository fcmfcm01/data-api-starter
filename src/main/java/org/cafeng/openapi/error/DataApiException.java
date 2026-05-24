package org.cafeng.openapi.error;

import java.util.UUID;

/**
 * Base runtime exception for the data-api framework.
 *
 * <p>Carries an auto-generated correlation ID for log tracing.
 * Subclasses represent specific failure modes: authentication, rate limiting,
 * validation, and resource-not-found.</p>
 */
public class DataApiException extends RuntimeException {

    private final String correlationId;

    public DataApiException(String message) {
        super(message);
        this.correlationId = UUID.randomUUID().toString();
    }

    public DataApiException(String message, Throwable cause) {
        super(message, cause);
        this.correlationId = UUID.randomUUID().toString();
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
