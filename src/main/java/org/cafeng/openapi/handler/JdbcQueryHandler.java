package org.cafeng.openapi.handler;
import org.cafeng.openapi.engine.*;
import org.cafeng.openapi.error.DataApiException;
import org.cafeng.openapi.scope.*;
import org.cafeng.openapi.sla.SlaMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.cafeng.openapi.definition.ResponseType;

import java.util.*;

/**
 * Handles JDBC-sourced API requests end to end.
 *
 * <p>Builds conditional SQL, validates parameters against injection patterns,
 * checks DDL permissions, executes the query, applies scope filtering,
 * and formats the response (paginated, list, or single).</p>
 */
public class JdbcQueryHandler {
    private final ConditionBuilder conditionBuilder; private final PaginationBuilder paginationBuilder;
    private final PageResponseBuilder pageResponseBuilder; private final SqlInjectionGuard sqlInjectionGuard;
    private final DdlGuard ddlGuard; private final ObjectMapper objectMapper;
    private final ScopeFilter scopeFilter; private final ScopeResolver scopeResolver; private final SlaMonitor slaMonitor;

    public JdbcQueryHandler(ConditionBuilder cb, PaginationBuilder pb, PageResponseBuilder prb,
            SqlInjectionGuard sig, DdlGuard dg, ObjectMapper om, ScopeFilter sf, ScopeResolver sr, SlaMonitor sla) {
        this.conditionBuilder = cb; this.paginationBuilder = pb; this.pageResponseBuilder = prb;
        this.sqlInjectionGuard = sig; this.ddlGuard = dg; this.objectMapper = om;
        this.scopeFilter = sf; this.scopeResolver = sr; this.slaMonitor = sla; }

    public ResponseEntity<String> handle(RequestContext ctx, HttpServletRequest req,
            Map<String, QueryEngine> engines) throws Exception {
        String id = ctx.apiDefinition().id();
        try {
            ConditionBuilder.ConditionResult cr = conditionBuilder.build(ctx.apiDefinition().source().query(), ctx.sqlParams());
            sqlInjectionGuard.validate(cr.parameters()); ddlGuard.check(cr.sql(), ctx.authResult().callerId());
            QueryEngine engine = engines.get("jdbc");
            if (engine == null) throw new DataApiException("No JDBC query engine available");
            ResponseEntity<String> response = SqlOperationUtils.isWriteOperation(cr.sql())
                    ? handleWrite(id, ctx, engine, cr.sql(), cr.parameters())
                    : handleRead(id, ctx, req, engine, cr.sql(), cr.parameters());
            slaMonitor.recordSuccess(id);
            return response;
        } finally {
            slaMonitor.recordLatency(id, System.currentTimeMillis() - ctx.startTime());
        }
    }

    private ResponseEntity<String> handleWrite(String id, RequestContext ctx, QueryEngine engine,
            String sql, Map<String, Object> params) throws Exception {
        QueryResult r = engine.execute(ctx.apiDefinition(), sql, params);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(Map.of("affectedRows", r.affectedRows())));
    }

    private ResponseEntity<String> handleRead(String id, RequestContext ctx, HttpServletRequest req,
            QueryEngine engine, String sql, Map<String, Object> params) throws Exception {
        List<Map<String, Object>> results;
        String type = ctx.apiDefinition().response().type();
        if (ResponseType.PAGE.yamlValue().equals(type)) return handlePaginated(id, ctx, req, engine, sql, params);
        else if (ResponseType.SINGLE.yamlValue().equals(type)) {
            QueryResult r = engine.execute(ctx.apiDefinition(), sql, params);
            if (r.data().isEmpty()) { return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(Map.of("error", "Not found"))); }
            results = r.data();
        } else { results = engine.execute(ctx.apiDefinition(), sql, params).data(); }
        results = applyScopeFilter(ctx, results);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(results));
    }

    private ResponseEntity<String> handlePaginated(String id, RequestContext ctx, HttpServletRequest req,
            QueryEngine engine, String sql, Map<String, Object> params) throws Exception {
        int page = getIntParam(req, "page", 1), size = getIntParam(req, "size", 20);
        String dataSql = paginationBuilder.build(sql, page, size);
        PaginatedResult result;
        if (engine instanceof JdbcQueryEngine jdbcEngine) {
            result = jdbcEngine.executePaginated(ctx.apiDefinition(), dataSql, sql, params);
        } else {
            QueryResult data = engine.execute(ctx.apiDefinition(), dataSql, params);
            long total = engine.executeCount(ctx.apiDefinition(), sql, params);
            result = new PaginatedResult(data.data(), total);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(pageResponseBuilder.build(result.data(), result.total(), page, size)));
    }

    private List<Map<String, Object>> applyScopeFilter(RequestContext ctx, List<Map<String, Object>> results) {
        return scopeFilter.apply(results, scopeResolver.resolveScopes(ctx.authResult().callerId()), ctx.apiDefinition()); }

    private int getIntParam(HttpServletRequest req, String name, int def) {
        String v = req.getParameter(name);
        if (v != null && !v.isEmpty()) try { return Integer.parseInt(v); } catch (NumberFormatException e) { /* use default */ }
        return def; }
}
