# YAML API 定义规范

## 概述

data-api-starter 是一个 Spring Boot Starter，允许开发者通过 YAML 文件声明式定义 REST API。启动时框架自动解析 YAML 文件、注册路由、生成 OpenAPI 文档，无需编写 Controller / Service / Repository 代码。

核心流程：

```
YAML 文件 (classpath:apis/*.yaml)
  → YamlDiscovery 扫描发现
  → YamlParser 解析为 ApiDefinition
  → YamlLint 启动校验（必填字段、注入检测、路径冲突、ID 唯一性）
  → DynamicRouterRegistrar 注册 Spring MVC 路由
  → HTTP 请求到达时执行参数化查询并返回结果
```

YAML 文件放置于 `classpath:apis/` 目录下（含子目录），仅支持 `.yaml` 后缀。每个 YAML 文件定义一个 API 端点。

---

## 完整字段说明

### 顶层结构

```yaml
api:
  id:          # (必填) API 唯一标识，全局不可重复
  name:        # (可选) API 显示名称，用于 OpenAPI 文档
  path:        # (必填) HTTP 路径，支持路径参数，如 /v1/orders/{orderNo}
  method:      # (必填) HTTP 方法：GET / POST / PUT / DELETE
  parameters:  # (可选) 参数列表，见下方详细说明
  source:      # (必填) 数据源定义
  response:    # (必填) 响应定义
  scopes:      # (可选) Scope 映射
  sla:         # (可选) SLA 配置
```

### 字段明细表

| 字段 | 层级 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| `id` | api | string | ✅ | - | API 唯一标识，全局不可重复。用于路由注册和日志追踪 |
| `name` | api | string | ❌ | - | API 显示名称，展示在 Swagger UI 和 /capabilities 端点 |
| `path` | api | string | ✅ | - | HTTP 路径，支持路径参数 `{paramName}` |
| `method` | api | string | ✅ | - | HTTP 方法：GET、POST、PUT、DELETE |
| `parameters` | api | array | ❌ | `[]` | 请求参数列表 |
| `source` | api | object | ✅ | - | 数据源定义，见 source 章节 |
| `response` | api | object | ✅ | - | 响应定义，见 response 章节 |
| `scopes` | api | map | ❌ | `{}` | Scope → 层级映射，见 scope 章节 |
| `sla` | api | object | ❌ | - | SLA 配置，见 sla 章节 |

### parameters[] 字段明细

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `name` | string | ✅ | - | 参数名，对应 SQL 中的 `:paramName` 占位符 |
| `in` | string | ❌ | `query` | 参数位置：`query`（URL 参数）、`path`（路径参数）、`body`（请求体） |
| `type` | string | ❌ | `string` | 参数类型：`string`、`integer`、`number`、`boolean` |
| `required` | boolean | ❌ | `false` | 是否必填。必填参数缺失时返回 400 |
| `description` | string | ❌ | - | 参数描述，展示在 OpenAPI 文档 |
| `enum` | array | ❌ | - | 允许值列表。传入非列表内的值时返回 400 |
| `defaultValue` | any | ❌ | - | 参数默认值，未传时使用 |
| `maxLength` | integer | ❌ | - | 字符串最大长度校验 |
| `minValue` | integer | ❌ | - | 数值最小值校验 |
| `maxValue` | integer | ❌ | - | 数值最大值校验 |

### source 字段明细

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `type` | string | ❌ | `jdbc` | 数据源类型，支持 `jdbc`、`r2dbc`、`http` |
| `datasource` | string | ✅ | - | 数据源名称。JDBC 对应 Spring 容器中的 DataSource Bean 名称（单数据源默认 `dataSource`）；R2DBC 对应 ConnectionFactory Bean 名称（单数据源默认 `connectionFactory`） |
| `query` | string | ✅ | - | SQL 查询语句。支持 `:paramName` 参数占位符和 `${param: SQL片段}` 条件语法 |

### response 字段明细

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `type` | string | ❌ | `list` | 响应类型：`page`、`list`、`single`，详见 response.type 章节 |
| `fields` | array | ❌ | `[]` | 响应字段定义列表，用于 scope 裁剪 |

