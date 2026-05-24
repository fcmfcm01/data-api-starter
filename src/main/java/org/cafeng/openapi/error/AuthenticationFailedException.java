package org.cafeng.openapi.error;

/**
 * Thrown when authentication fails (invalid credentials, expired token).
 */
public class AuthenticationFailedException extends DataApiException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
