# data-api-starter 使用指南

## 1. 概述

data-api-starter 是一个 Spring Boot Starter，让你通过 YAML 文件声明式定义 REST API。写一个 YAML 文件，启动后即可通过 HTTP 调用，无需编写 Controller、Service、Repository 代码。YAML 定义的 API 和手写 Java Controller 可以共存于同一个应用。

核心流程：

```
YAML 文件 → 解析为 ApiDefinition → YamlLint 启动校验 → 注册 Spring MVC 路由
                                                          ↓
HTTP 请求 → 参数映射 → 条件构建 → SQL 执行 → 字段裁剪 → JSON 响应
```

完整字段说明请参考 [YAML 字段参考](../yaml-spec.md)。

---

## 2. 安装与引入

前置条件：Java 17+、Spring Boot 3.2+、Maven 3.9+。

### 安装 starter

```bash
git clone <repo-url> && cd data-api-starter
mvn clean install -DskipTests
```

### 添加 Maven 依赖

在你的项目 `pom.xml` 中引入 starter：

```xml
<dependency>
    <groupId>org.cafeng</groupId>
    <artifactId>data-api-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

此外需要 `spring-boot-starter-web`、`spring-boot-starter-jdbc`、`spring-boot-starter-actuator` 和 `springdoc-openapi-starter-webmvc-ui:2.5.0`，具体版本参考 `examples/order-service/pom.xml`。

### 最小配置

在 `application.yml` 中配置数据源和 starter：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:orders;MODE=MSSQLServer
    driver-class-name: org.h2.Driver
    username: sa
    password:

data-api:
  scope-mapping: "internal:basic+detail+financial"

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

---

## 3. 第一个 API

在 `src/main/resources/apis/` 目录下创建 YAML 文件，一个文件定义一个 API 端点。

### 示例：订单列表查询

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

### 各字段说明

| 字段 | 说明 |
|------|------|
| `api.id` | API 唯一标识，全局不可重复，用于路由注册和日志追踪 |
| `api.path` | HTTP 路径，支持路径参数如 `/v1/orders/{orderNo}` |
| `api.method` | HTTP 方法：GET、POST、PUT、DELETE |
| `parameters` | 请求参数列表，定义参数名、来源（query/path/body）、类型和校验规则 |
| `source` | 数据源配置，`datasource` 对应 Spring 容器中的 Bean 名称，`query` 为 SQL 语句 |
| `response` | 响应定义，`type` 决定格式（page/list/single，默认 `list`），`fields` 定义字段和 scope |

### 验证

启动服务后调用 API：

```bash
curl http://localhost:8080/v1/orders
```

预期响应（分页格式）：

```json
{
  "content": [
    {"orderNo": "ORD001", "status": "ACTIVE", "amount": 100.00},
    {"orderNo": "ORD002", "status": "PENDING", "amount": 200.00}
  ],
  "totalElements": 2,
  "totalPages": 1,
  "page": 1,
  "size": 20
}
```

SQL 列名自动从 `snake_case` 转换为 `camelCase`，例如 `order_no` 变成 `orderNo`。

---

## 4. 条件查询

### 语法格式

条件语法用于在 SQL 中按需拼接查询条件：

```
${paramName: SQL片段}
```

当参数有值时，SQL 片段拼接到查询中；参数缺失、为 `null` 或空字符串时，片段被跳过。

### 参数行为速查

| 场景 | 行为 |
|------|------|
| 参数有值 | SQL 片段拼接，参数传入 |
| 参数缺失（未传） | 条件跳过 |
| 参数为空字符串 `""` | 等同于缺失，条件跳过 |
| 参数为 `null` | 等同于缺失，条件跳过 |
| 参数为 `"0"` | 视为有值，条件生效 |

完整的参数边界行为说明请参考 [YAML 字段参考](../yaml-spec.md)。

### 多条件组合

条件语法通常与 `WHERE 1=1` 搭配使用：

```yaml
query: "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status} ${customerName: AND customer_name = :customerName}"
```

传参 `{status: "ACTIVE", customerName: "张三"}` 时，生成 SQL：

```sql
SELECT * FROM orders WHERE 1=1 AND status = :status AND customer_name = :customerName
```

传参 `{status: ""}` 或不传参时，生成 SQL：

```sql
SELECT * FROM orders WHERE 1=1
```

### 安全保障

框架提供四层安全防护，确保 SQL 执行安全：

1. **输入校验（SqlInjectionGuard）**：运行时检测参数值中的 SQL 注入模式（注释、UNION 注入、布尔注入、时间盲注、文件操作、十六进制编码等），检测到攻击载荷立即拒绝请求

2. **DDL 权限管控（DdlGuard）**：检测 `CREATE`、`ALTER`、`DROP`、`TRUNCATE`、`RENAME`、`GRANT`、`REVOKE` 等结构变更操作，要求调用方拥有 `ddl` scope 才可执行。未授权的 DDL 请求返回 403

3. **参数化查询（PreparedStatement）**：所有 SQL 通过 `PreparedStatement` 执行。YAML 中的 `:paramName` 命名参数在运行时转换为 JDBC `?` 占位符，参数值通过 `setObject()` 绑定，不进行字符串拼接

4. **启动时校验（YamlLint）**：检测 YAML 中的 SQL 注入模式（字符串拼接）和 DDL 操作（发出警告），检测到注入风险时拒绝启动

---

## 5. 分页

### 配置方式

设置 `response.type: page` 即可启用分页。框架自动检测请求中的 `page` 和 `size` 参数，构建分页 SQL。

```yaml
response:
  type: page