### response.fields[] 字段明细

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `name` | string | ✅ | - | 字段名（camelCase）。对应 SQL 列名自动从 snake_case 转换 |
| `scope` | string | ❌ | `basic` | 字段可见层级，如 `basic`、`detail`、`financial` |
| `pii` | boolean | ❌ | `false` | 是否为个人敏感信息字段 |
| `description` | string | ❌ | - | 字段描述 |

### sla 字段明细

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `timeout` | integer | ❌ | `5000` | 查询超时时间（毫秒）。优先级：YAML `sla.timeout` > 全局 `data-api.global-timeout` > 默认 5000ms |
| `rateLimit` | integer | ❌ | - | 预留字段，当前未实现 |

---

## 完整 YAML 示例

### GET 分页查询

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

### GET 单条查询（路径参数）

```yaml
api:
  id: get-order-detail
  name: Get Order Detail
  path: /v1/orders/{orderNo}
  method: GET
  parameters:
    - name: orderNo
      in: path
      type: string
      required: true
      description: Order number
  source:
    type: jdbc
    datasource: dataSource
    query: "SELECT order_no, status, amount, customer_name FROM orders WHERE order_no = :orderNo"
  response:
    type: single
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

### POST 写操作

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

---

## 条件语法说明

### 语法格式

```
${paramName: SQL片段}
```

条件语法用于在 SQL 中按需拼接查询条件。当参数有值时，SQL 片段拼接到查询中；当参数缺失、为 `null` 或空字符串时，SQL 片段被跳过。

### 解析规则

| 场景 | 参数值 | 结果 |
|------|--------|------|
| 参数有值 | `status = "ACTIVE"` | SQL 片段拼接，参数传入 |
| 参数为 `null` | `status = null` | SQL 片段跳过 |
| 参数为空字符串 | `status = ""` | SQL 片段跳过 |
| 参数为 `"0"` | `status = "0"` | 视为有值，SQL 片段拼接 |

### 使用模式

条件语法通常与 `WHERE 1=1` 搭配使用，构建动态查询：

```yaml
query: "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status} ${customerName: AND customer_name = :customerName}"
```

**传参 `{status: "ACTIVE"}` 时：**
```sql
SELECT * FROM orders WHERE 1=1 AND status = :status
-- 参数: {status: "ACTIVE"}
```

**传参 `{status: "ACTIVE", customerName: "张三"}` 时：**
```sql
SELECT * FROM orders WHERE 1=1 AND status = :status AND customer_name = :customerName
-- 参数: {status: "ACTIVE", customerName: "张三"}
```

**不传参或传参 `{status: ""}` 时：**
```sql
SELECT * FROM orders WHERE 1=1
-- 参数: {}
```

### 安全保障

- **输入校验**：`SqlInjectionGuard` 在运行时检测参数值中的 SQL 注入模式（注释、UNION、布尔注入、时间盲注等），检测到攻击载荷立即拒绝请求
- **DDL 权限管控**：`DdlGuard` 检测结构变更操作（CREATE/ALTER/DROP/TRUNCATE/RENAME/GRANT/REVOKE），要求调用方拥有 `ddl` scope，未授权返回 403
- **参数化查询**：所有 SQL 通过 `PreparedStatement` 执行。YAML 中的 `:paramName` 命名参数在运行时转换为 JDBC `?` 占位符，参数值通过 `setObject()` 绑定，运行时值不进行字符串拼接
- **启动时校验**：`YamlLint` 在启动时校验 SQL 中的注入模式（检测到风险时拒绝启动）和 DDL 操作（输出警告日志），条件语法仅支持 `${paramName: SQL片段}` 一种模式

---

## Scope 层级说明

Scope 机制控制不同调用方可见的字段范围，实现数据字段级别的访问控制。

### 工作原理

1. `response.fields` 中为每个字段声明 `scope` 层级（如 `basic`、`detail`、`financial`）
2. `scopes` 映射定义调用方拥有的 scope 对应哪个层级
3. 运行时 `ScopeFilter` 根据调用方 scope 裁剪响应字段

### 层级推算规则

字段按声明顺序构建层级链，索引越高权限越大：

```
basic (index 0) < detail (index 1) < financial (index 2)
```

拥有 `detail` 权限的调用方可以看到 `basic` + `detail` 层级的所有字段，但无法看到 `financial` 层级字段。

### 示例

```yaml
response:
  fields:
    - name: orderNo       # scope: basic
    - name: status        # scope: basic
    - name: amount        # scope: basic
    - name: customerName  # scope: detail
    - name: idCard        # scope: financial
