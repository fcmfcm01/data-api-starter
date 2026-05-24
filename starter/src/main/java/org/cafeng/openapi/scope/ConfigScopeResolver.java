package org.cafeng.openapi.scope;

import java.util.*;

/**
 * Resolves scopes from a static configuration string.
 *
 * <p>Parses the {@code data-api.scope-mapping} property format
 * {@code "callerId:scope1+scope2+...,callerId2:scope3"} into an in-memory
 * map. Returns an empty set for unrecognized callers.</p>
 */
public class ConfigScopeResolver implements ScopeResolver {
    private final Map<String, Set<String>> scopeMapping = new HashMap<>();

    public ConfigScopeResolver(String mappingConfig) {
        parseMappingConfig(mappingConfig);
    }

    private void parseMappingConfig(String config) {
        if (config == null || "EMPTY".equals(config) || config.isBlank()) {
            return;
        }

        for (String entry : config.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length == 2) {
                String caller = parts[0].trim();
                Set<String> scopes = new HashSet<>(Arrays.asList(parts[1].trim().split("\\+")));
                scopeMapping.put(caller, scopes);
            }
        }
    }

    @Override
    public Set<String> resolveScopes(String callerId) {
        return scopeMapping.getOrDefault(callerId, Set.of());
    }

    public void registerScopeMapping(String callerId, Set<String> scopes) {
        scopeMapping.put(callerId, scopes);
    }
}
