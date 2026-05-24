package org.cafeng.openapi.engine;

import org.cafeng.openapi.scope.ScopeResolver;
import java.util.Set;
import java.util.Set;

/**
 * Guards DDL operations by requiring callers to have the 'ddl' scope.
 * DDL keywords: CREATE, ALTER, DROP, TRUNCATE, RENAME, GRANT, REVOKE.
 *
 * Uses the existing ScopeResolver to check caller permissions.
 * Configure scope-mapping with 'ddl' scope to authorize DDL callers:
 *   scope-mapping: "admin:basic+detail+sensitive+ddl"
 */
public class DdlGuard {

    static final String DDL_SCOPE = "ddl";

    private final ScopeResolver scopeResolver;

    public DdlGuard(ScopeResolver scopeResolver) {
        this.scopeResolver = scopeResolver;
    }

    /**
     * Checks if the SQL is a DDL operation and if so, verifies the caller has 'ddl' scope.
     * @param sql the SQL to check
     * @param callerId the caller identity (from X-Caller-Id header)
     * @throws DdlPermissionDeniedException if DDL detected without ddl scope
     */
    public void check(String sql, String callerId) {
        if (!SqlOperationUtils.isDdlOperation(sql)) return;

        Set<String> scopes = scopeResolver.resolveScopes(callerId);
        if (!scopes.contains(DDL_SCOPE)) {
            throw new DdlPermissionDeniedException(
                    "DDL operation requires '" + DDL_SCOPE + "' scope. Caller: " + callerId);
        }
    }

    public boolean isDdlOperation(String sql) {
        return SqlOperationUtils.isDdlOperation(sql);
    }

    public static class DdlPermissionDeniedException extends RuntimeException {
        public DdlPermissionDeniedException(String message) {
            super(message);
        }
    }
}