scopes:
  order.read: basic       # 拥有 order.read scope 的调用方只看到 basic 字段
```

| 调用方 Scope | 可见字段 |
|-------------|---------|
| `order.read`（对应 `basic`） | `orderNo`、`status`、`amount` |
| `order.detail`（对应 `detail`） | `orderNo`、`status`、`amount`、`customerName` |
| `order.financial`（对应 `financial`） | `orderNo`、`status`、`amount`、`customerName`、`idCard` |
| 无 scope | 排除 `sensitive` 标记字段外的所有字段 |

### Scope 字段默认值

- 字段未声明 `scope` 时默认为 `basic`
- 字段标记为 `pii: true` 表示包含个人敏感信息，供审计用途

---

## response.type 说明

`response.type` 决定 API 的响应格式和查询策略。

| type | 说明 | 响应格式 | 是否执行 COUNT 查询 | 适用场景 |
|------|------|----------|-------------------|---------|
| `page` | 分页列表 | `{content, totalElements, totalPages, page, size}` | ✅ 需要 | 列表查询，支持分页 |
| `list` | 非分页列表 | `[...]` 直接数组 | ❌ 不需要 | 全量列表，不分页 |
| `single` | 单个对象 | `{...}` 直接对象 | ❌ 不需要 | 详情查询、写操作 |

### page 响应格式

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

- `content`：当前页数据数组
- `totalElements`：总记录数，由 COUNT 查询获得
- `totalPages`：总页数，计算公式为 `ceil(totalElements / size)`
- `page`：当前页码（从 1 开始）
- `size`：每页大小

COUNT 查询策略：框架根据 SqlDialect 移除对应的分片子句（`OFFSET ... FETCH` 或 `LIMIT ... OFFSET`），生成 `SELECT COUNT(*) FROM (原始SQL去掉分片子句) AS _count`

### list 响应格式

```json
[
  {"orderNo": "ORD001", "status": "ACTIVE"},
  {"orderNo": "ORD002", "status": "PENDING"}
]
```

### single 响应格式

```json
{"orderNo": "ORD001", "status": "ACTIVE", "amount": 100.00, "customerName": "张三"}
```

---

## 写操作说明

写操作通过 `source.query` 定义 INSERT / UPDATE / DELETE 语句，参数来源为请求体（`in: body`）。

### 约束

- `response.type` 固定为 `single`
- 写操作不支持分页
- 写操作不支持 ScopeFilter 字段裁剪
- `response.fields` 可以省略

### 返回格式

```json
{"affectedRows": 1}
```

### 请求体处理

POST / PUT 请求的 `parameters` 中 `in: body` 的参数从 JSON 请求体中提取：

```json
// POST /v1/orders
// Content-Type: application/json
{
  "orderNo": "ORD001",
  "status": "ACTIVE",
  "amount": 100.00,
  "customerName": "张三"
}
```

### UPDATE 示例

```yaml
api:
  id: update-order-status
  name: Update Order Status
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
      enum:
        - ACTIVE
        - PENDING
        - CANCELLED
  source:
    type: jdbc
    datasource: dataSource
    query: "UPDATE orders SET status = :status WHERE order_no = :orderNo"
  response:
    type: single
  sla:
    timeout: 5000
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
  sla:
    timeout: 5000
