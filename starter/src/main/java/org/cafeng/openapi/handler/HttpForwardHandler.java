package org.cafeng.openapi.handler;

import org.cafeng.openapi.error.DataApiException;
import org.cafeng.openapi.definition.ResponseType;
import org.cafeng.openapi.engine.QueryEngine;
import org.cafeng.openapi.engine.QueryResult;
import org.cafeng.openapi.engine.SqlInjectionGuard;
import org.cafeng.openapi.scope.ScopeFilter;
import org.cafeng.openapi.scope.ScopeResolver;
import org.cafeng.openapi.sla.SlaMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles HTTP-sourced API requests by forwarding to an upstream service.
 *
 * <p>Validates parameters against injection patterns, delegates to
 * {@code HttpQueryEngine}, applies scope filtering, and formats the response.</p>
 */
public class HttpForwardHandler {

    private final SqlInjectionGuard sqlInjectionGuard;
    private final ObjectMapper objectMapper;
    private final ScopeFilter scopeFilter;
    private final ScopeResolver scopeResolver;
    private final SlaMonitor slaMonitor;

    public HttpForwardHandler(SqlInjectionGuard sqlInjectionGuard,
                              ObjectMapper objectMapper,
                              ScopeFilter scopeFilter,
                              ScopeResolver scopeResolver,
                              SlaMonitor slaMonitor) {
        this.sqlInjectionGuard = sqlInjectionGuard;
        this.objectMapper = objectMapper;
        this.scopeFilter = scopeFilter;
        this.scopeResolver = scopeResolver;
        this.slaMonitor = slaMonitor;
    }

    public ResponseEntity<String> handle(RequestContext ctx, HttpServletRequest request,
                                          Map<String, QueryEngine> engines) throws Exception {
        String apiId = ctx.apiDefinition().id();

        try {
            sqlInjectionGuard.validate(ctx.sqlParams());

            QueryEngine engine = engines.get("http");
            if (engine == null) {
                throw new DataApiException("No HTTP query engine available");
            }

            QueryResult result = engine.execute(ctx.apiDefinition(), null, ctx.sqlParams());
            List<Map<String, Object>> data = new ArrayList<>(result.data());

            String callerId = ctx.authResult().callerId();
            Set<String> scopes = scopeResolver.resolveScopes(callerId);
            data = scopeFilter.apply(data, scopes, ctx.apiDefinition());

            String json;
            if (data.size() == 1 && ctx.apiDefinition().response() != null
                    && ResponseType.SINGLE.yamlValue().equals(ctx.apiDefinition().response().type())) {
                json = objectMapper.writeValueAsString(data.get(0));
            } else {
                json = objectMapper.writeValueAsString(data);
            }

            slaMonitor.recordSuccess(apiId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } finally {
            slaMonitor.recordLatency(apiId, System.currentTimeMillis() - ctx.startTime());
        }
    }
}
