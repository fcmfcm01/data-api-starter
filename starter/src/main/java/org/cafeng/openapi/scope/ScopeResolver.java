package org.cafeng.openapi.scope;

import java.util.Set;

/**
 * Resolves a caller identity to the set of scope tiers it has access to.
 *
 * <p>Implementations may derive scopes from configuration, JWT claims,
 * or any external source. The framework uses a composite that tries
 * each resolver in order and returns the first non-empty result.</p>
 */
public interface ScopeResolver {
    Set<String> resolveScopes(String callerId);
}
