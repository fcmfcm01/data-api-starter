package org.cafeng.openapi.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Pass-through authentication provider that trusts all requests.
 *
 * <p>Reads the {@code X-Caller-Id} header for scope resolution.
 * Falls back to {@code "anonymous"} when the header is absent.
 * This is the default when {@code data-api.auth-type} is {@code none}.</p>
 */
public class NoOpAuthenticationProvider implements AuthenticationProvider {

    @Override
    public AuthResult authenticate(HttpServletRequest request) {
        String callerId = request.getHeader("X-Caller-Id");
        if (callerId == null || callerId.isEmpty()) {
            callerId = "anonymous";
        }
        return AuthResult.authenticated(callerId, Set.of());
    }
}
