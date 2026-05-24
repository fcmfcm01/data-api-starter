package org.cafeng.openapi.scope;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtScopeResolverTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only";
    private final JwtScopeResolver resolver = new JwtScopeResolver(SECRET);

    @Test
    void shouldResolveScopesFromValidToken() {
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        String token = JWT.create()
                .withClaim("scope", "basic detail financial")
                .sign(algorithm);

        Set<String> scopes = resolver.resolveScopes(token);

        assertEquals(Set.of("basic", "detail", "financial"), scopes);
    }

    @Test
    void shouldReturnEmptyWhenNoScopeClaim() {
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        String token = JWT.create()
                .sign(algorithm);

        Set<String> scopes = resolver.resolveScopes(token);

        assertTrue(scopes.isEmpty());
    }

    @Test
    void shouldReturnEmptyForInvalidToken() {
        Set<String> scopes = resolver.resolveScopes("invalid.jwt.token");

        assertTrue(scopes.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullCallerId() {
        Set<String> scopes = resolver.resolveScopes(null);

        assertTrue(scopes.isEmpty());
    }

    @Test
    void shouldReturnEmptyForEmptyScopeClaim() {
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        String token = JWT.create()
                .withClaim("scope", "")
                .sign(algorithm);

        Set<String> scopes = resolver.resolveScopes(token);

        assertTrue(scopes.isEmpty());
    }

    @Test
    void shouldSplitMultipleSpaceSeparatedScopes() {
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        String token = JWT.create()
                .withClaim("scope", "basic  detail   financial")
                .sign(algorithm);

        Set<String> scopes = resolver.resolveScopes(token);

        assertEquals(Set.of("basic", "detail", "financial"), scopes);
    }
}
