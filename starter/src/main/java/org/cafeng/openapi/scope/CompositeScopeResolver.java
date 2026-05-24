package org.cafeng.openapi.scope;

import java.util.List;
import java.util.Set;

/**
 * Chains multiple {@link ScopeResolver} instances and returns the first non-empty result.
 *
 * <p>Used to combine configuration-based, JWT-based, and custom scope resolvers.
 * If no resolver produces scopes, returns an empty set.</p>
 */
public class CompositeScopeResolver implements ScopeResolver {

    private final List<ScopeResolver> resolvers;

    public CompositeScopeResolver(List<ScopeResolver> resolvers) {
        this.resolvers = resolvers != null ? resolvers : List.of();
    }

    @Override
    public Set<String> resolveScopes(String callerId) {
        if (callerId == null) {
            return Set.of();
        }
        for (ScopeResolver resolver : resolvers) {
            Set<String> scopes = resolver.resolveScopes(callerId);
            if (scopes != null && !scopes.isEmpty()) {
                return scopes;
            }
        }
        return Set.of();
    }
}
