package org.cafeng.openapi.autoconfigure;

import org.cafeng.openapi.capability.CapabilityEndpoint;
import org.cafeng.openapi.datasource.DataSourceRegistry;
import org.cafeng.openapi.engine.*;
import org.cafeng.openapi.error.DataApiExceptionHandler;
import org.cafeng.openapi.openapi.OpenApiGenerator;
import org.cafeng.openapi.parser.YamlDiscovery;
import org.cafeng.openapi.parser.YamlLint;
import org.cafeng.openapi.parser.YamlParser;
import org.cafeng.openapi.param.RequestParameterMapper;
import org.cafeng.openapi.registry.ApiDefinitionRegistry;
import org.cafeng.openapi.handler.JdbcQueryHandler;
import org.cafeng.openapi.handler.HttpForwardHandler;
import org.cafeng.openapi.router.DynamicRouterRegistrar;
import org.cafeng.openapi.router.PathConflictDetector;
import org.cafeng.openapi.scope.ScopeFilter;
import org.cafeng.openapi.scope.ConfigScopeResolver;
import org.cafeng.openapi.scope.ScopeResolver;
import org.cafeng.openapi.sla.SlaMonitor;
import org.cafeng.openapi.security.AuthenticationProvider;
import org.cafeng.openapi.security.AuthResult;
import org.cafeng.openapi.security.NoOpAuthenticationProvider;
import org.cafeng.openapi.security.JwtAuthenticationProvider;
import org.cafeng.openapi.security.ApiKeyAuthenticationProvider;
import org.cafeng.openapi.security.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(DataApiProperties.class)
/**
 * Spring Boot auto-configuration that assembles all data-api-starter beans.
 *
 * <p>Registers query engines, guards, scope resolvers, authentication providers,
 * route registrar, and the startup initializer. Uses
 * {@code @ConditionalOnMissingBean} on {@code ScopeResolver} and
 * {@code AuthenticationProvider} so applications can supply custom
 * implementations.</p>
 */
