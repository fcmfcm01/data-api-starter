package org.cafeng.openapi.definition;

/**
 * Service-level agreement configuration for an API endpoint.
 *
 * <p>Defines per-API timeout (in milliseconds, defaults to 5000) and an optional
 * rate limit. Timeout precedence: YAML {@code sla.timeout} &gt;
 * global {@code data-api.global-timeout} &gt; default 5000ms.</p>
 */
public record ApiSla(
    Integer timeout,
    Integer rateLimit
) {
    public ApiSla {
        timeout = timeout != null ? timeout : 5000;
    }
}