```

---

## 参数边界行为

| 场景 | 行为 | 示例 |
|------|------|------|
| 参数缺失（未传） | `${param: ...}` 条件跳过 | `GET /v1/orders` → 不拼接 status 条件 |
| 参数为空字符串 `""` | 等同于缺失，条件跳过 | `GET /v1/orders?status=` → 不拼接 status 条件 |
| 参数为 `null` | 等同于缺失，条件跳过 | JSON body 中字段值为 null → 不拼接 |
| 参数为 `"0"` | 视为有值，条件生效 | `GET /v1/orders?status=0` → 拼接 status 条件 |
| SQL NULL 在结果中 | JSON 保留为 `null`，不移除字段 | `{"orderNo": "ORD001", "customerName": null}` |
| 必填参数缺失 | 返回 400 Bad Request | `POST /v1/orders` 缺少 `orderNo` → 400 |
| enum 校验失败 | 返回 400 Bad Request | `status=INVALID` 不在枚举列表中 → 400 |
| 类型转换失败 | 返回 400 Bad Request | `page=abc` 无法转为 integer → 400 |
| 路径参数缺失 | 返回 404 Not Found | Spring MVC 标准行为 |

---

## 错误处理

### 错误响应格式

所有错误返回统一 JSON 格式：

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

### 错误码对照表

| HTTP 状态码 | 触发条件 |
|------------|---------|
| **400 Bad Request** | 必填参数缺失、参数类型不匹配、enum 校验失败、参数值超出范围 |
| **404 Not Found** | 路径不匹配（Spring MVC 标准行为） |
| **500 Internal Server Error** | 未预期的服务端异常（SQL 语法错误、数据源不可用等） |
| **503 Service Unavailable** | 查询超时（SQLTimeoutException） |

### 启动时校验错误

以下错误在应用启动时检测，导致启动失败：

| 校验项 | 说明 |
|--------|------|
| api.id 重复 | 多个 YAML 文件使用相同的 `id` |
| api.id 缺失 | `id` 字段为空或未定义 |
| api.path 缺失 | `path` 字段为空或未定义 |
| api.method 缺失 | `method` 字段为空或未定义 |
| source 缺失 | `source` 对象未定义 |
| source.datasource 缺失 | 数据源名称为空 |
| source.query 缺失 | SQL 查询语句为空 |
| response 缺失 | `response` 对象未定义 |
| response.type 无效 | 不是 `page`、`list`、`single` 之一 |
| 路径冲突（YAML 间） | 多个 YAML 定义了相同的 `path + method` 组合 |
| 路径冲突（YAML vs Java） | YAML 路径与 `@RequestMapping` 冲突 |
| SQL 注入模式 | 检测到不安全的 SQL 模式 |
| DDL 操作警告 | YAML 中包含 DDL 语句（输出 WARN 日志） |

---

## 数据源配置

### JDBC 数据源

JDBC 数据源通过 Spring Boot 标准配置，在 `application.yml` 中定义：

```yaml
# 单数据源
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=orders
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
```

YAML API 定义中的 `source.datasource` 对应 Spring 容器中的 DataSource Bean 名称：

- 单数据源：默认 Bean 名称为 `dataSource`
- 多数据源：通过 `@Bean(name="mssql-order")` 自定义 Bean 名称，YAML 中对应填写 `datasource: mssql-order`

### R2DBC 数据源

R2DBC 是 Spring 生态的响应式数据库连接规范，框架通过 `R2dbcQueryEngine` 提供支持。R2DBC 使用 `ConnectionFactory`（而非 JDBC 的 `DataSource`），通过 `spring.r2dbc.*` 属性配置。

**与 JDBC 的关键差异：**

| 特性 | JDBC | R2DBC |
|------|------|-------|
| 连接对象 | `DataSource` | `ConnectionFactory` |
| 参数绑定 | 1-based（`?` 从 1 开始） | 0-based（`?` 从 0 开始） |
| 默认 Bean 名称 | `dataSource` | `connectionFactory` |
| 配置前缀 | `spring.datasource.*` | `spring.r2dbc.*` |
| 依赖 | `spring-boot-starter-jdbc` | `spring-boot-starter-data-r2dbc` |

**配置示例：**

```yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///products;MODE=MySQL
    username: sa
