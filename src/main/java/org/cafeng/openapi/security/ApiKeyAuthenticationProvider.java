package org.cafeng.openapi.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Authenticates requests via static API keys in the {@code X-API-Key} header.
 *
 * <p>Accepts a comma-separated list of valid keys configured through
 * {@code data-api.api-keys}. Selected when {@code data-api.auth-type}
 * is {@code apikey}. Generates a hex-encoded caller ID from the key hash.</p>
 */
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationProvider.class);
    private final Set<String> validKeys;

    public ApiKeyAuthenticationProvider(String commaSeparatedKeys) {
        Set<String> keys = new LinkedHashSet<>();
        if (commaSeparatedKeys != null && !commaSeparatedKeys.isBlank()) {
            for (String key : commaSeparatedKeys.split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isEmpty()) {
                    keys.add(trimmed);
                }
            }
        }
        this.validKeys = Collections.unmodifiableSet(keys);
    }

    @Override
    public AuthResult authenticate(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            return AuthResult.unauthenticated();
        }
        if (validKeys.contains(apiKey.trim())) {
            String callerId = "apikey:" + Integer.toHexString(apiKey.hashCode());
            return AuthResult.authenticated(callerId, Set.of());
        }
        log.debug("Invalid API key provided");
        return AuthResult.denied("Invalid API key");
    }
}
