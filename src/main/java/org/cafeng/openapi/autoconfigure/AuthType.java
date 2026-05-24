package org.cafeng.openapi.autoconfigure;

/**
 * Supported authentication modes for the data-api-starter.
 *
 * <p>Selected via {@code data-api.auth-type} property. Spring Boot
 * relaxed binding handles kebab-case values (e.g. {@code jwt}, {@code api-key}).</p>
 */
public enum AuthType {
    NONE, JWT, APIKEY
}
