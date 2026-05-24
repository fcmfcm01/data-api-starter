package org.cafeng.openapi.error;

/**
 * Thrown when request parameters fail validation (missing required,
 * enum mismatch, type conversion failure).
 */
public class ValidationException extends DataApiException {

    public ValidationException(String message) {
        super(message);
    }
}
