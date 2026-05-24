package org.cafeng.openapi.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.cafeng.openapi.scope.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Authenticates requests via JWT Bearer tokens.
 *
 * <p>Extracts the token from the {@code Authorization: Bearer ...} header,
 * verifies the HMAC256 signature, and reads the {@code scope} claim.
 * Selected when {@code data-api.auth-type} is {@code jwt}.</p>
 */
public class JwtAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationProvider.class);
    private final Algorithm algorithm;

    public JwtAuthenticationProvider(String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    @Override
    public AuthResult authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return AuthResult.unauthenticated();
        }
        String token = authHeader.substring(7);
        try {
            com.auth0.jwt.JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            String callerId = jwt.getSubject();
            String scopeClaim = jwt.getClaim("scope").asString();
            Set<String> scopes = parseScopes(scopeClaim);
            return AuthResult.authenticated(callerId != null ? callerId : "jwt-user", scopes);
        } catch (Exception e) {
            log.debug("JWT verification failed: {}", e.getMessage());
            return AuthResult.denied("Invalid or expired token");
        }
    }

    private Set<String> parseScopes(String scopeClaim) {
        return new LinkedHashSet<>(ScopeUtils.parseScopeString(scopeClaim));
    }
}