public class DataApiAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataApiAutoConfiguration.class);

    @Bean
    public DataSourceRegistry dataSourceRegistry(ApplicationContext applicationContext) {
        DataSourceRegistry registry = new DataSourceRegistry();
        Map<String, DataSource> beans = applicationContext.getBeansOfType(DataSource.class);
        if (beans.isEmpty()) {
            log.warn("No DataSource beans found in application context");
        }
        beans.forEach(registry::registerDataSource);
        return registry;
    }

    @Bean
    public ConditionBuilder conditionBuilder() {
        return new ConditionBuilder();
    }

    @Bean
    public PaginationBuilder paginationBuilder() {
        return new PaginationBuilder();
    }

    @Bean
    public PageResponseBuilder pageResponseBuilder() {
        return new PageResponseBuilder();
    }

    @Bean
    public JdbcQueryEngine jdbcQueryEngine(DataSourceRegistry dataSourceRegistry, DataApiProperties properties) {
        return new JdbcQueryEngine(dataSourceRegistry, properties.getJdbcFetchSize());
    }

    @Bean
    public HttpQueryEngine httpQueryEngine() {
        return new HttpQueryEngine();
    }

    @Bean
    public RequestParameterMapper requestParameterMapper(ObjectMapper objectMapper) {
        return new RequestParameterMapper(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ScopeResolver.class)
    public ScopeResolver scopeResolver(DataApiProperties properties) {
        return new ConfigScopeResolver(properties.getScopeMapping());
    }

    @Bean
    public ScopeFilter scopeFilter(DataApiProperties properties) {
        return new ScopeFilter(properties.isStrictScopes());
    }

    @Bean
    public SlaMonitor slaMonitor(MeterRegistry meterRegistry) {
        return new SlaMonitor(meterRegistry);
    }

    @Bean
    public ApiDefinitionRegistry apiDefinitionRegistry() {
        return new ApiDefinitionRegistry();
    }

    @Bean
    public OpenApiGenerator openApiGenerator(ApiDefinitionRegistry apiDefinitionRegistry) {
        return new OpenApiGenerator(apiDefinitionRegistry);
    }

    @Bean
    public SqlInjectionGuard sqlInjectionGuard() {
        return new SqlInjectionGuard();
    }

    @Bean
    public DdlGuard ddlGuard(ScopeResolver scopeResolver) {
        return new DdlGuard(scopeResolver);
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationProvider.class)
    public AuthenticationProvider authenticationProvider(DataApiProperties properties) {
        return switch (properties.getAuthType()) {
            case JWT -> new JwtAuthenticationProvider(properties.getJwtSecret());
            case APIKEY -> new ApiKeyAuthenticationProvider(properties.getApiKeys());
            case NONE -> new NoOpAuthenticationProvider();
        };
    }

    @Bean
    public RateLimiter rateLimiter(DataApiProperties properties) {
        return new RateLimiter(properties.isRateLimitEnabled());
    }

    @Bean
    public JdbcQueryHandler jdbcQueryHandler(ConditionBuilder conditionBuilder,
                                              PaginationBuilder paginationBuilder,
                                              PageResponseBuilder pageResponseBuilder,
                                              SqlInjectionGuard sqlInjectionGuard,
                                              DdlGuard ddlGuard,
                                              ObjectMapper objectMapper,
                                              ScopeFilter scopeFilter,
                                              ScopeResolver scopeResolver,
                                              SlaMonitor slaMonitor) {
        return new JdbcQueryHandler(conditionBuilder, paginationBuilder, pageResponseBuilder,
                sqlInjectionGuard, ddlGuard, objectMapper, scopeFilter, scopeResolver, slaMonitor);
    }

    @Bean
    public HttpForwardHandler httpForwardHandler(SqlInjectionGuard sqlInjectionGuard,
                                                  ObjectMapper objectMapper,
                                                  ScopeFilter scopeFilter,
                                                  ScopeResolver scopeResolver,
                                                  SlaMonitor slaMonitor) {
        return new HttpForwardHandler(sqlInjectionGuard, objectMapper, scopeFilter, scopeResolver, slaMonitor);
    }

    @Bean
    public DynamicRouterRegistrar dynamicRouterRegistrar(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            List<QueryEngine> queryEngines,
            RequestParameterMapper parameterMapper,
            SlaMonitor slaMonitor,
            AuthenticationProvider authProvider,
            RateLimiter rateLimiter,
            ApiDefinitionRegistry apiDefinitionRegistry,
            ObjectMapper objectMapper,
            JdbcQueryHandler jdbcQueryHandler,
            HttpForwardHandler httpForwardHandler) {
        return new DynamicRouterRegistrar(
                handlerMapping, queryEngines, parameterMapper, slaMonitor,
                authProvider, rateLimiter, apiDefinitionRegistry, objectMapper,
                jdbcQueryHandler, httpForwardHandler);
    }

    @Bean
    public YamlDiscovery yamlDiscovery(DataApiProperties properties) {
        return new YamlDiscovery(properties.getApisPath());
    }

    @Bean
    public YamlParser yamlParser() {
        return new YamlParser();
    }

    @Bean
    public YamlLint yamlLint() {
        return new YamlLint();
    }

    @Bean
    public PathConflictDetector pathConflictDetector(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        return new PathConflictDetector(handlerMapping);
    }

    @Bean
    public DataApiInitializer dataApiInitializer(
            DataApiProperties properties,
            DynamicRouterRegistrar routerRegistrar,
            OpenApiGenerator openApiGenerator,
            ApiDefinitionRegistry apiDefinitionRegistry,
            YamlDiscovery yamlDiscovery,
            YamlParser yamlParser,
            YamlLint yamlLint,
            PathConflictDetector pathConflictDetector) throws Exception {
        return new DataApiInitializer(properties, routerRegistrar, openApiGenerator,
                apiDefinitionRegistry, yamlDiscovery, yamlParser, yamlLint, pathConflictDetector);
    }
}
