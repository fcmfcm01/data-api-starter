package org.cafeng.openapi.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationProviderTest {

    private static final String SECRET = "test-secret-key";
    private JwtAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtAuthenticationProvider(SECRET);
    }

    @Test
    void shouldAuthenticateValidJwt() {
        String token = JWT.create()
                .withSubject("test-user")
                .withClaim("scope", "read write")
                .sign(Algorithm.HMAC256(SECRET));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        AuthResult result = provider.authenticate(request);

        assertTrue(result.authenticated());
        assertEquals("test-user", result.callerId());
        assertTrue(result.scopes().contains("read"));
        assertTrue(result.scopes().contains("write"));
    }

    @Test
    void shouldReturnUnauthenticatedWhenNoHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        AuthResult result = provider.authenticate(request);

        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }

    @Test
    void shouldReturnUnauthenticatedWhenNoBearerPrefix() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Token abc");

        AuthResult result = provider.authenticate(request);

        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }

    @Test
    void shouldReturnDeniedForInvalidToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer not-a-real-token");

        AuthResult result = provider.authenticate(request);

        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }

    @Test
    void shouldReturnDeniedForExpiredToken() {
        String token = JWT.create()
                .withSubject("test-user")
                .withClaim("scope", "read")
                .withExpiresAt(new Date(0))
                .sign(Algorithm.HMAC256(SECRET));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        AuthResult result = provider.authenticate(request);

        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }

    @Test
    void shouldReturnDeniedForWrongSecret() {
        String token = JWT.create()
                .withSubject("test-user")
                .withClaim("scope", "read")
                .sign(Algorithm.HMAC256("wrong-secret"));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        AuthResult result = provider.authenticate(request);

        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }
}
