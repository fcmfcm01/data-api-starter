# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-SNAPSHOT] - 2025-05-24

### Added

- YAML-driven REST API definition with automatic Spring MVC route registration
- `source.type: jdbc` with parameterized queries via `PreparedStatement`
- `source.type: r2dbc` with reactive queries via `ConnectionFactory` auto-configuration
- `source.type: http` with configurable URL, method, headers, and timeout
- RestClient migration (replaces RestTemplate for HTTP forwarding)
- Conditional SQL syntax: `${param: SQL fragment}` for dynamic WHERE clauses
- Built-in pagination (`response.type: page`) with automatic dialect-aware SQL generation
- SqlDialect auto-detection from JDBC/R2DBC URLs (MSSQL, MySQL, PostgreSQL, H2)
- Dialect-aware pagination: MSSQL `OFFSET/FETCH` vs MySQL/PostgreSQL/H2 `LIMIT/OFFSET`
- Scope-based field filtering with hierarchical access control (basic, detail, financial)
- SQL injection protection via `SqlInjectionGuard` (8 regex patterns, defense-in-depth)
- DDL permission guard (`DdlGuard`) requiring `ddl` scope for schema-altering operations
- Authentication providers: `none` (default), `jwt` (HMAC256), `apikey` (header-based)
- Per-API rate limiting with token bucket algorithm
- SLA monitoring with Micrometer metrics (`dataapi.latency`, `dataapi.success`, `dataapi.error`, `dataapi.query`)
- Multi-datasource support via `DataSourceRegistry`
- OpenAPI 3.0 spec auto-generation with Swagger UI integration
- `/capabilities` endpoint for API inventory
- Startup-time YAML validation via `YamlLint` (required fields, ID uniqueness, path conflicts, injection detection)
- Path conflict detection against existing `@RequestMapping` handlers
- Error message sanitization to prevent schema leakage in API responses
- `RequestParameterMapper` with type coercion, enum validation, and required-field checks
- Spring Boot auto-configuration (`DataApiAutoConfiguration`) with `@ConditionalOnMissingBean` extension points
- Multi-module Maven project restructuring (parent POM + starter + examples + spikes)
- New examples: `r2dbc-product-service` (R2DBC + MySQL), `restclient-proxy-service` (HTTP forwarding)
- New spikes: `r2dbc-connection-factory-spike`, `dialect-detection-spike`, `restclient-spike`
- Maven profiles: `-Pskip-examples`, `-Pskip-spikes` for selective builds

### Changed

- Upgraded `order-service` example to Spring Boot 4.0.6 and springdoc 3.0.3
- Restructured to multi-module Maven layout (parent POM + starter + examples + spikes)
- Replaced RestTemplate with RestClient for HTTP forwarding