```

### 分页参数

`page`（默认 1）和 `size`（默认 20）在 `parameters` 中声明即可。框架自动检测这两个参数名，构建 `OFFSET ... FETCH NEXT ... ROWS ONLY` 分页 SQL。

单页最大记录数限制为 1000（`MAX_PAGE_SIZE`），超出会返回 400 错误。

### 响应格式

```json
{
  "content": [
    {"orderNo": "ORD001", "status": "ACTIVE", "amount": 100.00},
    {"orderNo": "ORD002", "status": "PENDING", "amount": 200.00}
  ],
  "totalElements": 256,
  "totalPages": 13,
  "page": 1,
  "size": 20
}
```

| 字段 | 说明 |
|------|------|
| `content` | 当前页数据数组 |
| `totalElements` | 总记录数，由 COUNT 查询获得 |
| `totalPages` | 总页数，计算公式 `ceil(totalElements / size)` |
| `page` | 当前页码 |
| `size` | 每页大小 |

### COUNT 查询策略

框架自动生成分页总数查询：`SELECT COUNT(*) FROM (原始SQL去掉OFFSET/FETCH) AS _count`。`JdbcQueryEngine` 去掉分片子句，外包 COUNT，与原始查询共享参数。

---

## 6. Scope 字段过滤

Scope 机制控制不同调用方可见的字段范围，实现数据字段级别的访问控制。

### 工作原理

1. `response.fields` 中为每个字段声明 `scope` 层级
2. `scopes` 映射定义调用方拥有的 scope 对应哪个层级
3. 运行时 `ScopeFilter` 根据调用方 scope 裁剪响应字段

### 层级推算规则

字段按声明顺序构建层级链，索引越高权限越大：

```
basic (index 0) < detail (index 1) < financial (index 2)
```

拥有 `detail` 权限的调用方可以看到 `basic` 和 `detail` 层级的所有字段，但无法看到 `financial` 层级字段。

### 多层级示例

以下示例展示了三级 scope 设计：basic 暴露基础订单信息，detail 增加客户姓名，financial 增加身份证号等敏感字段。

```yaml
api:
  id: query-orders
  path: /v1/orders
  method: GET
  source:
    type: jdbc
    datasource: dataSource
    query: "SELECT order_no, status, amount, customer_name, id_card, bank_account FROM orders WHERE 1=1"
  response:
    type: list
    fields:
      - name: orderNo
        scope: basic
      - name: status
        scope: basic
      - name: amount
        scope: basic
      - name: customerName
        scope: detail
      - name: idCard
        scope: financial
        pii: true
      - name: bankAccount
        scope: financial
        pii: true
  scopes:
    order.read: basic
    order.detail: detail
    order.financial: financial
```

| 调用方 Scope | 对应层级 | 可见字段 |
|-------------|---------|---------|
| `order.read` | basic | `orderNo`、`status`、`amount` |
| `order.detail` | detail | `orderNo`、`status`、`amount`、`customerName` |
| `order.financial` | financial | `orderNo`、`status`、`amount`、`customerName`、`idCard`、`bankAccount` |
| 无 scope | 全部（排除 pii 标记） | `orderNo`、`status`、`amount`、`customerName` |

scope 到层级的映射通过 `data-api.scope-mapping` 配置（如 `"internal:basic+detail+financial"`）。

### 字段默认值

- 字段未声明 `scope` 时默认为 `basic`
- 字段标记 `pii: true` 表示包含个人敏感信息，供审计用途

---

## 7. 写操作

Phase 1 支持 INSERT、UPDATE、DELETE 写操作，通过 `source.query` 定义 SQL。

### 约束

- 写操作 `response.type` 固定为 `single`
- 不支持分页
- 不支持 ScopeFilter 字段裁剪
- 返回格式固定为 `{"affectedRows": N}`

### INSERT 示例

```yaml
api:
  id: create-order
  name: Create Order
  path: /v1/orders
  method: POST
  parameters:
    - name: orderNo
      in: body
      type: string
      required: true
      description: Order number
    - name: status
      in: body
      type: string
      required: true
      description: Order status
    - name: amount
      in: body
      type: number
      required: true
      description: Order amount
    - name: customerName
      in: body
      type: string
      required: true
      description: Customer name
  source:
    type: jdbc
    datasource: dataSource
    query: "INSERT INTO orders (order_no, status, amount, customer_name) VALUES (:orderNo, :status, :amount, :customerName)"
  response:
    type: single
  sla:
    timeout: 5000
