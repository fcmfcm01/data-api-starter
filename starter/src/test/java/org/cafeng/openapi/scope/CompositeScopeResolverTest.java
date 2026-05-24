package org.cafeng.openapi.scope;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompositeScopeResolverTest {

    private static ScopeResolver stubResolver(Set<String> returnValue) {
        return callerId -> returnValue;
    }

    private static ScopeResolver stubResolver(String onlyFor, Set<String> returnValue) {
        return callerId -> onlyFor.equals(callerId) ? returnValue : Set.of();
    }

    @Test
    void shouldUseFirstNonEmptyResult() {
        ScopeResolver resolver1 = stubResolver(Set.of("basic", "detail"));
        ScopeResolver resolver2 = stubResolver(Set.of("other"));

        CompositeScopeResolver composite = new CompositeScopeResolver(List.of(resolver1, resolver2));
        Set<String> result = composite.resolveScopes("caller1");

        assertEquals(Set.of("basic", "detail"), result);
    }

    @Test
    void shouldFallbackToSecondWhenFirstIsEmpty() {
        ScopeResolver resolver1 = stubResolver(Set.of());
        ScopeResolver resolver2 = stubResolver(Set.of("basic", "detail"));

        CompositeScopeResolver composite = new CompositeScopeResolver(List.of(resolver1, resolver2));
        Set<String> result = composite.resolveScopes("caller1");

        assertEquals(Set.of("basic", "detail"), result);
    }

    @Test
    void shouldReturnEmptyWhenAllResolversReturnEmpty() {
        ScopeResolver resolver1 = stubResolver(Set.of());
        ScopeResolver resolver2 = stubResolver(Set.of());

        CompositeScopeResolver composite = new CompositeScopeResolver(List.of(resolver1, resolver2));
        Set<String> result = composite.resolveScopes("caller1");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForEmptyResolverList() {
        CompositeScopeResolver composite = new CompositeScopeResolver(List.of());
        Set<String> result = composite.resolveScopes("caller1");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullCallerId() {
        ScopeResolver resolver1 = stubResolver(Set.of("basic"));

        CompositeScopeResolver composite = new CompositeScopeResolver(List.of(resolver1));
        Set<String> result = composite.resolveScopes(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSelectFirstNonEmptyInChainOfThree() {
        ScopeResolver resolver1 = stubResolver("callerX", Set.of());
        ScopeResolver resolver2 = stubResolver("callerX", Set.of("detail"));
        ScopeResolver resolver3 = stubResolver(Set.of("sensitive"));

        CompositeScopeResolver composite = new CompositeScopeResolver(List.of(resolver1, resolver2, resolver3));
        Set<String> result = composite.resolveScopes("callerX");

        assertEquals(Set.of("detail"), result);
    }

    @Test
    void shouldBehaveLikeSingleResolver() {
        ScopeResolver resolver = stubResolver(Set.of("basic", "detail"));

        CompositeScopeResolver composite = new CompositeScopeResolver(List.of(resolver));
        Set<String> result = composite.resolveScopes("caller1");

        assertEquals(Set.of("basic", "detail"), result);
    }
}
