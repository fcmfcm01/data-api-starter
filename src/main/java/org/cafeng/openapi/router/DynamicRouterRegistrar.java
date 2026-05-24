package org.cafeng.openapi.router;
import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.engine.*;
import org.cafeng.openapi.error.ErrorMessageSanitizer;
import org.cafeng.openapi.error.ValidationException;
import org.cafeng.openapi.handler.*;
import org.cafeng.openapi.param.RequestParameterMapper;
import org.cafeng.openapi.registry.ApiDefinitionRegistry;
import org.cafeng.openapi.scope.*;
import org.cafeng.openapi.sla.SlaMonitor;
import org.cafeng.openapi.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.*;

/**
 * Registers each validated {@link ApiDefinition} as a live Spring MVC route.
 *
 * <p>Creates an internal handler per API that orchestrates the full request pipeline:
 * authentication, rate limiting, parameter mapping, SQL injection and DDL checks,
 * query execution, scope-based field filtering, and SLA recording.</p>
 */
public class DynamicRouterRegistrar {
    private static final Logger log = LoggerFactory.getLogger(DynamicRouterRegistrar.class); private static final int MAX_BODY_SIZE = 1024 * 1024;
    private final RequestMappingHandlerMapping handlerMapping; private final Map<String, QueryEngine> queryEngines;
    private final RequestParameterMapper parameterMapper; private final AuthenticationProvider authProvider; private final RateLimiter rateLimiter;
    private final SlaMonitor slaMonitor; private final ObjectMapper objectMapper; private final ApiDefinitionRegistry apiRegistry;
    private final JdbcQueryHandler jdbcQueryHandler; private final HttpForwardHandler httpForwardHandler;

    public DynamicRouterRegistrar(RequestMappingHandlerMapping hm, List<QueryEngine> engines,
            RequestParameterMapper mapper, SlaMonitor sla, AuthenticationProvider auth, RateLimiter rl,
            ApiDefinitionRegistry registry, ObjectMapper om, JdbcQueryHandler jdbc, HttpForwardHandler http) {
        this.handlerMapping = hm; this.queryEngines = engines.stream().collect(Collectors.toMap(QueryEngine::getType, e -> e));
        this.parameterMapper = mapper; this.slaMonitor = sla; this.authProvider = auth; this.rateLimiter = rl;
        this.apiRegistry = registry; this.objectMapper = om; this.jdbcQueryHandler = jdbc; this.httpForwardHandler = http; }
    public DynamicRouterRegistrar(RequestMappingHandlerMapping hm, List<QueryEngine> engines,
            ConditionBuilder cb, PaginationBuilder pb, PageResponseBuilder prb, RequestParameterMapper mapper,
            ScopeFilter sf, ScopeResolver sr, SlaMonitor sla, SqlInjectionGuard sig, DdlGuard dg, ObjectMapper om) {
        this(hm, engines, mapper, sla, new NoOpAuthenticationProvider(), new RateLimiter(true), new ApiDefinitionRegistry(), om,
                new JdbcQueryHandler(cb, pb, prb, sig, dg, om, sf, sr, sla), new HttpForwardHandler(sig, om, sf, sr, sla)); }

    public void registerApi(ApiDefinition def) throws Exception {
        apiRegistry.register(def);
        handlerMapping.registerMapping(RequestMappingInfo.paths(def.path()).methods(RequestMethod.valueOf(def.method().toUpperCase())).build(),
                new DynamicApiHandler(def), DynamicApiHandler.class.getDeclaredMethod("handle", HttpServletRequest.class)); }
    public Set<String> getRegisteredApiIds() { return apiRegistry.getRegisteredApiIds(); }

    private class DynamicApiHandler {
        private final ApiDefinition apiDefinition;
        DynamicApiHandler(ApiDefinition def) { this.apiDefinition = def; }
        public ResponseEntity<String> handle(HttpServletRequest req) throws Exception {
            String id = apiDefinition.id(); slaMonitor.recordQuery(id); long start = System.currentTimeMillis();
            try {
                AuthResult ar = authProvider.authenticate(req);
                if (!ar.authenticated()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(Map.of("error", "Unauthorized", "message", "Authentication required")));
                Integer rl = apiDefinition.sla() != null ? apiDefinition.sla().rateLimit() : null;
                if (rl != null && rl > 0 && !rateLimiter.tryConsume(id, ar.callerId(), rl)) { long ra = rateLimiter.getRetryAfterSeconds(id, ar.callerId());
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON).header("Retry-After", String.valueOf(ra))
                            .body(objectMapper.writeValueAsString(Map.of("error", "Rate limit exceeded", "retryAfter", ra))); }
                Map<String,Object> bp = parseBody(req); Map<String,Object> sp = parameterMapper.mapParameters(apiDefinition, req, bp);
                RequestContext ctx = new RequestContext(apiDefinition, sp, ar, start);
                return "http".equals(apiDefinition.source().type()) ? httpForwardHandler.handle(ctx, req, queryEngines) : jdbcQueryHandler.handle(ctx, req, queryEngines);
            } catch (Exception e) { slaMonitor.recordError(id, e.getClass().getSimpleName()); log.debug("Error for {}: {}", id, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(Map.of("error", ErrorMessageSanitizer.sanitize(e.getMessage())))); }
        }
        private Map<String, Object> parseBody(HttpServletRequest req) {
            Map<String, Object> p = new HashMap<>();
            if (!"POST".equalsIgnoreCase(req.getMethod()) && !"PUT".equalsIgnoreCase(req.getMethod())) return p;
            try { String ct = req.getContentType();
                if (ct != null && ct.contains("application/json")) { byte[] b = req.getInputStream().readAllBytes();
                    if (b.length > MAX_BODY_SIZE) throw new ValidationException("Request body too large (max 1MB)");
                    if (b.length > 0) objectMapper.readValue(b, Map.class).forEach((k, v) -> p.put(String.valueOf(k), v)); }
            } catch (Exception e) { log.debug("Body parse fail for {}: {}", apiDefinition.id(), e.getMessage()); }
            return p;
        }
    }
}