```

调用：

```bash
curl -X POST http://localhost:8080/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"ORD003","status":"ACTIVE","amount":300.00,"customerName":"王五"}'
```

返回：`{"affectedRows": 1}`

### UPDATE 示例（路径参数 + body 混合）

```yaml
api:
  id: update-order-status
  path: /v1/orders/{orderNo}/status
  method: PUT
  parameters:
    - name: orderNo
      in: path
      type: string
      required: true
    - name: status
      in: body
      type: string
      required: true
      enum: [ACTIVE, PENDING, CANCELLED]
  source:
    type: jdbc
    datasource: dataSource
    query: "UPDATE orders SET status = :status WHERE order_no = :orderNo"
  response:
    type: single
```

调用：

```bash
curl -X PUT http://localhost:8080/v1/orders/ORD001/status \
  -H "Content-Type: application/json" \
  -d '{"status":"CANCELLED"}'
```

### DELETE 示例

```yaml
api:
  id: delete-order
  name: Delete Order
  path: /v1/orders/{orderNo}
  method: DELETE
  parameters:
    - name: orderNo
      in: path
      type: string
      required: true
  source:
    type: jdbc
    datasource: dataSource
    query: "DELETE FROM orders WHERE order_no = :orderNo"
  response:
    type: single
```

调用：`curl -X DELETE http://localhost:8080/v1/orders/ORD001`

返回：`{"affectedRows": 1}`

---

## 8. 多数据源

`DataSourceRegistry` 启动时自动扫描 Spring 容器中所有 `DataSource` Bean，按 Bean 名称建立映射。每个 API 的 `source.datasource` 指定要使用的数据源名称。

### 单数据源

默认 Bean 名称为 `dataSource`，YAML 中写 `datasource: dataSource`：

```yaml
source:
  datasource: dataSource
  query: "SELECT * FROM orders"
```

### 多数据源配置

示例项目只使用了单数据源。多数据源场景需要手动注册 `DataSource` Bean。以下是一个完整的 Java 配置示例：

```java
package com.example.orderservice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "mssql-order")
    @ConfigurationProperties("app.datasource.mssql-order")
    public DataSource mssqlOrderDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

对应的 `application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/main_db
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10

app:
  datasource:
    mssql-order:
      url: jdbc:sqlserver://localhost:1433;databaseName=orders
      username: ${MSSQL_ORDER_USER}
      password: ${MSSQL_ORDER_PASSWORD}
      hikari:
        maximum-pool-size: 5
