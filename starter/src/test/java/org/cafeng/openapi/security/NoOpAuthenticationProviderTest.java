package org.cafeng.openapi.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoOpAuthenticationProviderTest {

    private final NoOpAuthenticationProvider provider = new NoOpAuthenticationProvider();

    @Test
    void shouldReturnAuthenticatedForAnyRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        AuthResult result = provider.authenticate(request);

        assertTrue(result.authenticated());
        assertEquals("anonymous", result.callerId());
        assertTrue(result.scopes().isEmpty());
    }
}
