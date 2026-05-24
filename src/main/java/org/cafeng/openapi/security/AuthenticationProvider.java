package org.cafeng.openapi.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for authenticating incoming API requests.
 *
 * <p>The framework selects an implementation based on {@code data-api.auth-type}:
 * {@code none} (default), {@code jwt}, or {@code apikey}. Applications can
 * register a custom bean to override the default.</p>
 */
public interface AuthenticationProvider {
    AuthResult authenticate(HttpServletRequest request);
}