```

在 YAML API 定义中，`source.datasource` 填写对应的数据源 Bean 名称。如果指定的名称不存在，请求时返回 500 错误。

---

## 9. 错误处理

### 统一错误格式

所有错误返回统一的 JSON 格式：

```json
{
  "status": 400,
  "error": "Bad request",
  "message": "具体错误描述",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| 字段 | 说明 |
|------|------|
| `status` | HTTP 状态码 |
| `error` | 错误类别描述 |
| `message` | 具体错误信息 |
| `requestId` | 请求唯一标识（UUID），用于日志追踪 |

### 常见错误码

| HTTP 状态码 | 触发条件 |
|------------|---------|
| 400 Bad Request | 必填参数缺失、参数类型不匹配、enum 校验失败、SQL 注入攻击载荷检测 |
| 403 Forbidden | DDL 操作未授权（调用方缺少 `ddl` scope） |
| 404 Not Found | 路径不匹配 |
| 500 Internal Server Error | 未预期的服务端异常（SQL 语法错误、数据源不可用） |
| 503 Service Unavailable | 查询超时 |

完整的错误码对照表和参数边界行为请参考 [YAML 字段参考](../yaml-spec.md)。

### 启动时校验错误

以下错误在应用启动时由 `YamlLint` 检测，导致启动失败：

- **api.id 重复**：多个 YAML 文件使用相同的 `id`
- **api.id / path / method 缺失**：必填字段为空或未定义
- **source / response 缺失**：必要对象未定义
- **路径冲突（YAML 间）**：多个 YAML 定义了相同的 `path + method` 组合
- **路径冲突（YAML vs Java）**：YAML 路径与 `@RequestMapping` 冲突，`PathConflictDetector` 检测
- **SQL 注入模式**：检测到不安全的 SQL 模式，拒绝启动
- **DDL 操作警告**：YAML 中包含 DDL 语句（CREATE/ALTER/DROP 等），启动时输出 WARN 日志提醒确认权限配置

---

## 10. OpenAPI 集成

### Maven 依赖

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```

启动服务后访问 Swagger UI：`http://localhost:8080/swagger-ui.html`

Swagger UI 自动展示所有 YAML 定义的 API，包含参数说明、enum 约束和响应格式。

### /capabilities 端点

`http://localhost:8080/capabilities` 返回所有已注册 API 清单，运维可快速了解服务暴露的数据能力。

### /actuator/env

Spring Boot Actuator 环境端点可用于查看配置，包括查询超时时间。超时优先级：YAML `sla.timeout` > `data-api.global-timeout` > 默认 5000ms。

---

## 11. 最佳实践

### SQL 编写

- 使用 `WHERE 1=1` 搭配条件语法，避免动态拼接 `WHERE` 或 `AND` 关键字
- SQL 列名使用 `snake_case`，框架自动转为 `camelCase` 返回
- 只查询需要的列，避免 `SELECT *`
- DDL 操作（CREATE/ALTER/DROP 等）需要调用方拥有 `ddl` scope，在 `scope-mapping` 中配置授权

### API 命名

- `api.id` 使用小写短横线，如 `query-orders`、`get-order-detail`
- `api.path` 遵循 RESTful 风格，资源用复数名词
- HTTP 方法语义：GET 查询、POST 创建、PUT 更新、DELETE 删除

### Scope 设计

- 按信息敏感度划分层级，通常三级：`basic`（基本信息）、`detail`（详细信息）、`financial`（财务/敏感信息）
- 标记 `pii: true` 供安全审计，高层级 scope 自动包含低层级字段

### SLA 配置

- 查询类 API 建议 timeout 3000-5000ms，写操作可放宽到 5000-10000ms
- 超时优先级：YAML `sla.timeout` > 全局 `data-api.global-timeout` > 默认值 5000ms

### YAML 文件组织

建议按功能模块分目录存放：

```
src/main/resources/apis/
├── order/
│   ├── query-orders.yaml
│   ├── get-order-detail.yaml
│   └── create-order.yaml
├── product/
│   └── query-products.yaml
└── report/
    └── monthly-summary.yaml
```

框架递归扫描 `apis/` 下所有 `.yaml` 文件（不支持 `.yml`），目录结构不影响路由注册。

---

## 12. 完整示例

示例项目位于 `examples/order-service/`，展示了 starter 的完整使用方式。

### 文件结构

```
examples/order-service/
├── pom.xml                                          ← 项目依赖配置
├── src/main/java/com/example/orderservice/
│   └── OrderServiceApplication.java                 ← Spring Boot 启动类
└── src/main/resources/
    ├── application.yml                              ← 数据源和 starter 配置
    ├── apis/
    │   ├── query-orders.yaml                        ← GET 分页查询
    │   ├── get-order-detail.yaml                    ← GET 单条查询（路径参数）
    │   └── create-order.yaml                        ← POST 写操作
    ├── schema.sql                                   ← 建表语句
    └── data.sql                                     ← 初始化数据
```

### 启动与验证

```bash
# 安装 starter 到本地仓库
cd data-api-starter && mvn clean install -DskipTests

# 启动示例服务
cd examples/order-service && mvn spring-boot:run

# 验证
curl http://localhost:8080/v1/orders                          # 分页查询
curl http://localhost:8080/v1/orders/ORD001                   # 单条查询
curl http://localhost:8080/swagger-ui.html                    # Swagger UI
curl http://localhost:8080/capabilities                       # 能力清单
```

---

## 13. 配置参考

### data-api 配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `data-api.apis-path` | string | `classpath:apis/` | YAML 文件扫描路径，支持子目录递归 |
| `data-api.global-timeout` | integer | `5000` | 全局查询超时时间（毫秒），YAML 中 `sla.timeout` 可覆盖 |
| `data-api.scope-mapping` | string | - | Scope 到层级的映射，格式：`"callerId:scope1+scope2+..."` |

示例：

```yaml
data-api:
  apis-path: classpath:apis/
  global-timeout: 5000
  scope-mapping: "internal:basic+detail+financial,admin:basic+detail+financial+ddl"
```

### Spring 数据源配置

使用 Spring Boot 标准配置即可。单数据源默认 Bean 名称为 `dataSource`，YAML 中 `source.datasource` 写 `"dataSource"`。多数据源参见 [第 8 节 多数据源](#8-多数据源)。
