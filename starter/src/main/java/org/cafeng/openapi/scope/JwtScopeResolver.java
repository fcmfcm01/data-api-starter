package org.cafeng.openapi.scope;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.*;

/**
 * Extracts scope tiers from a JWT token's {@code scope} claim.
 *
 * <p>Verifies the JWT signature using HMAC256 with the configured secret,
 * then splits the space-delimited scope claim into a set. Returns an empty
 * set for invalid or expired tokens.</p>
 */
public class JwtScopeResolver implements ScopeResolver {

    private final Algorithm algorithm;

    public JwtScopeResolver(String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    @Override
    public Set<String> resolveScopes(String token) {
        if (token == null || token.isBlank()) {
            return Set.of();
        }
        try {
            com.auth0.jwt.JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            String scopeClaim = jwt.getClaim("scope").asString();
            if (scopeClaim == null || scopeClaim.isBlank()) {
                return Set.of();
            }
            Set<String> scopes = new LinkedHashSet<>(ScopeUtils.parseScopeString(scopeClaim));
            return Collections.unmodifiableSet(scopes);
        } catch (Exception e) {
            return Set.of();
        }
    }
}
