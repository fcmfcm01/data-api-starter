package org.cafeng.openapi.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyAuthenticationProviderTest {

    private final ApiKeyAuthenticationProvider provider = new ApiKeyAuthenticationProvider("key1,key2");

    @Test
    void shouldAuthenticateValidKey() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn("key1");

        AuthResult result = provider.authenticate(request);

        assertTrue(result.authenticated());
        assertTrue(result.callerId().startsWith("apikey:"));
        assertTrue(result.scopes().isEmpty());
    }

    @Test
    void shouldAuthenticateSecondKey() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn("key2");

        AuthResult result = provider.authenticate(request);

        assertTrue(result.authenticated());
    }

    @Test
    void shouldReturnUnauthenticatedWhenNoHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn(null);

        AuthResult result = provider.authenticate(request);

        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }

    @Test
    void shouldReturnDeniedForInvalidKey() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn("wrong");

        AuthResult result = provider.authenticate(request);

        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }

    @Test
    void shouldHandleBlankKey() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn("  ");

        AuthResult result = provider.authenticate(request);

        assertFalse(result.authenticated());
        assertNull(result.callerId());
    }

    @Test
    void shouldHandleWhitespaceAroundKeys() {
        ApiKeyAuthenticationProvider whitespaceProvider = new ApiKeyAuthenticationProvider(" key1 , key2 ");

        HttpServletRequest request1 = mock(HttpServletRequest.class);
        when(request1.getHeader("X-API-Key")).thenReturn("key1");
        assertTrue(whitespaceProvider.authenticate(request1).authenticated());

        HttpServletRequest request2 = mock(HttpServletRequest.class);
        when(request2.getHeader("X-API-Key")).thenReturn("key2");
        assertTrue(whitespaceProvider.authenticate(request2).authenticated());
    }
}
