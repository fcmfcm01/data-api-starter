package org.cafeng.openapi.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationIntegrationTest {

    @Test
    void noOpProviderShouldAuthenticateAllRequests() {
        AuthenticationProvider provider = new NoOpAuthenticationProvider();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Caller-Id")).thenReturn("user1");

        AuthResult result = provider.authenticate(req);

        assertTrue(result.authenticated());
        assertEquals("user1", result.callerId());
    }

    @Test
    void noOpProviderShouldDefaultToAnonymousWhenNoCallerId() {
        AuthenticationProvider provider = new NoOpAuthenticationProvider();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Caller-Id")).thenReturn(null);

        AuthResult result = provider.authenticate(req);

        assertTrue(result.authenticated());
        assertEquals("anonymous", result.callerId());
    }

    @Test
    void jwtProviderShouldReturnUnauthenticatedWhenNoBearer() {
        AuthenticationProvider provider = new JwtAuthenticationProvider("test-secret-key-1234567890");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(null);

        AuthResult result = provider.authenticate(req);

        assertFalse(result.authenticated());
    }

    @Test
    void jwtProviderShouldReturnUnauthenticatedForInvalidBearer() {
        AuthenticationProvider provider = new JwtAuthenticationProvider("test-secret-key-1234567890");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");

        AuthResult result = provider.authenticate(req);

        assertFalse(result.authenticated());
    }

    @Test
    void jwtProviderShouldAuthenticateWithValidBearer() {
        String secret = "test-secret-key-1234567890";
        AuthenticationProvider provider = new JwtAuthenticationProvider(secret);

        // Create a valid JWT
        com.auth0.jwt.algorithms.Algorithm algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(secret);
        String token = com.auth0.jwt.JWT.create()
                .withSubject("test-user")
                .withClaim("scope", "read write")
                .sign(algorithm);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);

        AuthResult result = provider.authenticate(req);

        assertTrue(result.authenticated());
        assertEquals("test-user", result.callerId());
        assertTrue(result.scopes().contains("read"));
        assertTrue(result.scopes().contains("write"));
    }

    @Test
    void apiKeyProviderShouldReturnUnauthenticatedWhenNoApiKey() {
        AuthenticationProvider provider = new ApiKeyAuthenticationProvider("key1,key2");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-API-Key")).thenReturn(null);

        AuthResult result = provider.authenticate(req);

        assertFalse(result.authenticated());
    }

    @Test
    void apiKeyProviderShouldReturnUnauthenticatedForInvalidKey() {
        AuthenticationProvider provider = new ApiKeyAuthenticationProvider("valid-key");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-API-Key")).thenReturn("wrong-key");

        AuthResult result = provider.authenticate(req);

        assertFalse(result.authenticated());
    }

    @Test
    void apiKeyProviderShouldAuthenticateWithValidKey() {
        AuthenticationProvider provider = new ApiKeyAuthenticationProvider("my-secret-key");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-API-Key")).thenReturn("my-secret-key");

        AuthResult result = provider.authenticate(req);

        assertTrue(result.authenticated());
        assertNotNull(result.callerId());
        assertTrue(result.callerId().startsWith("apikey:"));
    }

    @Test
    void authResultAuthenticatedShouldSetFields() {
        AuthResult result = AuthResult.authenticated("user1", Set.of("read", "write"));
        assertTrue(result.authenticated());
        assertEquals("user1", result.callerId());
        assertEquals(Set.of("read", "write"), result.scopes());
    }

    @Test
    void authResultUnauthenticatedShouldHaveNoCallerId() {
        AuthResult result = AuthResult.unauthenticated();
        assertFalse(result.authenticated());
        assertNull(result.callerId());
        assertTrue(result.scopes().isEmpty());
    }

    @Test
    void authResultDeniedShouldNotBeAuthenticated() {
        AuthResult result = AuthResult.denied("bad token");
        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }

    @Test
    void authResultAuthenticatedShouldHandleNullScopes() {
        AuthResult result = AuthResult.authenticated("user1", null);
        assertTrue(result.authenticated());
        assertNotNull(result.scopes());
        assertTrue(result.scopes().isEmpty());
    }
}