```

**YAML API 定义示例：**

```yaml
api:
  id: query-products
  name: Query Products
  path: /v1/products
  method: GET
  parameters:
    - name: category
      in: query
      type: string
      required: false
    - name: page
      in: query
      type: integer
      required: false
    - name: size
      in: query
      type: integer
      required: false
  source:
    type: r2dbc
    datasource: connectionFactory
    query: "SELECT id, name, price, category FROM products WHERE 1=1 ${category: AND category = :category}"
  response:
    type: page
    fields:
      - name: id
        scope: basic
      - name: name
        scope: basic
      - name: price
        scope: basic
      - name: category
        scope: detail
  scopes:
    product.read: basic
```

> **注意**：R2DBC 的 `source.datasource` 对应 Spring 容器中的 `ConnectionFactory` Bean 名称。使用 `spring-boot-starter-data-r2dbc` 自动配置时，默认 Bean 名称为 `connectionFactory`。

---

## SqlDialect 与多数据库分页

框架内置 `SqlDialect` 枚举，自动根据数据源 URL 检测数据库类型并生成对应的分页 SQL。

### 支持的方言

| 方言 | 数据库 | 分页语法 |
|------|--------|----------|
| `MSSQL` | SQL Server | `OFFSET N ROWS FETCH NEXT M ROWS ONLY` |
| `MYSQL` | MySQL | `LIMIT M OFFSET N` |
| `POSTGRESQL` | PostgreSQL | `LIMIT M OFFSET N` |
| `H2` | H2（默认模式） | `LIMIT M OFFSET N` |

### 自动检测

`SqlDialect.fromUrl()` 根据 JDBC 或 R2DBC URL 前缀自动识别方言：

| URL 前缀 | 检测结果 |
|----------|---------|
| `jdbc:sqlserver:` / `r2dbc:sqlserver:` | `MSSQL` |
| `jdbc:mysql:` / `r2dbc:mysql:` | `MYSQL` |
| `jdbc:postgresql:` / `r2dbc:postgresql:` | `POSTGRESQL` |
| `jdbc:h2:` / `r2dbc:h2:` | `H2`（除非 URL 包含 `MODE=MSSQLServer`，则返回 `MSSQL`） |

> **H2 兼容模式**：当 H2 URL 中包含 `MODE=MSSQLServer` 或 `MODE=MSSQL` 时，框架自动切换为 MSSQL 方言，生成 `OFFSET ... FETCH` 分页语法。这在开发和测试环境中很有用，可以用 H2 模拟 SQL Server 行为。

### 分页 SQL 生成

`PaginationBuilder` 根据方言生成对应的分页子句：

**MSSQL 方言：**
```sql
SELECT order_no, status FROM orders WHERE 1=1
OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY
```

**MySQL / PostgreSQL / H2 方言：**
```sql
SELECT order_no, status FROM orders WHERE 1=1
LIMIT 20 OFFSET 0
```

安全限制：单页最大 1000 条（`MAX_PAGE_SIZE`），页码最大 10000（`MAX_PAGE`）。

### COUNT 查询

分页查询自动执行 COUNT 查询获取总记录数。`buildCountSql()` 方法根据方言移除对应的分片子句（`OFFSET ... FETCH` 或 `LIMIT ... OFFSET`），外包 `SELECT COUNT(*)`：

```sql
-- 原始查询
SELECT * FROM orders WHERE 1=1 ORDER BY order_no OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY
-- 生成的 COUNT 查询
SELECT COUNT(*) FROM (SELECT * FROM orders WHERE 1=1) AS _count
```

---

## 列名转换

SQL 查询返回的列名自动从 `snake_case` 转换为 `camelCase`：

| SQL 列名 | API 字段名 |
|----------|-----------|
| `order_no` | `orderNo` |
| `customer_name` | `customerName` |
| `created_at` | `createdAt` |
| `status` | `status` |

---

## HTTP 数据源

除 JDBC 和 R2DBC 外，API 可以配置为转发请求到上游 HTTP 服务。框架内部使用 Spring 6 的 `RestClient` 实现请求转发。

### 配置方式

设置 `source.type: http` 并提供 `url`、`method`、`headers` 和 `timeout`：

```yaml
api:
  id: proxy-user-profile
  name: Proxy User Profile
  path: /v1/users/{userId}/profile
  method: GET
  parameters:
    - name: userId
      in: path
      type: string
      required: true
  source:
    type: http
    url: "https://internal-service.example.com/api/users/{userId}"
    method: GET
    headers:
      X-Internal-Token: "service-token-123"
    timeout: 5000
  response:
    type: single
