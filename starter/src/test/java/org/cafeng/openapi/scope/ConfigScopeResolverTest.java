package org.cafeng.openapi.scope;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigScopeResolverTest {

    @Test
    void shouldResolveScopesFromConfig() {
        ConfigScopeResolver resolver = new ConfigScopeResolver("caller1:basic+detail,caller2:sensitive");

        assertEquals(Set.of("basic", "detail"), resolver.resolveScopes("caller1"));
        assertEquals(Set.of("sensitive"), resolver.resolveScopes("caller2"));
    }

    @Test
    void shouldReturnEmptyForUnknownCaller() {
        ConfigScopeResolver resolver = new ConfigScopeResolver("caller1:basic");

        assertTrue(resolver.resolveScopes("unknown").isEmpty());
    }

    @Test
    void shouldHandleEmptyConfig() {
        ConfigScopeResolver resolver = new ConfigScopeResolver("");

        assertTrue(resolver.resolveScopes("anyone").isEmpty());
    }

    @Test
    void shouldSupportDynamicRegistration() {
        ConfigScopeResolver resolver = new ConfigScopeResolver("");

        resolver.registerScopeMapping("admin", Set.of("basic", "detail", "sensitive"));

        assertEquals(Set.of("basic", "detail", "sensitive"), resolver.resolveScopes("admin"));
    }
}
