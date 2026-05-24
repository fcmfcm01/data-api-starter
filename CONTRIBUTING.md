# Contributing to data-api-starter

Thanks for your interest in contributing. This guide covers how to build, test, and submit changes.

## Prerequisites

- Java 17 or later
- Maven 3.9+
- Git

## Building

```bash
git clone <repo-url> && cd data-api-starter
mvn clean install
```

This compiles the code, runs all tests, and installs the artifact to your local Maven repository.

To skip tests during development iterations:

```bash
mvn clean install -DskipTests
```

## Running Tests

```bash
# All tests
mvn test

# A single test class
mvn test -Dtest=JdbcQueryEngineTest

# A single test method
mvn test -Dtest=JdbcQueryEngineTest#testBasicQuery
```

The project has around 290 tests. All must pass before submitting a PR.

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
src/main/java/org/cafeng/openapi/
  autoconfigure/   Spring Boot auto-configuration
  capability/      /capabilities endpoint
  datasource/      DataSource registry
  definition/      API definition records
  engine/          Query engines, SQL guards, condition/pagination builders
  error/           Exception handling and message sanitization
  handler/         Request handlers for JDBC and HTTP sources
  openapi/         OpenAPI spec generator
  param/           Request parameter mapping
  parser/          YAML discovery, parsing, and linting
  registry/        API definition registry
  router/          Dynamic route registration and conflict detection
  scope/           Scope resolution and field filtering
  security/        Authentication providers and rate limiter
  sla/             SLA monitoring with Micrometer

src/test/java/org/cafeng/openapi/
  (mirrors main structure)
```

## Making Changes

1. Create a branch from `main`:

   ```bash
   git checkout -b feature/your-feature-name
   ```

2. Make your changes. Add tests for any new behavior.

3. Run the full test suite:

   ```bash
   mvn test
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
- Ensure `mvn test` passes cleanly before pushing
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