```

### HTTP source 字段明细

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `type` | string | 是 | - | 设置为 `http` |
| `url` | string | 是 | - | 上游服务 URL |
| `method` | string | 否 | `GET` | HTTP 方法：GET、POST、PUT、DELETE |
| `headers` | map | 否 | `{}` | 静态请求头，键值对形式 |
| `timeout` | integer | 否 | `5000` | 请求超时时间（毫秒） |

### 参数传递

- GET 请求：参数作为查询字符串追加到 URL
- POST/PUT 请求：参数作为 JSON 请求体发送

### 响应解析

框架自动尝试从常见包装键中提取数据数组：`data`、`items`、`results`、`records`、`list`。如果都不存在，将整个响应当作单条记录处理。总数从 `total`、`total_count`、`totalCount`、`count` 键提取。

---

## 认证配置

### 配置项

```yaml
data-api:
  auth-type: none        # none | jwt | apikey
  jwt-secret: ""         # HMAC256 密钥（auth-type: jwt 时必填）
  api-keys: ""           # 逗号分隔的有效 API Key（auth-type: apikey 时必填）
  auth.strict-scopes: false  # 是否严格模式（无 scope 时返回空数据）
```

### 认证模式

| 模式 | 配置值 | 认证方式 | Caller ID 来源 |
|------|--------|----------|---------------|
| 无认证 | `none` | 信任所有请求 | `X-Caller-Id` 请求头，默认 `anonymous` |
| JWT | `jwt` | `Authorization: Bearer <token>` | JWT subject |
| API Key | `apikey` | `X-API-Key` 请求头 | 密钥哈希的十六进制值 |

### JWT 认证

使用 HMAC256 算法验证 JWT 签名。Token 需包含：

- `sub`：调用方标识（用于 scope 解析）
- `scope`：空格分隔的 scope 列表（如 `"order.read order.detail"`）

```yaml
data-api:
  auth-type: jwt
  jwt-secret: "your-256-bit-secret"
```

### API Key 认证

从 `X-API-Key` 请求头读取密钥，与配置的逗号分隔列表匹配：

```yaml
data-api:
  auth-type: apikey
  api-keys: "key-alpha-001,key-beta-002,key-gamma-003"
```

---

## 速率限制

### 配置

```yaml
data-api:
  rate-limit-enabled: true  # 全局开关
```

### 工作机制

使用令牌桶算法，每个 `apiId:callerId` 组合维护独立桶。令牌以每秒一个的速率补充。

在 API 的 YAML 中通过 `sla.rateLimit` 设置每秒允许的请求数：

```yaml
sla:
  rateLimit: 100
```

超限时返回 HTTP 429 和 `Retry-After` 响应头。

---

## 安全功能

### SQL 注入防护

框架提供四层安全防护：

1. **启动时检测（YamlLint）**：检测 YAML 中的 SQL 拼接模式，发现风险拒绝启动
2. **运行时校验（SqlInjectionGuard）**：检测 8 种注入模式（注释注入、UNION 注入、布尔注入、时间盲注、文件操作、十六进制编码等）
3. **DDL 权限管控（DdlGuard）**：检测 `CREATE`、`ALTER`、`DROP`、`TRUNCATE`、`RENAME`、`GRANT`、`REVOKE` 操作，要求 `ddl` scope
4. **参数化查询（PreparedStatement）**：所有 SQL 通过 `PreparedStatement` 执行，参数值不进行字符串拼接

### DDL 权限

需要在 `scope-mapping` 中为需要执行 DDL 的调用方配置 `ddl` scope：

```yaml
data-api:
  scope-mapping: "admin:basic+detail+financial+ddl"
```
