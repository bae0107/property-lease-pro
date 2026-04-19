# 系统架构分层 Spec

> 版本：v10.0 | 技术栈：Spring Boot 3.2.0 / Spring Cloud 2023.0.0 / Java 17 / MySQL / Redis / jOOQ /
> Liquibase / Maven

---

## 目录

1. [系统总览](#1-系统总览)
2. [Maven 模块拓扑](#2-maven-模块拓扑)
3. [版本管理与 Parent 继承链](#3-版本管理与-parent-继承链)
4. [安全架构](#4-安全架构)
5. [Security-Starter 详细设计](#5-security-starter-详细设计)
6. [Common 模块](#6-common-模块)
7. [服务分层规范](#7-服务分层规范)
8. [OpenAPI 契约规范（同源原则）](#8-openapi-契约规范同源原则)
9. [Clients 模块规范](#9-clients-模块规范)
10. [统一响应结构规范](#10-统一响应结构规范)
11. [数据访问层规范（Liquibase + jOOQ 离线模式）](#11-数据访问层规范liquibase--jooq-离线模式)
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
│   可配置放行：/api/v1/auth/login 等（yml 配置）            │
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

> Gateway 的 `permit-paths` 中，凡是路由到某个微服务的路径（如 `/api/v1/auth/login` 路由到
> main-service），该微服务的 `permit-paths` 中**必须包含相同路径**。两端均不携带 Service JWT，微服务侧若不放行则返回
> 401。`/actuator/**` 和 `/error` 由 starter 内置常量自动放行，无需双端配置。

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
│   └── src/main/resources/openapi/common-openapi.yaml
├── clients/
│   ├── main-service-client/
│   ├── billing-service-client/
│   └── device-service-client/
├── gateway/
├── main-service/
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
  `common`、`spring-boot-starter-actuator`、`micrometer-tracing-bridge-brave`
- 统一插件：`openapi-generator-maven-plugin`（指向自定义 mustache 模板）、`liquibase-maven-plugin`、
  `jooq-codegen-maven-plugin`

---

## 4. 安全架构

### 4.1 Token 体系

| 属性     | User JWT                                                 | Service JWT                                                           |
|--------|----------------------------------------------------------|-----------------------------------------------------------------------|
| 用途     | 外部客户端→Gateway 的认证凭证                                      | 系统内所有服务间通信的统一凭证                                                       |
| 颁发方式   | 用户登录时由 IAM 签发                                            | 本地自签，无需远程调用                                                           |
| 算法     | HS256                                                    | HS256                                                                 |
| 密钥     | `security.jwt.user.secret`（仅 Gateway 使用）                 | `security.jwt.service.secret`（所有服务使用）                                 |
| Claims | `sub(username)`, `userId`, `permissions[]`, `iat`, `exp` | `sub(serviceName)`, `userId`(nullable), `permissions[]`, `iat`, `exp` |
| 生命周期   | 较长（1800s），IAM 提供 refresh                                 | 极短（300s），每次调用前本地重新生成                                                  |

> User JWT 只在外部客户端和 Gateway 之间流动，**不进入微服务内网**。Gateway 是两种 Token 的转换节点。

### 4.2 完整认证流程

```
外部客户端
    │ Authorization: Bearer <userJwt>
    ▼
[Gateway - ReactiveUserJwtFilter]（WebFilter，在路由前执行）
  ① ensureTraceId：已有则透传，无则生成 UUID；写入 request header 和 exchange.attributes
  ② 内置放行（/actuator/**、/error）或 yml 配置放行（/api/v1/auth/login 等）：直接转发
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
  yml 配置放行：与 Gateway 对齐的路径（C-42 约束）
  认证路径：
    读 X-Service-Token → 缺失 → 直接写 401 JSON（含 MDC traceId）
    验证 Service JWT（service.secret）→ 失败 → 直接写 401 JSON
    解析 userId / permissions[] → 构建 ServiceJwtAuthenticationToken → 写入 SecurityContext
    /api/**：@PreAuthorize 执行权限判定
    /internal/**：Service JWT 认证通过即可

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

> `security.mode=mock` 是安全运行模式，与部署环境 Profile（local/dev/prod）是两个独立维度。
> 典型配置：`spring.profiles.active=local` + `security.mode=mock`。
> mock 模式下方法安全（`@PreAuthorize`）正常生效，可通过配置不同 permissions 测试鉴权逻辑。

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
示例：order:read / order:write / device:command

User ──(M:N)── Role ──(M:N)── Permission

permissions[] 登录时由 IAM 从 DB 查询后嵌入 User JWT
运行时从 Service JWT 直接读取，不查 IAM 数据库
```

---

## 5. Security-Starter 详细设计

### 5.1 核心设计决策

| 决策项                   | 方案                                                                                   |
|-----------------------|--------------------------------------------------------------------------------------|
| Authentication 载体     | `ServiceJwtAuthenticationToken extends AbstractAuthenticationToken`                  |
| 统一 Token 机制           | Gateway→微服务 与 微服务→微服务 完全相同                                                           |
| 用户上下文传递               | userId + permissions 打包进 Service JWT Claims，密码学保护                                    |
| 微服务过滤器                | `TraceIdFilter`（最先）+ `ServletServiceJwtFilter`（认证）                                   |
| JWT 配置校验              | 条件 Bean 校验（`ServiceJwtValidator` / `UserJwtValidator`），mock 模式跳过 JWT 校验              |
| 放行路径                  | 内置常量（`DEFAULT_PERMIT_PATHS`）+ yml 可扩展                                                |
| requestMatchers       | 使用 `AntPathRequestMatcher`（不依赖 Spring MVC HandlerMappingIntrospector，测试友好）           |
| @PreAuthorize         | mustache 模板生成，权限来自 Service JWT                                                       |
| @EnableMethodSecurity | 独立 `MethodSecurityConfiguration` 类，`@ConditionalOnMissingBean` 保护，service/mock 模式均激活 |

### 5.2 模块包结构

```
security-starter/src/main/java/com/jugu/propertylease/security/
├── autoconfigure/
│   ├── SecurityStarterAutoConfiguration.java    # 总入口，公共 Bean（注意：非 SecurityAutoConfiguration，避免与 Spring Boot 内置类名冲突）
│   ├── SecurityResponseUtils.java               # 统一错误响应 JSON 构建工具
│   ├── ServiceJwtValidator.java                 # service/gateway 模式启动校验 jwt.service.secret
│   ├── UserJwtValidator.java                    # gateway 模式启动校验 jwt.user.secret
│   ├── servlet/
│   │   ├── ServletSecurityAutoConfiguration.java    # mode=service 条件装配
│   │   ├── MockServletSecurityAutoConfiguration.java # mode=mock 条件装配
│   │   ├── MethodSecurityConfiguration.java         # @EnableMethodSecurity 独立类
│   │   └── SecurityHandlers.java                    # EntryPoint + AccessDeniedHandler
│   └── reactive/
│       └── ReactiveSecurityAutoConfiguration.java
├── constants/
│   └── SecurityConstants.java
├── properties/
│   ├── SecurityProperties.java                  # 含 MockUser 内嵌类
│   └── JwtProperties.java
├── token/
│   ├── JwtTokenParser.java
│   ├── ServiceTokenGenerator.java
│   ├── UserTokenPayload.java
│   └── ServiceTokenPayload.java
├── authentication/
│   └── ServiceJwtAuthenticationToken.java
├── filter/
│   ├── servlet/
│   │   ├── TraceIdFilter.java                   # TraceId → MDC，SecurityFilterChain 内最先
│   │   ├── ServletServiceJwtFilter.java
│   │   └── MockUserFilter.java                  # mock 模式注入固定用户
│   └── reactive/
│       └── ReactiveUserJwtFilter.java
├── context/
│   └── CurrentUser.java
├── authorization/
│   └── SecurityPermissionEvaluator.java
├── datapermission/
│   ├── DataPermissionContext.java
│   ├── DataPermissionInterceptor.java
│   └── DataPermissionVisitListener.java
├── interceptor/
│   └── ServiceTokenClientInterceptor.java       # 出站附加 X-Service-Token + X-Trace-Id
├── audit/
│   ├── AuditLog.java
│   ├── AuditEvent.java
│   ├── AuditLogger.java
│   └── AuditLogAspect.java
└── exception/
    └── InvalidTokenException.java
```

### 5.3 AutoConfiguration 装配矩阵

| 条件                                | 装配内容                                                                                                                                                                                    |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 始终                                | `JwtTokenParser`、`ServiceTokenGenerator`、`ServiceTokenClientInterceptor`                                                                                                                |
| `mode=service`                    | `ServiceJwtValidator`（校验 jwt.service.secret）                                                                                                                                            |
| `mode=gateway`                    | `ServiceJwtValidator`（校验 jwt.service.secret）+ `UserJwtValidator`（校验 jwt.user.secret）                                                                                                    |
| `mode=mock`                       | 不创建任何 Validator（jwt 配置可完全省略）                                                                                                                                                            |
| `SERVLET` + `mode=service`        | `TraceIdFilter`、`ServletServiceJwtFilter`、`SecurityFilterChain`（AntPathRequestMatcher）、`SecurityPermissionEvaluator`、`DataPermissionInterceptor`、`@Import(MethodSecurityConfiguration)` |
| `SERVLET` + `mode=mock`           | `TraceIdFilter`、`MockUserFilter`、`SecurityFilterChain`（全部 permitAll）、`SecurityPermissionEvaluator`、`DataPermissionInterceptor`、`@Import(MethodSecurityConfiguration)`                   |
| `REACTIVE` + `mode=gateway`       | `ReactiveUserJwtFilter`、`SecurityWebFilterChain`                                                                                                                                        |
| `@ConditionalOnBean(AuditLogger)` | `AuditLogAspect`（三种 mode 均支持）                                                                                                                                                           |

### 5.4 DEFAULT_PERMIT_PATHS

```java
// SecurityConstants.java
public static final List<String> DEFAULT_PERMIT_PATHS = List.of(
    "/actuator/**",   // 运维端点，内网访问
    "/error"          // Spring Boot error dispatch 路径，必须放行
                      // 业务异常（如 405）触发 error dispatch 后若不放行，
                      // Security 会拦截并返回 UNAUTHORIZED，掩盖真实错误码
);
```

### 5.5 SecurityProperties 配置示例

```yaml
# mode=service（生产微服务）
security:
  mode: service
  service-name: billing-service
  jwt:
    service:
      secret: ${JWT_SERVICE_SECRET}
      expiration: 300

# mode=gateway（API Gateway）
security:
  mode: gateway
  service-name: gateway
  permit-paths:
    - /api/main-service/auth/login
  jwt:
    user:
      secret: ${JWT_USER_SECRET}
      expiration: 1800
    service:
      secret: ${JWT_SERVICE_SECRET}
      expiration: 300

# mode=mock（本地开发，不需要 jwt 配置）
security:
  mode: mock
  service-name: billing-service
  mock-user:            # 默认 userId=1, permissions=[]，可不配
    user-id: 1
    permissions:
      - "lease:read"
      - "lease:write"
```

### 5.6 TraceId 在微服务侧的处理

`TraceIdFilter` 注册在 `SecurityFilterChain` 内，紧跟 `SecurityContextHolderFilter` 之后，比
`ServletServiceJwtFilter` 更早执行：

```
SecurityContextHolderFilter（≈300）→ TraceIdFilter → ... → ServletServiceJwtFilter → 业务
```

保证 `ServletServiceJwtFilter` 写 401 响应时，MDC 中已有 traceId，可携带在错误响应中。

### 5.7 security-starter 对外暴露 API

| 类 / 接口 / 注解                     | 用途                                         |
|---------------------------------|--------------------------------------------|
| `SecurityConstants`             | 内置放行路径、Header 名称等常量                        |
| `SecurityProperties`            | 配置强类型绑定                                    |
| `JwtTokenParser`                | 解析/校验 JWT                                  |
| `ServiceTokenGenerator`         | 生成 Service JWT                             |
| `ServiceTokenClientInterceptor` | RestClient 拦截器，附加 Service JWT + X-Trace-Id |
| `ServiceJwtAuthenticationToken` | Spring Security Authentication 载体          |
| `CurrentUser`                   | 获取当前 userId / permissions / isSystemCall   |
| `DataPermissionContext`         | ThreadLocal userId 容器                      |
| `DataPermissionVisitListener`   | 数据权限抽象基类                                   |
| `@AuditLog`                     | 标记需审计的方法                                   |
| `AuditLogger`                   | 审计写入接口                                     |
| `AuditEvent`                    | 审计事件 POJO                                  |
| `InvalidTokenException`         | Token 校验失败异常                               |

---

## 6. Common 模块

### 6.1 定位

全系统共用的基础能力库，不依赖任何内部模块。详见 `common-module-spec-v1.md`。

### 6.2 核心能力概览

| 能力         | 类                                                             | 说明                                                             |
|------------|---------------------------------------------------------------|----------------------------------------------------------------|
| 统一错误响应格式   | `ErrorResponse`                                               | 扁平结构 `{code, message, traceId}`，traceId 为 null 不序列化            |
| 业务异常       | `BusinessException`                                           | 携带 HttpStatus + errorCode，全系统统一基础异常                            |
| 全局异常处理     | `GlobalExceptionHandler`                                      | 继承 `ResponseEntityExceptionHandler`，统一覆盖所有 Spring MVC HTTP 类异常 |
| 函数式结果包装    | `Result<T>`                                                   | 显式处理 Feign 调用成功/失败两分支                                          |
| Feign 异常收敛 | `FeignBusinessExceptionErrorDecoder` + `FeignExceptionAspect` | 所有 Feign 失败统一转为 `BusinessException`                            |

### 6.3 统一错误响应格式

全系统所有 4xx/5xx 响应体统一为**扁平格式**，禁止使用嵌套结构：

```json
{ "code": "USER_NOT_FOUND", "message": "用户 ID 999 不存在", "traceId": "abc123" }
```

此格式适用于：微服务 GlobalExceptionHandler、security-starter
SecurityHandlers（401/403）、ReactiveUserJwtFilter（Gateway
401）、GatewayErrorWebExceptionHandler（502/504/404）、RateLimitFilter（429）。

---

## 7. 服务分层规范

### 7.1 分层结构

```
DelegateImpl（薄转接层）→ Service（业务逻辑 + 事务）→ Repository 接口 → Repository impl（jOOQ）→ MySQL
```

### 7.2 各层约定

| 层                   | 职责                                 | 关键约束                                       |
|---------------------|------------------------------------|--------------------------------------------|
| **DelegateImpl**    | 实现 Delegate；参数透传；`@Valid`          | 禁止业务逻辑；`@PreAuthorize` 由模板生成在 Delegate 接口上 |
| **Service**         | 业务编排；`@Transactional`；`@AuditLog`  | 禁止拼 SQL；可用 OpenAPI 生成的 DTO                 |
| **Repository 接口**   | 定义数据操作契约，返回 Model                  | 只定义方法签名                                    |
| **Repository impl** | jOOQ 实现；Record ↔ Model 由 Mapper 转换 | Record 不出此层；不加 `@Transactional`            |
| **Model**           | 纯 POJO，无框架注解                       | 不直接作为响应体                                   |

---

## 8. OpenAPI 契约规范（同源原则）

### 8.1 共享 schema（common-openapi.yaml）

包含 `ApiResult`、`ErrorDetail`、`PageInfo`，各服务通过相对路径 `$ref` 引用，generator 编译期
dereference 内联展开。

### 8.2 yaml 文件位置

```
{service}/src/main/resources/openapi/
├── {service}-api.yaml            # /api/v1/**，含 x-required-permission
└── {service}-internal-api.yaml   # /internal/v1/**，无 x-required-permission
```

### 8.3 @PreAuthorize 生成

`x-required-permission: "order:read"` → 生成：

```java
@PreAuthorize("hasPermission(null, 'order:read')")
default ResponseEntity<OrderResponse> getOrder(...) { ... }
```

⚠️ **C-40**：`vendorExtensions.x-required-permission` key 格式需首次运行 generator 后验证。

---

## 9. Clients 模块规范

clients 通过相对路径引用各服务 `*-internal-api.yaml`，只有一份，无编译顺序依赖。
`BillingClientAutoConfiguration` 条件：`@ConditionalOnBean(ServiceTokenClientInterceptor.class)`。

```yaml
services:
  billing:
    url: http://billing-service:8081
  device:
    url: http://device-service:8082
```

---

## 10. 统一响应结构规范

| 接口             | 响应方式                                | yaml schema                 |
|----------------|-------------------------------------|-----------------------------|
| `/api/**`      | `ResponseBodyAdvice` 包装 `Result<T>` | `{Resource}ApiResult`（含包装层） |
| `/internal/**` | 直接 `ResponseEntity<T>`              | 直接数据 schema                 |

---

## 11. 数据访问层规范（Liquibase + jOOQ 离线模式）

### 11.1 ID 生成策略

- **当前**：MySQL `AUTO_INCREMENT BIGINT`
- **后续**：hutool `IdUtil.getSnowflake()`，只改 `IdGenerator` 工具类和 changelog

### 11.2 jOOQ VisitListener 注册方式

各服务通过 `JooqConfig` 手动注册（⚠️ C-43 待确认 `DefaultConfigurationCustomizer` API）：

```java
@Configuration @RequiredArgsConstructor
public class JooqConfig {
    private final BillingDataPermissionVisitListener visitListener;

    @Bean
    public DefaultConfigurationCustomizer jooqCustomizer() {
        return config -> config.set(new VisitListenerProvider[]{ () -> visitListener });
    }
}
```

### 11.3 数据库字段约定

| 约定    | 规则                                                        |
|-------|-----------------------------------------------------------|
| 主键    | `BIGINT AUTO_INCREMENT`（当前），后续改 Snowflake                 |
| 软删除   | `is_deleted TINYINT(1) DEFAULT 0` + `deleted_at DATETIME` |
| 审计字段  | 每表必须有 `created_at`、`updated_at`、`created_by`、`updated_by` |
| 乐观锁   | 并发写场景加 `version BIGINT DEFAULT 0`                         |
| 命名    | `snake_case`                                              |
| Redis | 仅业务缓存；Repository 层禁止封装 Redis                              |

---

## 12. 错误处理规范

### 12.1 异常分层

| 层       | 异常类型                                    | HTTP 状态     | 处理位置                                             |
|---------|-----------------------------------------|-------------|--------------------------------------------------|
| Gateway | `InvalidTokenException`（User JWT 无效）    | 401         | `ReactiveUserJwtFilter` 直接写响应                    |
| Gateway | 路由/下游异常                                 | 404/502/504 | `GatewayErrorWebExceptionHandler`                |
| Gateway | 限流                                      | 429         | `RateLimitFilter` 直接写响应                          |
| 微服务     | `InvalidTokenException`（Service JWT 无效） | 401         | `ServletServiceJwtFilter` 直接写响应                  |
| 微服务     | `AccessDeniedException`                 | 403         | `SecurityAccessDeniedHandler`                    |
| 微服务     | `BusinessException`                     | 业务自带        | `GlobalExceptionHandler`                         |
| 微服务     | Spring MVC HTTP 类异常                     | 框架自带        | `GlobalExceptionHandler.handleExceptionInternal` |
| 微服务     | `Exception`（兜底）                         | 500         | `GlobalExceptionHandler`                         |

### 12.2 统一响应格式

所有错误响应使用扁平 `ErrorResponse`：

```json
{ "code": "USER_NOT_FOUND", "message": "...", "traceId": "abc123" }
```

traceId 读取：

- 微服务：从 MDC 读取（`TraceIdFilter` 注入）
- Gateway Reactive 侧：从 `exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID)` 读取（
  `ReactiveUserJwtFilter.ensureTraceId` 写入），兜底从 request header 读

### 12.3 错误码格式

```
{SERVICE}_{RESOURCE}_{REASON}
框架级：COMMON_HTTP_{statusCode}（如 COMMON_HTTP_404、COMMON_HTTP_405）
校验失败：COMMON_REQUEST_VALIDATION_FAILED
请求体错误：COMMON_REQUEST_BODY_UNREADABLE
内部错误：INTERNAL_SERVER_ERROR
Gateway：GATEWAY_ROUTE_NOT_FOUND / GATEWAY_DOWNSTREAM_UNAVAILABLE 等
认证：IAM_TOKEN_EXPIRED / IAM_TOKEN_INVALID / IAM_TOKEN_MALFORMED / IAM_TOKEN_MISSING
```

---

## 13. 服务间通信规范

### 13.1 调用方向

```
main-service → billing-service /internal/v1/**  ✅
main-service → device-service  /internal/v1/**  ✅
其他方向：当前禁止
```

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
- **⚠️ C-31**：`beforeJson` 获取方式待确认

---

## 15. 可观测性规范（TraceId 全链路）

### 15.1 TraceId 生成与传递规则

| 场景                           | 生成方                                             | 传递方式                                    |
|------------------------------|-------------------------------------------------|-----------------------------------------|
| 外部请求进入 Gateway（无 X-Trace-Id） | `ReactiveUserJwtFilter.ensureTraceId()` 生成 UUID | 写入 request header + exchange.attributes |
| 外部请求携带 X-Trace-Id            | 原样透传                                            | 同上（写入 attributes 供 ErrorHandler 读取）     |
| 微服务收到请求                      | `TraceIdFilter` 读取 X-Trace-Id Header            | 写入 MDC（key="traceId"），请求结束 finally 清理   |
| 直连微服务（无 Gateway）             | `TraceIdFilter` 生成 UUID                         | 写入 MDC                                  |
| 服务 A → 服务 B（出站）              | `ServiceTokenClientInterceptor`                 | 从 MDC 读 traceId → 设置 X-Trace-Id Header  |

### 15.2 traceId 在错误响应中的保证

修复三个 Bug 后，进入 Spring 应用层的请求，无论在哪个环节失败，ErrorResponse 中均携带 traceId：

| 错误场景                     | traceId 来源                                                     |
|--------------------------|----------------------------------------------------------------|
| Gateway JWT 无效 → 401     | `exchange.getAttribute(ATTR_TRACE_ID)`（ensureTraceId 已写入）      |
| Gateway 限流 → 429         | `exchange.getAttribute(ATTR_TRACE_ID)`（兜底 header）              |
| Gateway 下游不可达 → 502      | `exchange.getAttribute(ATTR_TRACE_ID)`（解决 originalExchange 问题） |
| Gateway 路由未找到 → 404      | 同上                                                             |
| 微服务 Service JWT 无效 → 401 | MDC（TraceIdFilter 已注入）                                         |
| 微服务方法鉴权失败 → 403          | MDC                                                            |
| 微服务业务异常                  | MDC                                                            |
| 微服务 Spring MVC 框架异常      | MDC                                                            |
| 微服务未预期异常 → 500           | MDC                                                            |

**已知边界（应用层无法处理）：**

- Tomcat 层失败（请求体过大、连接数耗尽等）：Filter 链未运行，无 traceId
- Reactor Netty 层失败（TLS 握手失败、HTTP 解析错误等）：WebFilter 未运行，无 traceId

### 15.3 traceId 在 Gateway ErrorWebExceptionHandler 的实现原理

`GatewayErrorWebExceptionHandler` 收到的是 `originalExchange`（Spring WebFlux 机制），无法访问
`ReactiveUserJwtFilter` mutate 后的 request header。但 `exchange.mutate().request().build()` 构建新
exchange 时，`attributes` map 指向同一实例，因此：

```java
// ReactiveUserJwtFilter.ensureTraceId() 写入 attributes（ATTR_TRACE_ID 公共常量）
exchange.getAttributes().put(ReactiveUserJwtFilter.ATTR_TRACE_ID, traceId);

// GatewayErrorWebExceptionHandler 优先从 attributes 读
String traceId = exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID);
if (traceId == null) {
    traceId = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID); // 兜底
}
```

### 15.4 Actuator 与 Metrics

Micrometer Tracing + Brave。暴露 `/actuator/health`、`/actuator/info`、`/actuator/prometheus`
，管理端口独立（Gateway 8090，微服务各自配置）。

---

## 16. 包命名与目录结构

### 16.1 包命名规则

| 模块                             | 根包                                      |
|--------------------------------|-----------------------------------------|
| security-starter               | `com.jugu.propertylease.security`       |
| common                         | `com.jugu.propertylease.common`         |
| gateway                        | `com.jugu.propertylease.gateway`        |
| main-service                   | `com.jugu.propertylease.main`           |
| billing-service                | `com.jugu.propertylease.billing`        |
| device-service                 | `com.jugu.propertylease.device`         |
| clients/billing-service-client | `com.jugu.propertylease.client.billing` |

### 16.2 微服务包结构（以 billing-service 为例）

```
billing-service/src/main/java/com/jugu/propertylease/billing/
├── BillingServiceApplication.java
├── controller/
│   ├── OrdersApiDelegateImpl.java
│   └── OrdersInternalApiDelegateImpl.java
├── service/OrderService.java
├── repository/
│   ├── OrderRepository.java
│   └── impl/OrderRepositoryImpl.java
├── model/Order.java
├── mapper/OrderRecordMapper.java
├── audit/BillingAuditLoggerImpl.java
├── datapermission/BillingDataPermissionVisitListener.java
├── config/
│   ├── BillingConfig.java
│   └── JooqConfig.java
└── exception/BillingExceptionHandler.java
```

---

## 17. 模块间依赖矩阵

| 模块                 | 可依赖                                                                          | 禁止依赖                |
|--------------------|------------------------------------------------------------------------------|---------------------|
| `common`           | JDK only + Spring Boot（web/validation/aop）                                   | 任何内部模块              |
| `security-starter` | `common`、Spring Boot autoconfigure、Spring Security、JJWT                      | 任何微服务模块             |
| `clients/*`        | `common`、`security-starter`                                                  | 任何微服务模块             |
| `gateway`          | `security-starter`、`common`                                                  | 微服务业务模块             |
| `main-service`     | `security-starter`、`common`、`billing-service-client`、`device-service-client` | billing/device 业务代码 |
| `billing-service`  | `security-starter`、`common`                                                  | main/device 任何模块    |
| `device-service`   | `security-starter`、`common`                                                  | main/billing 任何模块   |

---

## 18. 关键约束速查

| #    | 约束                                                       | 说明                                                                                                          |
|------|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| C-01 | 简单分层                                                     | DelegateImpl → Service → Repository，无 DDD                                                                   |
| C-02 | DelegateImpl 只做转接                                        | Service 不直接实现 Delegate                                                                                      |
| C-03 | Model 无框架注解                                              | 纯 POJO                                                                                                      |
| C-04 | 禁止 ORM                                                   | 只用 jOOQ                                                                                                     |
| C-05 | jOOQ 只在 Repository impl                                  | DSLContext/Record 不出 impl                                                                                   |
| C-06 | jOOQ 离线生成                                                | 基于 Liquibase changelog                                                                                      |
| C-07 | @Transactional 只在 Service 层                              | Repository 不加                                                                                               |
| C-08 | OpenAPI 同源                                               | 必须实现生成的 Delegate                                                                                            |
| C-09 | Liquibase 同源                                             | 禁止手动改表                                                                                                      |
| C-10 | 共享 schema 同源                                             | 只在 common-openapi.yaml 定义                                                                                   |
| C-11 | 接口双轨制                                                    | /api/** 含 Permission 鉴权；/internal/** 只做 Service JWT 认证                                                      |
| C-12 | 响应结构双轨制                                                  | /api/** 包装 Result<T>；/internal/** 直接 ResponseEntity<T>                                                      |
| C-13 | clients yaml 同源                                          | 相对路径引用，只有一份                                                                                                 |
| C-14 | 禁止手写服务间 HTTP 调用                                          | 通过 clients 生成的 Client                                                                                       |
| C-15 | clients Bean 自动注册                                        | AutoConfiguration + @ConditionalOnBean(ServiceTokenClientInterceptor)                                       |
| C-16 | 服务地址固定配置                                                 | `services.{name}.url`                                                                                       |
| C-17 | 统一 Service JWT                                           | Gateway→微服务 与 微服务→微服务 完全相同机制                                                                                |
| C-18 | User JWT 不进内网                                            | Gateway 移除 Authorization Header，换发 Service JWT                                                              |
| C-19 | 用户上下文打包进 Service JWT                                     | userId + permissions 密码学保护，不信任明文 Header                                                                     |
| C-20 | Service JWT 本地自签                                         | 每次调用前生成，携带当前 userId + permissions                                                                           |
| C-21 | 密钥隔离                                                     | user.secret（Gateway 专用）≠ service.secret（全系统共用），均 HS256                                                      |
| C-22 | security-starter 双栈                                      | SERVLET/REACTIVE 自动检测                                                                                       |
| C-23 | 权限数据来自 Service JWT                                       | 运行时不查 IAM 数据库                                                                                               |
| C-24 | @PreAuthorize 自动生成                                       | mustache 模板从 x-required-permission 生成                                                                       |
| C-25 | 鉴权只在对外接口                                                 | /internal/** 无 Permission 鉴权                                                                                |
| C-26 | 数据权限 VisitListener                                       | SELECT/UPDATE/DELETE 均注入；INSERT 不注入                                                                         |
| C-27 | VisitListener 手动注册                                       | 各服务通过 JooqConfig 手动 set 到 DSLContext                                                                        |
| C-28 | 审计服务自治                                                   | 每服务自己的 audit_logs，@AuditLog + AOP 自动写入                                                                      |
| C-29 | 审计事务隔离                                                   | AuditLogger 实现用 REQUIRES_NEW，写入失败静默忽略                                                                       |
| C-30 | @EnableMethodSecurity 唯一                                 | 独立 MethodSecurityConfiguration + @ConditionalOnMissingBean 保护，service/mock 模式均激活                            |
| C-31 | ⚠️ 待确认                                                   | @AuditLog 的 beforeJson 获取方式                                                                                 |
| C-32 | ⚠️ 待确认                                                   | VisitListener 表识别辅助方法                                                                                       |
| C-33 | 主键当前用自增                                                  | 后续替换 Snowflake                                                                                              |
| C-34 | Redis 仅业务缓存                                              | Repository 层禁止封装 Redis                                                                                      |
| C-35 | 审计字段必须                                                   | 每业务表必须有 created_at/updated_at/created_by/updated_by                                                         |
| C-36 | 跨服务不跨库                                                   | 禁止跨数据库 JOIN                                                                                                 |
| C-37 | Outbox 最终一致                                              | 跨服务写走 Outbox；接收方接口幂等                                                                                        |
| C-38 | TraceId 全链路                                              | Gateway 生成，X-Trace-Id Header 传递，MDC 注入；出站调用透传                                                               |
| C-39 | 放行路径分层管理                                                 | /actuator/** 和 /error 由 SecurityConstants 内置；服务特有路径在 yml 配置                                                 |
| C-40 | ⚠️ 待确认                                                   | mustache 模板 vendorExtensions key 格式                                                                         |
| C-41 | mode 三个值                                                 | gateway（WebFlux）/ service（Servlet 生产）/ mock（Servlet 本地开发）                                                   |
| C-42 | 放行路径双端对齐                                                 | Gateway permit-paths 中路由到微服务的路径，该微服务 permit-paths 中必须包含相同路径                                                 |
| C-43 | ⚠️ 待确认                                                   | DefaultConfigurationCustomizer API                                                                          |
| C-44 | Authentication 载体                                        | ServiceJwtAuthenticationToken extends AbstractAuthenticationToken，不使用 UserDetails                           |
| C-45 | jwt 配置条件校验                                               | mode=service/gateway 时由条件 Bean（ServiceJwtValidator/UserJwtValidator）校验；mode=mock 跳过                         |
| C-46 | AuditLogAspect 在 @Transactional 外层                       | @Order(LOWEST_PRECEDENCE-1)                                                                                 |
| C-47 | AntPathRequestMatcher                                    | SecurityFilterChain 使用 AntPathRequestMatcher，不用 MvcRequestMatcher（后者依赖 HandlerMappingIntrospector，测试不友好）    |
| C-48 | 错误响应扁平格式                                                 | 全系统统一 `{code, message, traceId}`，禁止嵌套结构                                                                     |
| C-49 | traceId 写入 exchange.attributes                           | ReactiveUserJwtFilter 将 traceId 同时写入 request header（下游透传）和 exchange.attributes（ErrorWebExceptionHandler 读取） |
| C-50 | mock 模式与部署 Profile 独立                                    | security.mode=mock 是安全模式配置，与 spring.profiles.active=local/dev/prod 是两个独立维度                                  |
| C-51 | GlobalExceptionHandler 继承 ResponseEntityExceptionHandler | 覆写 handleExceptionInternal 一处定制，Spring 新增 HTTP 类异常自动覆盖                                                      |
| C-52 | ServiceTokenClientInterceptor 透传 X-Trace-Id              | 从 MDC 读取 traceId，附加到出站请求 Header，保证多服务调用链 traceId 一致                                                         |

---

## 19. 待确认事项

| #    | 事项                                                                           | 影响范围                        | 优先级 |
|------|------------------------------------------------------------------------------|-----------------------------|-----|
| C-31 | @AuditLog 的 beforeJson 获取方式                                                  | AuditLogAspect              | 低   |
| C-32 | DataPermissionVisitListener 表识别辅助方法                                          | DataPermissionVisitListener | 低   |
| C-40 | mustache 模板 vendorExtensions key 格式，首次运行后验证                                  | apiDelegate.mustache        | 高   |
| C-43 | DefaultConfigurationCustomizer API 在 Spring Boot 3.2 + jOOQ 3.19 中的准确包名和方法签名 | JooqConfig                  | 中   |
