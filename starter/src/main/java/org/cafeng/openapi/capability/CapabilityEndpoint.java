package org.cafeng.openapi.capability;

import org.cafeng.openapi.registry.ApiDefinitionRegistry;
import org.cafeng.openapi.definition.ApiDefinition;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * REST controller that exposes a summary of all registered APIs at
 * {@code GET /capabilities}.
 *
 * <p>Returns each API's ID, name, path, method, response fields with scope
 * levels, and PII flags. Intended for operational discovery and documentation.</p>
 */
@RestController
public class CapabilityEndpoint {

    private final ApiDefinitionRegistry apiDefinitionRegistry;

    public CapabilityEndpoint(ApiDefinitionRegistry apiDefinitionRegistry) {
        this.apiDefinitionRegistry = apiDefinitionRegistry;
    }

    @GetMapping(value = "/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> getCapabilities() {
        List<Map<String, Object>> capabilities = new ArrayList<>();
        
        for (ApiDefinition api : apiDefinitionRegistry.getAll()) {
            Map<String, Object> cap = new LinkedHashMap<>();
            cap.put("id", api.id());
            cap.put("name", api.name());
            cap.put("path", api.path());
            cap.put("method", api.method());
            
            List<Map<String, String>> fields = new ArrayList<>();
            if (api.response() != null && api.response().fields() != null) {
                for (var field : api.response().fields()) {
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put("name", field.name());
                    f.put("scope", field.scope());
                    if (field.pii()) {
                        f.put("pii", "true");
                    }
                    fields.add(f);
                }
            }
            cap.put("fields", fields);
            
            Map<String, String> scopes = new LinkedHashMap<>();
            if (api.scopes() != null) {
                scopes.putAll(api.scopes());
            }
            cap.put("scopes", scopes);
            
            capabilities.add(cap);
        }
        
        return capabilities;
    }
}
