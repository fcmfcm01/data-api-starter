# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-SNAPSHOT] - 2025-05-24

### Added

- YAML-driven REST API definition with automatic Spring MVC route registration
- `source.type: jdbc` with parameterized queries via `PreparedStatement`
- `source.type: http` with configurable URL, method, headers, and timeout
- Conditional SQL syntax: `${param: SQL fragment}` for dynamic WHERE clauses
- Built-in pagination (`response.type: page`) with automatic OFFSET/FETCH and COUNT queries
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
