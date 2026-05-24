# 监控指南

data-api-starter 通过 Micrometer 自动采集查询性能和错误指标。本文档说明如何配置监控、理解指标和使用健康检查。

## Micrometer 指标

框架注册四类指标，所有指标以 `dataapi.` 为前缀，并带有 `api` 标签标识具体 API：

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `dataapi.query` | Counter | `api` | 每个 API 的总请求计数 |
| `dataapi.latency` | Timer | `api` | 查询执行耗时（毫秒） |
| `dataapi.success` | Counter | `api` | 成功请求计数 |
| `dataapi.error` | Counter | `api`, `type` | 失败请求计数，`type` 标签为异常类名 |

### 前置依赖

确保项目中包含 Spring Boot Actuator 和 Micrometer：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Micrometer 会自动绑定到应用中配置的监控系统（Prometheus、Datadog、Influx 等）。

### Prometheus 集成示例

添加 Prometheus registry：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

在 `application.yml` 中暴露 Prometheus 端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health, info
```

常用 PromQL 查询：

```promql
# 平均查询延迟（按 API）
rate(dataapi_latency_seconds_sum[5m]) / rate(dataapi_latency_seconds_count[5m])

# 每秒错误率
rate(dataapi_error_total[5m])

# P99 延迟
histogram_quantile(0.99, rate(dataapi_latency_seconds_bucket[5m]))
```

## SLA 配置

每个 API 可以独立配置超时时间：

```yaml
api:
  id: query-orders
  sla:
    timeout: 3000    # 毫秒
    rateLimit: 100   # 每秒请求数
```

超时优先级：YAML `sla.timeout` > 全局 `data-api.global-timeout` > 默认 5000ms。

## 健康检查

框架不注册独立的健康指示器。使用 Spring Boot Actuator 的标准健康端点：

```bash
curl http://localhost:8080/actuator/health
```

如果需要监控 API 可用性，可以通过 Prometheus 告警规则基于 `dataapi.error` 和 `dataapi.latency` 指标触发。

## R2DBC 监控

R2DBC 查询（`source.type: r2dbc`）与 JDBC 查询共享相同的指标体系。`R2dbcQueryEngine` 内部使用同一个 `SlaMonitor` 进行指标采集，因此上述四类指标（`dataapi.query`、`dataapi.latency`、`dataapi.success`、`dataapi.error`）同时覆盖两种引擎，无需额外配置。

### R2DBC 日志

在日志配置中添加 `org.cafeng.openapi.r2dbc` 包以查看 R2DBC 特有的日志输出（连接获取、参数绑定等）：

```yaml
logging:
  level:
    org.cafeng.openapi.r2dbc: DEBUG
```

R2DBC 连接获取过程在 DEBUG 级别记录。所有引擎的通用日志（查询执行、SLA 超时等）仍然通过 `org.cafeng.openapi.engine` 包输出。

## 日志

所有 API 请求在 DEBUG 级别记录。在 `application.yml` 中调整日志级别：

```yaml
logging:
  level:
    org.cafeng.openapi: DEBUG
```

启动时的 API 注册、Lint 错误和路径冲突在 INFO/WARN 级别输出，无需额外配置。
