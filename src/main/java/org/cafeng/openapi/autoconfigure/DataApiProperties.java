package org.cafeng.openapi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the data-api-starter, bound to the
 * {@code data-api} prefix.
 *
 * <p>Controls YAML scan path, global timeout, scope mapping, authentication
 * mode, JWT secret, API keys, rate limiting, and JDBC fetch size.</p>
 */
@ConfigurationProperties(prefix = "data-api")
public class DataApiProperties {

    private String apisPath = "classpath:apis/";
    private Integer globalTimeout = 5000;
    private Boolean capabilitiesEnabled = true;
    private String scopeMapping = "";
    private String authType = "none";
    private boolean strictScopes = false;
    private String jwtSecret = "";
    private String apiKeys = "";
    private boolean rateLimitEnabled = true;
    private int jdbcFetchSize = 100;

    public String getApisPath() {
        return apisPath;
    }

    public void setApisPath(String apisPath) {
        this.apisPath = apisPath;
    }

    public Integer getGlobalTimeout() {
        return globalTimeout;
    }

    public void setGlobalTimeout(Integer globalTimeout) {
        this.globalTimeout = globalTimeout;
    }

    public Boolean getCapabilitiesEnabled() {
        return capabilitiesEnabled;
    }

    public void setCapabilitiesEnabled(Boolean capabilitiesEnabled) {
        this.capabilitiesEnabled = capabilitiesEnabled;
    }

    public String getScopeMapping() {
        return scopeMapping;
    }

    public void setScopeMapping(String scopeMapping) {
        this.scopeMapping = scopeMapping;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public boolean isStrictScopes() {
        return strictScopes;
    }

    public void setStrictScopes(boolean strictScopes) {
        this.strictScopes = strictScopes;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(String apiKeys) {
        this.apiKeys = apiKeys;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public int getJdbcFetchSize() {
        return jdbcFetchSize;
    }

    public void setJdbcFetchSize(int jdbcFetchSize) {
        this.jdbcFetchSize = jdbcFetchSize;
    }
}
