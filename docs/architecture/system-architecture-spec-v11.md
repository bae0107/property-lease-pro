# 系统架构分层 Spec

> 版本：v11.0 | 技术栈：Spring Boot 3.2.0 / Spring Cloud 2023.0.0 / Java 17 / MySQL + H2（本地）/
> Redis / jOOQ 3.19 / Liquibase 4.x / Maven

---

## 目录

1. [系统总览](#1-系统总览)
2. [Maven 模块拓扑](#2-maven-模块拓扑)
3. [版本管理与 Parent 继承链](#3-版本管理与-parent-继承链)
4. [安全架构](#4-安全架构)
5. [Security-Starter 详细设计](#5-security-starter-详细设计)
6. [Common 模块](#6-common-模块)
7. [服务分层规范](#7-服务分层规范)
8. [OpenAPI 契约规范](#8-openapi-契约规范)
9. [Clients 模块规范](#9-clients-模块规范)
10. [统一响应结构规范](#10-统一响应结构规范)
11. [数据访问层规范（Liquibase + jOOQ）](#11-数据访问层规范liquibase--jooq)
12. [错误处理规范](#12-错误处理规范)
13. [服务间通信规范](#13-服务间通信规范)
14. [审计日志规范](#14-审计日志规范)
15. [可观测性规范（TraceId 全链路）](#15-可观测性规范traceid-全链路)
16. [包命名与目录结构](#16-包命名与目录结构)
17. [模块间依赖矩阵](#17-模块间依赖矩阵)
18. [关键约束速查](#18-关键约束速查)
19. [待确认事项](#19-待确认事项)

---

## 1. 系统总览

```
┌───────────────────────────────────────────────────────────┐
│                        外部客户端                          │
└──────────────────────────┬────────────────────────────────┘
                           │ HTTPS（唯一对外入口）
┌──────────────────────────▼────────────────────────────────┐
│              API Gateway（WebFlux / Reactive）             │
│   职责：验证 User JWT → 将用户上下文打包进 Service JWT     │
│   内置放行：/actuator/**、/error（starter 常量，无需配置） │
│   可配置放行：/api/main-service/auth/** 等（yml 配置）     │
│   其余路径：校验 User JWT，生成携带 userId+permissions     │
│             的 Service JWT，移除 Authorization Header      │
└────────┬─────────────────┬──────────────────┬─────────────┘
         │ X-Service-Token │                  │
         │（含userId+perms）│                  │
┌────────▼──────┐  ┌───────▼────────┐  ┌─────▼──────────────┐
│  main-service │  │ billing-service│  │  device-service    │
│  含 IAM 模块  │  │  订单 / 支付   │  │  设备 / 命令下发   │
│  （Servlet）  │  │  （Servlet）   │  │  （Servlet）       │
└───────────────┘  └────────────────┘  └────────────────────┘
```

**统一 Service JWT 原则（Zero-Trust）：**

> Gateway→微服务 与 微服务→微服务 采用完全相同的校验机制。所有入站请求均需携带有效 Service
> JWT（除内置/配置放行路径外），用户上下文（userId + permissions）通过 Service JWT Claims 密码学保护传递，不信任任何明文
> Header。

**放行路径双端对齐约束（C-42）：**

> Gateway 的 `permit-paths` 中，凡是路由到某个微服务的路径，该微服务的 `permit-paths` 中**必须包含对应的
StripPrefix 后路径**。
>
> 示例（main-service IAM 模块）：
> - Gateway permit-paths：`/api/main-service/auth/**`
> - main-service permit-paths：`/auth/**`、`/internal/v1/**`
>
> `/actuator/**` 和 `/error` 由 starter 内置常量自动放行，无需双端配置。

---

## 2. Maven 模块拓扑

```
com.jugu.propertylease (root pom)
│
├── starters/
│   ├── starter-parent/
│   └── microservice-starter-parent/
│       └── src/main/resources/openapi-templates/
│           └── apiDelegate.mustache
│
├── security-starter/
├── common/
│   └── src/main/resources/openapi/
│       └── common-components.yaml          ← 全系统公共 OpenAPI Schema 唯一来源
├── clients/
│   ├── main-service-client/
│   ├── billing-service-client/
│   └── device-service-client/
├── gateway/
├── main-service/
│   └── src/main/resources/openapi/
│       ├── main-service-api.yaml           ← 对外接口（经 Gateway）
│       └── main-service-internal-api.yaml  ← 内部接口（服务间直调）
├── billing-service/
└── device-service/
```

---

## 3. 版本管理与 Parent 继承链

### 3.1 继承关系

```
spring-boot-starter-parent:3.2.0
  └── starter-parent
        ├── security-starter
        ├── common
        ├── clients/*
        └── microservice-starter-parent
              ├── gateway / main-service / billing-service / device-service
```

### 3.2 starter-parent 职责

- 统一管理版本：Spring Cloud 2023.0.0、jOOQ 3.19.x、Liquibase 4.x、JJWT
- 通用插件：`maven-compiler-plugin`（Java 17）、`flatten-maven-plugin`
- **不引入**任何 Spring Web / Cloud 依赖

### 3.3 microservice-starter-parent 职责

- 继承 `starter-parent`
- 引入微服务共用依赖：`spring-boot-starter-web`、`spring-boot-starter-security`、`security-starter`、
  `common`、`spring-boot-starter-actuator`、`micrometer-tracing-bridge-brave`、`h2`（scope=test 或
  runtime，本地使用）
- 统一插件：
    - `openapi-generator-maven-plugin`（7.17.0，指向 mustache 模板，统一配置 `openApiNullable=false`）
    - `liquibase-maven-plugin`
    - `jooq-codegen-maven-plugin`（基于 Liquibase changelog 离线生成）

**openapi-generator 统一配置（在 microservice-starter-parent 设置，所有微服务继承）：**

```xml
<configOptions>
    <openApiNullable>false</openApiNullable>  <!-- 禁止生成 JsonNullable 包装 -->
    <useSpringBoot3>true</useSpringBoot3>
    <delegatePattern>true</delegatePattern>
    <interfaceOnly>true</interfaceOnly>
    <useTags>true</useTags>
</configOptions>
```

> `openApiNullable=false` 原因：yaml 中 `nullable: true` 的三态语义（未传/null/有值）
> 对过滤字段等场景多余，关闭后生成干净的 `@Nullable T` 而非 `JsonNullable<T>`。

---

## 4. 安全架构

### 4.1 Token 体系

| 属性     | User JWT                                                      | Service JWT                                                           |
|--------|---------------------------------------------------------------|-----------------------------------------------------------------------|
| 用途     | 外部客户端→Gateway 的认证凭证                                           | 系统内所有服务间通信的统一凭证                                                       |
| 颁发方式   | 用户登录时由 IAM（main-service）签发                                    | 本地自签，无需远程调用                                                           |
| 算法     | HS256                                                         | HS256                                                                 |
| 密钥     | `security.jwt.user.secret`（main-service 签发，Gateway 验证，两端必须相同） | `security.jwt.service.secret`（所有服务使用）                                 |
| Claims | `sub(username)`, `userId`, `permissions[]`, `iat`, `exp`      | `sub(serviceName)`, `userId`(nullable), `permissions[]`, `iat`, `exp` |
| 生命周期   | 较长（1800s），IAM 提供 /auth/refresh                                | 极短（300s），每次调用前本地重新生成                                                  |

> User JWT 只在外部客户端和 Gateway 之间流动，**不进入微服务内网**。Gateway 是两种 Token 的转换节点。
>
> User JWT 由 main-service IAM 模块签发（`UserJwtIssuer`，使用 `jwt.user.secret`，独立于
`ServiceTokenGenerator`）。

### 4.2 完整认证流程

```
外部客户端
    │ Authorization: Bearer <userJwt>
    ▼
[Gateway - ReactiveUserJwtFilter]（WebFilter，在路由前执行）
  ① ensureTraceId：已有则透传，无则生成 UUID；写入 request header + exchange.attributes
  ② 内置放行（/actuator/**、/error）或 yml 配置放行（/api/main-service/auth/** 等）：直接转发
  ③ 认证路径：
     验证 User JWT（user.secret）→ 失败 → 401 JSON（含 traceId）
     解析 username / userId / permissions[]
     生成 Service JWT：sub=gateway, userId=xxx, permissions=[...], exp=now+300
     移除 Authorization Header（阻止 User JWT 进入内网）
     添加 X-Service-Token
  ④ 转发
         │
         ▼
[微服务 - TraceIdFilter]（SecurityFilterChain 内最先执行）
  读取 X-Trace-Id Header → 已有则写 MDC，无则生成 UUID 写 MDC

[微服务 - ServletServiceJwtFilter]
  内置放行：/actuator/**、/error
  yml 配置放行：/auth/**、/internal/v1/**（C-42 对齐）
  认证路径：
    读 X-Service-Token → 缺失 → 直接写 401 JSON（含 MDC traceId）
    验证 Service JWT（service.secret）→ 失败 → 直接写 401 JSON
    解析 userId / permissions[] → 构建 ServiceJwtAuthenticationToken → 写入 SecurityContext
    /api/**：@PreAuthorize 执行权限判定（权限码来自 User JWT permissions[]）
    /internal/**：Service JWT 认证通过即可，无方法级鉴权

[微服务 A → 微服务 B]（ServiceTokenClientInterceptor）
  从 MDC 取 traceId → 设置 X-Trace-Id Header（全链路 traceId 一致）
  从 SecurityContext 取 userId + permissions
  生成新 Service JWT（sub=service-a，透传 userId+permissions）
  设置 X-Service-Token → 微服务 B 按相同逻辑处理
```

### 4.3 security.mode 三个值

| mode      | 运行形态    | 认证机制                       | 适用场景                                |
|-----------|---------|----------------------------|-------------------------------------|
| `gateway` | WebFlux | 验证 User JWT，生成 Service JWT | API Gateway                         |
| `service` | Servlet | 验证 Service JWT             | 生产/开发环境微服务                          |
| `mock`    | Servlet | 跳过所有 Token 验证，注入固定 mock 用户 | 本地开发（`security.mode` 独立于部署 Profile） |

> 典型配置：`spring.profiles.active=local` + `security.mode=mock`。
> mock 模式下 `@PreAuthorize` 正常生效，可通过配置不同 permissions 测试鉴权逻辑。

### 4.4 Spring Security 集成方式

| 组件                         | 选择                                                                  | 理由                                   |
|----------------------------|---------------------------------------------------------------------|--------------------------------------|
| Authentication 载体          | `ServiceJwtAuthenticationToken extends AbstractAuthenticationToken` | 语义准确，符合框架扩展规范                        |
| 不使用 `UserDetails`          | ✅ 不实现                                                               | 无状态 JWT 场景无需密码/账号状态字段                |
| 不使用 OAuth2 Resource Server | ✅ 不引入                                                               | 双 Token 体系 + 自定义 Header 与 OAuth2 不匹配 |
| 权限鉴权                       | `@PreAuthorize` + 自定义 `PermissionEvaluator`                         | 天然 Spring Security 集成                |
| 方法安全                       | `@EnableMethodSecurity(prePostEnabled=true)`                        | 激活 `@PreAuthorize` 支持                |

### 4.5 RBAC 模型

```
Permission 格式：{resource}:{action}（全小写）
示例：iam:user:read / order:read / device:command

User ──(M:N)── Role ──(M:N)── Permission

permissions[] 登录时由 IAM 从 DB 查询后嵌入 User JWT
运行时从 Service JWT 直接读取，不查 IAM 数据库
```

**角色分类（role_type）：**

| role_type | 说明           | 权限管理方式                                           | 分配可锁定                         |
|-----------|--------------|--------------------------------------------------|-------------------------------|
| `BUILTIN` | 内置角色（系统预置）   | 由 `PermissionSyncRunner` 从 yaml 全量维护，不可通过 API 修改 | 由 `lock_role_assignment` 字段控制 |
| `CUSTOM`  | 自定义角色（管理员创建） | 通过 `PUT /iam/roles/{id}/permissions` API 管理      | 默认不锁定                         |

**内置角色清单：**

| code           | name  | lock_role_assignment | data_scope_dimension |
|----------------|-------|----------------------|----------------------|
| `SYSTEM_ADMIN` | 系统管理员 | 0（可分配）               | NULL（全量访问）           |
| `INSTALLER`    | 安装工   | 1（锁定）                | NULL                 |
| `TENANT_ROLE`  | 租客    | 1（锁定）                | NULL                 |

**数据权限维度（data_scope_dimension）：**

```
层级：区域（area）> 门店（store）
AREA  → 区域维度（总部管理员/运营管理员/区域管理员）
STORE → 门店维度（财务/门店管家/管家）
NULL  → 无数据权限要求（SYSTEM_ADMIN/INSTALLER/TENANT_ROLE 等）

适用范围：仅 STAFF 类型用户有数据权限记录（iam_user_data_scope）
  ADMIN  → 全量访问（代码特判）
  TENANT → 只看自身数据（业务层过滤）
```

**权限同步机制（PermissionSyncRunner）：**

```
启动时运行（@Order(1) ApplicationRunner），从 OpenAPI yaml 的
x-required-permission 字段自动 upsert iam_permission 表：
  - 新增：INSERT
  - 恢复（yaml 重新加入）：UPDATE SET deleted_at=NULL
  - 软删除（yaml 移除）：UPDATE SET deleted_at=NOW(3)
  - BUILTIN 角色的 iam_role_permission 全量替换为当前有效权限

Liquibase 只建表结构和初始化用户/角色数据，不插入 iam_permission 数据。
```

---

## 5. Security-Starter 详细设计

### 5.1 核心设计决策

| 决策项               | 方案                                                                         |
|-------------------|----------------------------------------------------------------------------|
| Authentication 载体 | `ServiceJwtAuthenticationToken extends AbstractAuthenticationToken`        |
| 统一 Token 机制       | Gateway→微服务 与 微服务→微服务 完全相同                                                 |
| 用户上下文传递           | userId + permissions 打包进 Service JWT Claims，密码学保护                          |
| 微服务过滤器            | `TraceIdFilter`（最先）+ `ServletServiceJwtFilter`（认证）                         |
| JWT 配置校验          | 条件 Bean 校验（`ServiceJwtValidator` / `UserJwtValidator`），mock 模式跳过 JWT 校验    |
| 放行路径              | 内置常量（`DEFAULT_PERMIT_PATHS`）+ yml 可扩展                                      |
| requestMatchers   | 使用 `AntPathRequestMatcher`（不依赖 Spring MVC HandlerMappingIntrospector，测试友好） |

> 详细规范见 `security-starter-module-specs-v5.md`。

---

## 6. Common 模块

### 6.1 Java 能力（已完成）

| 能力         | 类                                                             |
|------------|---------------------------------------------------------------|
| 统一错误响应格式   | `ErrorResponse`（扁平格式，`@JsonInclude(NON_NULL)`）                |
| 业务异常       | `BusinessException`（携带 HttpStatus + errorCode）                |
| 全局异常处理     | `GlobalExceptionHandler`（继承 `ResponseEntityExceptionHandler`） |
| 函数式结果包装    | `Result<T>`                                                   |
| Feign 异常收敛 | `FeignBusinessExceptionErrorDecoder` + `FeignExceptionAspect` |

### 6.2 jOOQ 配置工具（新增）

```java
// common/.../jooq/JooqConfigSupport.java
// 解决 C-43：各微服务用于注册 DataPermissionVisitListener
// 包名：org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer
// Spring Boot 3.2 + jOOQ 3.19 准确 API

public final class JooqConfigSupport {

    public static DefaultConfigurationCustomizer withVisitListener(VisitListener listener) {
        return config -> config.set(new DefaultVisitListenerProvider(listener));
    }

    public static DefaultConfigurationCustomizer withVisitListeners(VisitListener... listeners) {
        var providers = Arrays.stream(listeners)
            .map(DefaultVisitListenerProvider::new)
            .toArray(DefaultVisitListenerProvider[]::new);
        return config -> config.set(providers);
    }
}
```

各微服务使用方式：

```java
// {service}/config/JooqConfig.java
@Configuration
public class JooqConfig {
    @Bean
    public DefaultConfigurationCustomizer jooqCustomizer(
            XxxDataPermissionVisitListener listener) {
        return JooqConfigSupport.withVisitListener(listener);
    }
}
```

common pom.xml 中 jOOQ 和 spring-boot-autoconfigure 设为 `<optional>true</optional>`，避免污染无需
jOOQ 的模块。

### 6.3 OpenAPI 公共 Schema（新增）

公共 Schema 定义在 `common/src/main/resources/openapi/common-components.yaml`，是全系统唯一来源，各微服务通过相对路径引用。

**包含内容：**

| Schema            | 说明                                                  |
|-------------------|-----------------------------------------------------|
| `ErrorResponse`   | 全系统统一错误响应（扁平格式）                                     |
| `PageRequest`     | 通用分页请求（pageNo 从 1 开始，含 filters）                     |
| `QueryFilter`     | 过滤容器（stringFilters / idsFilters / enumFilters，均为数组） |
| `StringFilter`    | 字符串模糊过滤（LIKE %value%）                               |
| `IdsFilter`       | 多值 IN 过滤（IN(v1,v2,...)）—— 注意是数组，支持多字段同时过滤           |
| `EnumFilter`      | 枚举精确过滤（= value）                                     |
| `PageResponse`    | 分页响应基础（含 tableMeta，各资源通过 allOf 追加 items）            |
| `TableMeta`       | 表格字段元数据（随每次分页响应返回，后端内部缓存）                           |
| `FilterFieldMeta` | 单字段元数据（key / label / filterType / options）          |
| `FilterOption`    | 枚举下拉选项（value / label）                               |
| `BatchRequest`    | 通用批量操作（ids 数组，单个传 1 个）                              |

**不在 common 的 Schema：** 各服务特有的业务响应（如 IAM 的 `WechatBindRequiredResponse`）定义在各服务自己的
yaml 中。

**$ref 路径约定（以 main-service 视角为例）：**

```
../../../../../common/src/main/resources/openapi/common-components.yaml#/components/schemas/ErrorResponse
```

---

## 7. 服务分层规范

### 7.1 分层结构

```
DelegateImpl（薄转接层）→ Service（业务逻辑 + 事务）→ Repository 接口 → Repository impl（jOOQ）→ DB
```

### 7.2 各层约定

| 层                         | 职责                                                      | 关键约束                                                 |
|---------------------------|---------------------------------------------------------|------------------------------------------------------|
| **DelegateImpl**          | 实现 Delegate；参数透传；`@Valid`                               | 禁止业务逻辑；`@PreAuthorize` 由 mustache 模板生成在 Delegate 接口上 |
| **Service**               | 业务编排；`@Transactional`；`@AuditLog`                       | 禁止拼 SQL；通过 Repository 接口操作数据                         |
| **Repository 接口**         | 定义数据操作契约，返回 jOOQ POJO                                   | 只定义方法签名                                              |
| **Repository impl**       | jOOQ 实现；Record → POJO 由 `record.into(XxxPojo.class)` 完成 | Record 不出此层；不加 `@Transactional`                      |
| **jOOQ POJO**             | 编译期从 Liquibase 生成的数据模型                                  | 禁止手写任何 entity 类；POJO 即 Domain Model                  |
| **IamMapper / XxxMapper** | 静态方法，负责 jOOQ POJO ↔ OpenAPI DTO 转换                      | 不含业务逻辑，JDK 8+ 语法                                     |

**对象映射两条路径：**

```
路径 A（简单 CRUD）：DTO → Service → Repository(POJO) → Mapper → DTO
路径 B（复杂业务）：DTO → Service（中间状态用 record）→ Repository → Mapper → DTO
                    复杂业务不建独立 Domain Model，中间状态用 Java record 表达
```

---

## 8. OpenAPI 契约规范

### 8.1 两个基本面

```
基本面 1：OpenAPI yaml 是对外能力的唯一来源
  - 所有微服务的接口定义必须来自 yaml，通过 openapi-generator 生成 Delegate 接口
  - 各服务必须实现生成的 Delegate（禁止绕过）
  - 业务对象（请求/响应 DTO）由 yaml schemas 生成，禁止手写

基本面 2：Liquibase XML 是数据库结构的唯一来源
  - 禁止手动建表或修改表结构
  - jOOQ 在编译期从数据库生成 Record 和 POJO（Liquibase 先跑，jOOQ 后生成）
  - 禁止手写任何 entity 类（Hibernate entity / MyBatis entity 等）
```

### 8.2 x-required-permission 规则

| 路径前缀                | 是否需要 x-required-permission | 原因                            |
|---------------------|----------------------------|-------------------------------|
| `/auth/**`          | ❌ 例外（登录/绑定接口）              | 无 JWT，无法鉴权                    |
| `/iam/**`（及各服务对外接口） | ✅ 必须                       | 全部需要方法级鉴权                     |
| `/internal/v1/**`   | ❌ 不需要                      | Service JWT + 调用方白名单替代，无细粒度权限 |

违反此规则应在 CI 阶段拦截（可通过 Maven 插件扫描 yaml 校验）。

**mustache 模板生成 @PreAuthorize（C-40 已解决）：**

```mustache
{{! apiDelegate.mustache - vendorExtensions key 保持连字符格式}}
{{#vendorExtensions.x-required-permission}}
@PreAuthorize("hasPermission(null, '{{vendorExtensions.x-required-permission}}')")
{{/vendorExtensions.x-required-permission}}
```

### 8.3 分页与过滤规范

所有分页查询接口统一使用 `POST /{resource}/query`，请求体继承 `PageRequest`，响应体通过 allOf 继承
`PageResponse` 并追加 `items` 字段。

**QueryFilter 三种过滤类型：**

```
stringFilters：字符串模糊过滤，LIKE %value%
idsFilters：  多值 IN 过滤，IN(v1,v2,...)；注意是数组，支持多字段同时过滤
              示例：同时过滤 id IN [1,2,3] 且 roleId IN [10,11]
enumFilters： 枚举精确过滤，= value，可选值来自 tableMeta.fields[].options
```

**tableMeta 机制：**

- 随每次分页响应一起返回，后端内部缓存（对前端透明）
- 描述当前资源支持的可过滤字段（key / label / filterType / options）
- 前端根据 tableMeta 动态渲染过滤器 UI
- 后端根据 tableMeta 定义做 key 白名单校验，前后端使用同一份定义保证一致性

**allOf 行为说明（generator 7.x）：**

Spring generator 7.x 对 allOf 生成**扁平类**（所有字段内联，不生成 Java 继承），这是正确且符合预期的行为：

```java
// WechatBindRequiredResponse 实际生成（code/message/traceId/bindToken 全部内联）
class WechatBindRequiredResponse {
    String code;       // 来自 ErrorResponse
    String message;    // 来自 ErrorResponse
    String traceId;    // 来自 ErrorResponse
    String bindToken;  // 自身字段
}
```

### 8.4 批量操作规范

```
用 POST 而非 DELETE with body：
  DELETE with body 在部分反向代理（Nginx）和 CDN 中会丢弃请求体，导致服务端收到空 body。
  批量操作统一使用 POST /batch-delete 或 PUT /batch/status 等语义明确的路径。

单个操作复用批量接口：ids 传 1 个即为单个操作，不需要另建单独接口。
```

---

## 9. Clients 模块规范

### 9.1 模块职责

- 封装下游服务的内部接口（`/internal/v1/**`）调用
- 通过 openapi-generator 从 `{service}-internal-api.yaml` 生成 Feign 接口 + DTO
- 通过 AutoConfiguration + `@ConditionalOnBean(ServiceTokenClientInterceptor.class)` 自动注册

### 9.2 yaml 引用约定

clients 模块的 yaml 引用下游服务的 internal-api.yaml，使用相对路径：

```
billing-service-client/ 引用：
  ../../../billing-service/src/main/resources/openapi/billing-service-internal-api.yaml
```

---

## 10. 统一响应结构规范

### 10.1 成功响应

直接返回强类型业务数据，无统一包装（无 `{ "success": true, "data": {...} }` 结构）。HTTP 2xx 即表示成功。

### 10.2 错误响应

全系统统一扁平格式（`ErrorResponse`）：

```json
{ "code": "IAM_USER_NOT_FOUND", "message": "用户 ID 999 不存在", "traceId": "abc123" }
```

此格式适用于：微服务 GlobalExceptionHandler、security-starter
SecurityHandlers（401/403）、ReactiveUserJwtFilter（Gateway
401）、GatewayErrorWebExceptionHandler（502/504/404）、RateLimitFilter（429）。

---

## 11. 数据访问层规范（Liquibase + jOOQ）

### 11.1 两层职责分离

```
Liquibase XML（唯一来源）─────→ 数据库表结构
       ↓ jooq-codegen-maven-plugin（编译期，基于 H2 内存库）
jOOQ Record / POJO（编译期生成）─→ RepositoryImpl 使用
       ↓ IamMapper / XxxMapper（手写静态方法）
OpenAPI DTO（openapi-generator 生成）
```

### 11.2 Liquibase 规范

**必须使用 XML 格式，原因：**

1. **多数据库兼容**：XML 使用 Liquibase 抽象类型（`BIGINT`/`VARCHAR`/`DATETIME`），自动翻译为 H2 或
   MySQL 方言，同一份文件兼容两个数据库；SQL 格式是原生 SQL，H2/MySQL 方言不同，必须维护两套文件
2. **IDE 支持**：XSD 约束，IDEA/VSCode 有字段补全和实时校验，写错属性会实时报错
3. **高级属性完整**：`runOnChange`/`failOnError`/`onValidationFail` 等在 XML 格式支持最全

**每个 changeSet 只包含一个 DDL 操作（方便精确回滚）。**

**本地 H2 必须启用 MySQL 兼容模式：**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:property_lease;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

MySQL 兼容模式下，H2 支持 `DATETIME(3)`、`AUTO_INCREMENT`、`ENGINE=InnoDB` 等 MySQL 语法。

### 11.3 jOOQ 代码生成规范

- **离线模式**：jooq-codegen-maven-plugin 基于 Liquibase changelog 生成（不连接数据库）
- **禁止手写 entity**：所有数据库映射类（Record / POJO）均由 jOOQ 编译期生成
- `record.into(XxxPojo.class)` 完成 Record → POJO 转换（jOOQ 内置，编译期生成）
- jOOQ DSLContext / Record 不出 RepositoryImpl 层

**生成目标包（各微服务统一约定）：**

```
com.jugu.propertylease.{service}.generated
  ├── tables/           → 表 DSL 类
  ├── tables/records/   → Record 类
  └── tables/pojos/     → POJO 类（即 Domain Model）
```

### 11.4 ID 策略

当前使用数据库 `AUTO_INCREMENT`，未来切换为 Snowflake 时通过 jOOQ `ExecuteListener` 统一拦截 INSERT
并注入 ID，业务代码无感知（对业务透明）。

### 11.5 审计字段规范

每张业务表必须包含：`created_at DATETIME(3)`、`updated_at DATETIME(3)`，核心业务表加
`created_by BIGINT`。

---

## 12. 错误处理规范

### 12.1 异常分层

| 层       | 异常类型                                    | HTTP 状态         | 处理位置                                             |
|---------|-----------------------------------------|-----------------|--------------------------------------------------|
| Gateway | `InvalidTokenException`（User JWT 无效）    | 401             | `ReactiveUserJwtFilter` 直接写响应                    |
| Gateway | 路由/下游异常                                 | 404/502/504     | `GatewayErrorWebExceptionHandler`                |
| Gateway | 限流                                      | 429             | `RateLimitFilter` 直接写响应                          |
| 微服务     | `InvalidTokenException`（Service JWT 无效） | 401             | `ServletServiceJwtFilter` 直接写响应                  |
| 微服务     | `AccessDeniedException`                 | 403             | `SecurityAccessDeniedHandler`                    |
| 微服务     | `BusinessException`                     | 业务自带            | `GlobalExceptionHandler`                         |
| 微服务     | Spring MVC HTTP 类异常                     | 框架自带            | `GlobalExceptionHandler.handleExceptionInternal` |
| 微服务     | `Exception`（兜底）                         | 500             | `GlobalExceptionHandler`                         |
| 微服务     | `WechatBindRequiredException`           | 403 + bindToken | `GlobalExceptionHandler` 扩展                      |

### 12.2 统一响应格式

```json
{ "code": "IAM_USER_NOT_FOUND", "message": "...", "traceId": "abc123" }
```

traceId 来源：

- 微服务：从 MDC 读取（`TraceIdFilter` 注入）
- Gateway Reactive 侧：从 `exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID)` 读取，兜底从
  request header 读；其中 `RateLimitFilter` 在两者都缺失时会生成 UUID 作为最终兜底

### 12.3 错误码格式

```
{SERVICE}_{RESOURCE}_{REASON}（全大写，下划线分隔）

框架级：COMMON_HTTP_{statusCode}（如 COMMON_HTTP_404）
校验失败：COMMON_REQUEST_VALIDATION_FAILED
请求体错误：COMMON_REQUEST_BODY_UNREADABLE
内部错误：INTERNAL_SERVER_ERROR
Gateway：GATEWAY_ROUTE_NOT_FOUND / GATEWAY_DOWNSTREAM_UNAVAILABLE 等
认证：IAM_TOKEN_EXPIRED / IAM_TOKEN_INVALID / IAM_TOKEN_MALFORMED / IAM_TOKEN_MISSING
IAM 业务：IAM_USER_NOT_FOUND / IAM_AUTH_INVALID_CREDENTIALS 等（详见 IAM spec）
```

---

## 13. 服务间通信规范

### 13.1 调用方向

```
main-service → billing-service /internal/v1/**  ✅
main-service → device-service  /internal/v1/**  ✅
其他方向：当前禁止
```

内部接口（`/internal/v1/**`）：

- 需要有效 Service JWT（X-Service-Token）
- 无方法级 `@PreAuthorize` 鉴权
- Service 层校验调用方白名单（`CurrentUser.getCallerName()` 匹配预期服务名）

### 13.2 跨服务最终一致性（Outbox）

```
1. 业务写 + 写 outbox_events（同一本地事务）
2. 定时任务扫描 PENDING → Client 调用目标 /internal 接口
3. 成功 → SENT；失败重试 N 次 → FAILED（告警）
4. 目标接口幂等（event_id 唯一键防重）
```

---

## 14. 审计日志规范

- 每服务独立 `audit_logs` 表
- `@AuditLog`、`AuditEvent`、`AuditLogger`、`AuditLogAspect` 定义于 security-starter
- 各服务实现 `AuditLogger`，用 `@Transactional(REQUIRES_NEW)` 隔离，写入失败静默忽略
- `AuditLogAspect` 加 `@Order(Ordered.LOWEST_PRECEDENCE - 1)` 确保在 `@Transactional` 外层
- mock 模式下 `AuditLogAspect` 同样正常工作（`@ConditionalOnBean(AuditLogger)` 不受 mode 影响）

---

## 15. 可观测性规范（TraceId 全链路）

### 15.1 TraceId 生成与传递规则

| 场景                           | 生成方                                             | 传递方式                                    |
|------------------------------|-------------------------------------------------|-----------------------------------------|
| 外部请求进入 Gateway（无 X-Trace-Id） | `ReactiveUserJwtFilter.ensureTraceId()` 生成 UUID | 写入 request header + exchange.attributes |
| 外部请求携带 X-Trace-Id            | 原样透传                                            | 同上                                      |
| 微服务收到请求                      | `TraceIdFilter` 读取 X-Trace-Id Header            | 写入 MDC（key="traceId"），请求结束 finally 清理   |
| 直连微服务（无 Gateway）             | `TraceIdFilter` 生成 UUID                         | 写入 MDC                                  |
| 服务 A → 服务 B（出站）              | `ServiceTokenClientInterceptor`                 | 从 MDC 读 traceId → 设置 X-Trace-Id Header  |

### 15.2 traceId 在 Gateway ErrorWebExceptionHandler 的实现原理

`GatewayErrorWebExceptionHandler.handle(exchange, ex)` 收到的是 `originalExchange`，
`exchange.mutate()` 构建新 exchange 时 `attributes` map 指向同一实例。因此优先从 attributes 读
traceId：

```java
String traceId = exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID);
if (traceId == null) {
    traceId = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID); // 兜底
}
```

---

## 16. 包命名与目录结构

### 16.1 包命名规则

| 模块               | 根包                                |
|------------------|-----------------------------------|
| security-starter | `com.jugu.propertylease.security` |
| common           | `com.jugu.propertylease.common`   |
| gateway          | `com.jugu.propertylease.gateway`  |
| main-service     | `com.jugu.propertylease.main`     |
| billing-service  | `com.jugu.propertylease.billing`  |
| device-service   | `com.jugu.propertylease.device`   |

### 16.2 微服务包结构（以 main-service IAM 为例）

```
main-service/src/main/java/com/jugu/propertylease/main/
├── MainServiceApplication.java
├── iam/
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── IamController.java
│   │   └── InternalUserController.java
│   ├── application/
│   │   ├── PasswordLoginService.java
│   │   ├── WechatLoginService.java
│   │   ├── RefreshTokenService.java
│   │   ├── BindTokenService.java
│   │   ├── PermissionSyncRunner.java      ← 权限同步
│   │   ├── UserApplicationService.java
│   │   ├── InternalUserService.java
│   │   └── RoleApplicationService.java
│   ├── domain/service/
│   │   └── IdentityDomainService.java
│   ├── infrastructure/
│   │   ├── repository/（接口 + impl）
│   │   ├── mapper/IamMapper.java          ← POJO ↔ DTO 转换
│   │   ├── external/（WechatClient 等）
│   │   ├── jwt/UserJwtIssuer.java
│   │   └── redis/IamRedisKeys.java
│   ├── config/JooqConfig.java
│   ├── exception/WechatBindRequiredException.java
│   └── audit/IamAuditLogger.java
└── generated/                             ← jOOQ 编译期生成，不提交到 git
    ├── tables/
    ├── tables/records/
    └── tables/pojos/
```

---

## 17. 模块间依赖矩阵

| 模块                 | 可依赖                                                                          | 禁止依赖                |
|--------------------|------------------------------------------------------------------------------|---------------------|
| `common`           | JDK only + Spring Boot（web/validation/aop）+ jOOQ（optional）                   | 任何内部模块              |
| `security-starter` | `common`、Spring Boot autoconfigure、Spring Security、JJWT                      | 任何微服务模块             |
| `clients/*`        | `common`、`security-starter`                                                  | 任何微服务模块             |
| `gateway`          | `security-starter`、`common`                                                  | 微服务业务模块             |
| `main-service`     | `security-starter`、`common`、`billing-service-client`、`device-service-client` | billing/device 业务代码 |
| `billing-service`  | `security-starter`、`common`                                                  | main/device 任何模块    |
| `device-service`   | `security-starter`、`common`                                                  | main/billing 任何模块   |

---

## 18. 关键约束速查

| #    | 约束                                                       | 说明                                                                                |
|------|----------------------------------------------------------|-----------------------------------------------------------------------------------|
| C-01 | 简单分层                                                     | DelegateImpl → Service → Repository，无 DDD                                         |
| C-02 | DelegateImpl 只做转接                                        | Service 不直接实现 Delegate                                                            |
| C-03 | 禁止手写 entity                                              | jOOQ 编译期生成 Record/POJO，禁止 Hibernate entity 等                                      |
| C-04 | 禁止 ORM                                                   | 只用 jOOQ                                                                           |
| C-05 | jOOQ 只在 Repository impl                                  | DSLContext/Record 不出 impl 层                                                       |
| C-06 | jOOQ 从 Liquibase 生成                                      | 基于 Liquibase changelog 离线生成                                                       |
| C-07 | @Transactional 只在 Service 层                              | Repository 不加                                                                     |
| C-08 | OpenAPI 同源                                               | 必须实现生成的 Delegate                                                                  |
| C-09 | Liquibase XML 同源                                         | 禁止手动改表；必须 XML 格式（H2/MySQL 兼容）                                                     |
| C-10 | 公共 Schema 同源                                             | 只在 common-components.yaml 定义，禁止各服务重复                                              |
| C-11 | 接口双轨制                                                    | /api/** 含 x-required-permission；/internal/** 只做 Service JWT + 调用方白名单              |
| C-12 | 响应结构规范                                                   | 成功返回强类型数据；失败统一 ErrorResponse 扁平格式                                                 |
| C-13 | clients yaml 同源                                          | 相对路径引用，只有一份                                                                       |
| C-14 | 禁止手写服务间 HTTP 调用                                          | 通过 clients 生成的 Client                                                             |
| C-15 | clients Bean 自动注册                                        | AutoConfiguration + @ConditionalOnBean(ServiceTokenClientInterceptor)             |
| C-16 | 服务地址固定配置                                                 | `services.{name}.url`                                                             |
| C-17 | 统一 Service JWT                                           | Gateway→微服务 与 微服务→微服务 完全相同机制                                                      |
| C-18 | User JWT 不进内网                                            | Gateway 移除 Authorization Header，换发 Service JWT                                    |
| C-19 | 用户上下文打包进 Service JWT                                     | userId + permissions 密码学保护，不信任明文 Header                                           |
| C-20 | Service JWT 本地自签                                         | 每次调用前生成，携带当前 userId + permissions                                                 |
| C-21 | 密钥隔离                                                     | user.secret（IAM 签发/Gateway 验证）≠ service.secret（全系统共用），均 HS256                     |
| C-22 | security-starter 双栈                                      | SERVLET/REACTIVE 自动检测                                                             |
| C-23 | 权限数据来自 Service JWT                                       | 运行时不查 IAM 数据库                                                                     |
| C-24 | @PreAuthorize 自动生成                                       | mustache 模板从 vendorExtensions.x-required-permission 生成（C-40 已解决）                  |
| C-25 | 鉴权只在对外接口                                                 | /internal/** 无 Permission 鉴权                                                      |
| C-26 | 数据权限 VisitListener                                       | SELECT/UPDATE/DELETE 均注入；INSERT 不注入                                               |
| C-27 | VisitListener 注册                                         | 各服务通过 JooqConfig + JooqConfigSupport 注册（C-43 已解决）                                 |
| C-28 | 审计服务自治                                                   | 每服务自己的 audit_logs，@AuditLog + AOP 自动写入                                            |
| C-29 | 审计事务隔离                                                   | AuditLogger 实现用 REQUIRES_NEW，写入失败静默忽略                                             |
| C-30 | @EnableMethodSecurity 唯一                                 | 独立 MethodSecurityConfiguration + @ConditionalOnMissingBean 保护                     |
| C-31 | ⚠️ 待确认                                                   | @AuditLog 的 beforeJson 获取方式                                                       |
| C-32 | ⚠️ 待确认                                                   | VisitListener 表识别辅助方法设计                                                           |
| C-33 | 主键当前用自增                                                  | 后续通过 jOOQ ExecuteListener 切换 Snowflake，对业务透明                                      |
| C-34 | Redis 仅业务缓存                                              | Repository 层禁止封装 Redis                                                            |
| C-35 | 审计字段必须                                                   | 每业务表必须有 created_at/updated_at                                                     |
| C-36 | 跨服务不跨库                                                   | 禁止跨数据库 JOIN                                                                       |
| C-37 | Outbox 最终一致                                              | 跨服务写走 Outbox；接收方接口幂等                                                              |
| C-38 | TraceId 全链路                                              | Gateway 生成，X-Trace-Id Header 传递，MDC 注入；出站调用透传                                     |
| C-39 | 放行路径分层管理                                                 | /actuator/** 和 /error 由 SecurityConstants 内置；服务特有路径在 yml 配置                       |
| C-40 | vendorExtensions key 格式                                  | `vendorExtensions.x-required-permission`（连字符原样保留）✅ 已解决                            |
| C-41 | mode 三个值                                                 | gateway（WebFlux）/ service（Servlet 生产）/ mock（Servlet 本地开发）                         |
| C-42 | 放行路径双端对齐                                                 | Gateway 配外部路径（/api/main-service/auth/**），微服务配 StripPrefix 后路径（/auth/**）✅ 已解决      |
| C-43 | DefaultConfigurationCustomizer                           | `JooqConfigSupport` 工具类在 common 模块，封装准确 API ✅ 已解决                                 |
| C-44 | Authentication 载体                                        | ServiceJwtAuthenticationToken extends AbstractAuthenticationToken，不使用 UserDetails |
| C-45 | jwt 配置条件校验                                               | mode=service/gateway 时由条件 Bean 校验；mode=mock 跳过                                    |
| C-46 | AuditLogAspect 在 @Transactional 外层                       | @Order(LOWEST_PRECEDENCE-1)                                                       |
| C-47 | AntPathRequestMatcher                                    | SecurityFilterChain 使用，不用 MvcRequestMatcher                                       |
| C-48 | 错误响应扁平格式                                                 | 全系统统一 `{code, message, traceId}`，禁止嵌套结构                                           |
| C-49 | traceId 写入 exchange.attributes                           | ReactiveUserJwtFilter 同时写 request header 和 attributes                             |
| C-50 | mock 模式与部署 Profile 独立                                    | security.mode=mock 是安全模式，与 spring.profiles.active 是两个独立维度                         |
| C-51 | GlobalExceptionHandler 继承 ResponseEntityExceptionHandler | 覆写 handleExceptionInternal 统一处理                                                   |
| C-52 | ServiceTokenClientInterceptor 透传 X-Trace-Id              | 从 MDC 读取，出站调用附加到 Header                                                           |
| C-53 | openApiNullable=false                                    | 全项目统一，在 microservice-starter-parent 设置，禁止 JsonNullable 包装                         |
| C-54 | H2 MySQL 兼容模式                                            | 本地必须配置 MODE=MySQL                                                                 |
| C-55 | 分页请求用 POST                                               | POST /{resource}/query，body 用 PageRequest，支持过滤条件                                  |
| C-56 | tableMeta 随分页返回                                          | 后端内部缓存，前端无感知，用于动态渲染过滤器 UI 和后端白名单校验                                                |
| C-57 | 批量操作用 POST                                               | POST /batch-delete 等，避免 DELETE with body 代理兼容问题                                   |
| C-58 | 权限数据来自 yaml                                              | x-required-permission 是权限唯一来源，PermissionSyncRunner 启动时同步                          |
| C-59 | BUILTIN 角色权限不可 API 修改                                    | 由 PermissionSyncRunner 全量维护                                                       |
| C-60 | lock_role_assignment                                     | 内置角色可锁定用户分配，不可通过管理 API 变更角色                                                       |
| C-61 | data_scope_dimension                                     | 角色携带数据权限维度，仅 STAFF 类型用户有 iam_user_data_scope 记录                                   |

---

## 19. 待确认事项

| #    | 事项                                                      | 影响范围                 | 优先级 |
|------|---------------------------------------------------------|----------------------|-----|
| C-31 | @AuditLog 的 beforeJson 获取方式（AOP 自动查 DB？手动传入？SpEL？）      | AuditLogAspect       | 低   |
| C-32 | DataPermissionVisitListener.queryInvolvesTable() 辅助方法设计 | 各服务 VisitListener 实现 | 低   |
