package org.cafeng.openapi.handler;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.security.AuthResult;

import java.util.Map;

/**
 * Carries the state for a single API request through the handler pipeline.
 *
 * <p>Bundles the API definition, resolved SQL parameters, authentication result,
 * and the request start timestamp for latency tracking.</p>
 */
public record RequestContext(
    ApiDefinition apiDefinition,
    Map<String, Object> sqlParams,
    AuthResult authResult,
    long startTime
) {}
