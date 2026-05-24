# data-api-starter

## 项目简介

data-api-starter 是一个 Spring Boot Starter，让开发者通过 YAML 文件声明式定义 REST API。启动时框架自动解析 YAML、注册路由、生成 OpenAPI 文档，无需编写 Controller / Service / Repository 代码。

核心特性:

- YAML 驱动的 API 定义，一个文件对应一个端点
- 启动时自动注册 Spring MVC 路由，零 Controller 代码
- 自动生成 OpenAPI 3.0 spec，开箱即用 Swagger UI
- 参数化 SQL，支持 `${param: SQL片段}` 条件语法
- 多方言 SQL 分页：MSSQL (OFFSET/FETCH)、MySQL、PostgreSQL、H2 (LIMIT/OFFSET)，自动检测
- R2DBC 响应式数据源支持，通过 ConnectionFactory 自动配置
- Scope 字段裁剪，按权限等级过滤响应字段
- SQL 注入防护：参数化查询 + 输入校验双重保障
- DDL 权限管控，结构变更操作需要 `ddl` scope 授权
- 多数据源支持，每个 API 可指定不同 DataSource
- HTTP 转发支持，`source.type: http` 通过 RestClient 代理上游服务
- 认证支持：无认证、JWT (HMAC256)、API Key 三种模式
- 令牌桶速率限制，per-API per-caller 粒度
- `/capabilities` 端点自动汇总所有已注册 API
- Micrometer 指标采集，监控查询耗时与超时
- 可扩展设计：自定义 QueryEngine、ScopeResolver、AuthenticationProvider

## Quick Start

### 1. 安装

```bash
git clone <repo-url> && cd data-api-starter
mvn clean install -DskipTests        # 多模块构建（starter/ + examples/ + spikes/）
# 或只构建 starter/ 模块：
# mvn clean install -Pskip-examples -Pskip-spikes -DskipTests
```

### 2. 添加依赖

在项目的 `pom.xml` 中引入 starter:

```xml
<dependency>
    <groupId>org.cafeng</groupId>
    <artifactId>data-api-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 3. 配置数据源

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:orders;MODE=MSSQLServer
    driver-class-name: org.h2.Driver
    username: sa
    password:

data-api:
  scope-mapping: "internal:basic+detail+financial,admin:basic+detail+financial+ddl"
```

### 4. 编写 API 定义

在 `src/main/resources/apis/` 下创建 YAML 文件:

```yaml
api:
  id: query-orders
  name: Query Orders
  path: /v1/orders
  method: GET
  parameters:
    - name: status
      in: query
      type: string
      required: false
      description: Filter by order status
      enum:
        - ACTIVE
        - PENDING
        - CANCELLED
    - name: page
      in: query
      type: integer
      required: false
    - name: size
      in: query
      type: integer
      required: false
  source:
    type: jdbc
    datasource: dataSource
    query: "SELECT order_no, status, amount, customer_name FROM orders WHERE 1=1 ${status: AND status = :status}"
  response:
    type: page
    fields:
      - name: orderNo
        scope: basic
      - name: status
        scope: basic
      - name: amount
        scope: basic
      - name: customerName
        scope: detail
  scopes:
    order.read: basic
  sla:
    timeout: 3000
```

### 5. 启动

```bash
cd examples/order-service && mvn spring-boot:run
```

也可以试试其他示例:

```bash
cd examples/r2dbc-product-service && mvn spring-boot:run   # R2DBC + MySQL
cd examples/restclient-proxy-service && mvn spring-boot:run  # HTTP 转发
```

### 6. 验证

```bash
curl http://localhost:8080/v1/orders
```

Swagger UI 地址: `http://localhost:8080/swagger-ui.html`

## 架构概览

```
YAML → YamlDiscovery → YamlParser → YamlLint → DynamicRouterRegistrar → [JdbcQueryEngine | R2dbcQueryEngine | HttpQueryEngine]
```

**YamlDiscovery** 扫描 classpath 下 `apis/` 目录，发现所有 `.yaml` 文件。

**YamlParser** 将 YAML 内容解析为 `ApiDefinition` 结构化对象，包含参数、数据源、响应定义等。

**YamlLint** 在启动时执行校验，检查必填字段、SQL 注入风险、路径冲突和 ID 唯一性。

**DynamicRouterRegistrar** 将校验通过的 `ApiDefinition` 动态注册为 Spring MVC 路由。根据 `source.type`，请求被分发到三种引擎之一：`JdbcQueryEngine`（JDBC 阻塞查询）、`R2dbcQueryEngine`（R2DBC 响应式查询）或 `HttpQueryEngine`（RestClient HTTP 转发）。请求到达时经过四层安全防护：`SqlInjectionGuard` 校验输入参数，`DdlGuard` 拦截未授权的 DDL 操作，QueryEngine 通过参数化执行 SQL，最后 `ScopeFilter` 按权限裁剪响应字段。

## 示例项目

- [examples/order-service/](examples/order-service/) — JDBC + MSSQL 方言，完整的订单查询 API
- [examples/r2dbc-product-service/](examples/r2dbc-product-service/) — R2DBC + MySQL 方言，响应式数据源
- [examples/restclient-proxy-service/](examples/restclient-proxy-service/) — HTTP 转发，通过 RestClient 代理上游服务

## 文档链接

- [完整使用指南](docs/guide/getting-started.md)
- [YAML 字段参考](docs/yaml-spec.md)
- [监控指南](docs/guide/monitoring.md)
- [扩展指南](docs/guide/extending.md)
