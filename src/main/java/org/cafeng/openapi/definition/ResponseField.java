package org.cafeng.openapi.definition;

/**
 * A single field in an API response, with scope-level access control.
 *
 * <p>Each field declares a {@code scope} tier (defaults to {@code "basic"}) and
 * an optional {@code pii} flag for audit purposes. The {@link ScopeFilter} uses
 * these declarations to strip fields the caller is not authorized to see.</p>
 */
public record ResponseField(
    String name,
    String scope,
    boolean pii,
    String description
) {
    public ResponseField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field.name is required");
        }
        scope = scope != null ? scope : "basic";
        pii = pii;
    }
}
