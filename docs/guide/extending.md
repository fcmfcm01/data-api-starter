# 扩展指南

data-api-starter 通过 Spring 的 `@ConditionalOnMissingBean` 机制提供多个扩展点。你可以替换默认实现或添加新的组件。

## 自定义 QueryEngine

实现 `QueryEngine` 接口以支持新的数据源类型（如 Elasticsearch、Redis、GraphQL）。

```java
@Component
public class ElasticsearchQueryEngine implements QueryEngine {

    @Override
    public String getType() {
        return "elasticsearch";
    }

    @Override
    public QueryResult execute(ApiDefinition api, String processedQuery,
            Map<String, Object> parameters) {
        // 使用 processedQuery 作为 Elasticsearch 查询模板
        // parameters 包含请求参数
        // 返回 QueryResult 包含数据行
        RestHighLevelClient client = ...;
        SearchResponse response = client.search(...);
        List<Map<String, Object>> rows = mapHitsToRows(response.getHits());
        return new QueryResult(rows, response.getHits().getTotalHits().value, 0);
    }

    @Override
    public long executeCount(ApiDefinition api, String sql,
            Map<String, Object> params) throws Exception {
        // 可选：用于分页查询的总数计算
        return 0;
    }
}
```

注册后，YAML 中可以使用 `source.type: elasticsearch`。

## 自定义 ScopeResolver

实现 `ScopeResolver` 接口以从自定义来源（数据库、LDAP、OAuth2 scopes）解析调用方权限。

```java
@Component
public class DatabaseScopeResolver implements ScopeResolver {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseScopeResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> resolveScopes(String callerId) {
        if (callerId == null || callerId.isBlank()) {
            return Set.of();
        }
        List<String> scopes = jdbcTemplate.queryForList(
            "SELECT scope FROM caller_scopes WHERE caller_id = ?",
            String.class, callerId);
        return Set.copyOf(scopes);
    }
}
```

由于 `DataApiAutoConfiguration` 中 `ScopeResolver` 使用了 `@ConditionalOnMissingBean`，注册自定义实现后默认的 `ConfigScopeResolver` 不会创建。如果需要组合使用，可以将多个 `ScopeResolver` 注入 `CompositeScopeResolver`。

## 自定义 AuthenticationProvider

实现 `AuthenticationProvider` 接口以集成 OAuth2、SAML 或自定义认证机制。

```java
@Component
public class OAuth2AuthenticationProvider implements AuthenticationProvider {

    private final OAuth2TokenValidator tokenValidator;

    public OAuth2AuthenticationProvider(OAuth2TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    public AuthResult authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return AuthResult.unauthenticated();
        }
        String token = authHeader.substring(7);
        try {
            OAuth2Authentication auth = tokenValidator.validate(token);
            Set<String> scopes = extractScopes(auth);
            return AuthResult.authenticated(auth.getPrincipal(), scopes);
        } catch (Exception e) {
            return AuthResult.denied("Invalid token: " + e.getMessage());
        }
    }
}
```

注册自定义 `AuthenticationProvider` bean 后，框架不再创建默认的认证提供者（`NoOpAuthenticationProvider`、`JwtAuthenticationProvider` 或 `ApiKeyAuthenticationProvider`），无论 `data-api.auth-type` 如何配置。

## 扩展 YAML 校验

`YamlLint` 是一个常规 bean。如果需要添加自定义校验规则，可以继承或包装它：

```java
@Component
public class CustomYamlLint extends YamlLint {

    @Override
    public List<String> lint(List<ApiDefinition> apis) {
        List<String> errors = super.lint(apis);
        // 添加自定义校验
        for (ApiDefinition api : apis) {
            if (api.source() != null && "jdbc".equals(api.source().type())) {
                if (api.source().query().contains("SELECT *")) {
                    errors.add("SELECT * is not allowed in " + api.id()
                        + ": specify explicit columns");
                }
            }
        }
        return errors;
    }
}
```

## 注意事项

- 所有扩展通过标准 Spring Bean 注册。无需修改 starter 代码
- 使用 `@ConditionalOnMissingBean` 的扩展点：`ScopeResolver`、`AuthenticationProvider`
- 其他组件（如 `YamlLint`、`QueryEngine`）可以共存多个实现
- 自定义 bean 会在自动配置之前被发现，确保优先级正确
