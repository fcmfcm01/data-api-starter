package org.cafeng.openapi.error;

/**
 * Thrown when a single-result query returns no rows.
 */
public class ResourceNotFoundException extends DataApiException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
