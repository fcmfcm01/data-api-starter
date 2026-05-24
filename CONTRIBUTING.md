# Contributing to data-api-starter

Thanks for your interest in contributing. This guide covers how to build, test, and submit changes.

## Prerequisites

- Java 17 or later
- Maven 3.9+
- Git

## Building

```bash
git clone <repo-url> && cd data-api-starter
mvn clean install               # 构建所有模块（starter + examples + spikes）
# 或只构建 starter：
# mvn clean install -Pskip-examples -Pskip-spikes
```

This compiles the code, runs all tests, and installs the artifact to your local Maven repository.

To skip tests during development iterations:

```bash
mvn clean install -DskipTests
```

## Running Tests

```bash
# All tests (starter module)
mvn test -pl starter

# A single test class
mvn test -pl starter -Dtest=JdbcQueryEngineTest

# A single test method
mvn test -pl starter -Dtest=JdbcQueryEngineTest#testBasicQuery
```

The starter module has ~368 tests. All must pass before submitting a PR.

## Code Style

- Follow standard Java conventions (sun-style, not Google-style)
- Use 4-space indentation, no tabs
- Maximum line length: 120 characters
- Use `final` on fields that don't change after construction
- Prefer records for immutable data carriers
- Keep methods short and focused
- Javadoc is required on all public classes. Method-level Javadoc is optional unless the behavior is non-obvious

## Project Structure

```
data-api-starter/
├── pom.xml                    ← 父 POM（聚合模块）
├── starter/                   ← starter 模块
│   ├── pom.xml
│   └── src/
│       ├── main/java/org/cafeng/openapi/
│       │   autoconfigure/   Spring Boot auto-configuration
│       │   capability/      /capabilities endpoint
│       │   datasource/      DataSource registry
│       │   definition/      API definition records
│       │   engine/          Query engines, SQL guards, dialect, condition/pagination builders
│       │   error/           Exception handling and message sanitization
│       │   handler/         Request handlers for JDBC, R2DBC, and HTTP sources
│       │   openapi/         OpenAPI spec generator
│       │   param/           Request parameter mapping
│       │   parser/          YAML discovery, parsing, and linting
│       │   r2dbc/           ConnectionFactory registry for R2DBC
│       │   registry/        API definition registry
│       │   router/          Dynamic route registration and conflict detection
│       │   scope/           Scope resolution and field filtering
│       │   security/        Authentication providers and rate limiter
│       │   sla/             SLA monitoring with Micrometer
│       └── test/java/org/cafeng/openapi/
│           (mirrors main structure)
├── examples/                  ← 示例应用
│   ├── order-service/         JDBC + MSSQL 方言
│   ├── r2dbc-product-service/ R2DBC + MySQL 方言
│   └── restclient-proxy-service/ HTTP 转发
└── spikes/                    ← 技术验证项目
    ├── r2dbc-connection-factory-spike/
    ├── dialect-detection-spike/
    └── restclient-spike/
```

## Making Changes

1. Create a branch from `main`:

   ```bash
   git checkout -b feature/your-feature-name
   ```

2. Make your changes. Add tests for any new behavior.

3. Run the full test suite:

    ```bash
    mvn test -pl starter
    ```

4. Commit with a clear message:

   ```bash
   git commit -m "Add support for custom response transformers"
   ```

5. Push and open a pull request.

## Pull Request Guidelines

- One logical change per PR. Avoid mixing refactoring with new features
- Include tests that verify the new behavior or fix
- If changing public API signatures, update Javadoc and docs
- Ensure `mvn test -pl starter` passes cleanly before pushing
- If your change affects YAML configuration, update `docs/yaml-spec.md`

## Reporting Issues

When filing a bug report, include:

- Steps to reproduce
- Expected vs actual behavior
- Java version, Spring Boot version, and starter version
- Relevant YAML definitions and `application.yml` configuration
- Stack traces or error logs (sanitized of sensitive data)

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0, the same license as the project.
