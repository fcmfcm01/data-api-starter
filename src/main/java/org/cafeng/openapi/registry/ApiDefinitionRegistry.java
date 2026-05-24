package org.cafeng.openapi.registry;

import org.cafeng.openapi.definition.ApiDefinition;

import java.util.*;

/**
 * Central registry of all parsed and validated API definitions.
 *
 * <p>Populated during startup by {@code DataApiInitializer}. Serves as the
 * data source for the OpenAPI generator, the capabilities endpoint,
 * and any component that needs to look up an API by its ID.</p>
 */
public class ApiDefinitionRegistry {

    private final Map<String, ApiDefinition> apis = new LinkedHashMap<>();

    public void register(ApiDefinition api) {
        apis.put(api.id(), api);
    }

    public ApiDefinition get(String id) {
        return apis.get(id);
    }

    public Collection<ApiDefinition> getAll() {
        return Collections.unmodifiableCollection(apis.values());
    }

    public Set<String> getRegisteredApiIds() {
        return Collections.unmodifiableSet(apis.keySet());
    }
}
