package org.cafeng.openapi.security;

import java.util.Set;

/**
 * Immutable result of an authentication attempt.
 */
public record AuthResult(
        boolean authenticated,
        String callerId,
        Set<String> scopes
) {

    public static AuthResult authenticated(String callerId, Set<String> scopes) {
        return new AuthResult(true, callerId, scopes != null ? scopes : Set.of());
    }

    public static AuthResult unauthenticated() {
        return new AuthResult(false, null, Set.of());
    }

    public static AuthResult denied(String reason) {
        return new AuthResult(false, null, Set.of());
    }
}
