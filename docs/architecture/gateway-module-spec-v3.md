# Gateway 模块实现 Spec

> 版本：v3.0
> 根包：`com.jugu.propertylease.gateway`
> 运行形态：WebFlux / Reactive
> Security 模式：`security.mode=gateway`
>
> **v3.0 变更：**
> - GW-Q-8 已解决：main-service Controller 路径确认，C-42 双端对齐示例更新为通配符格式
> - 放行路径示例从 `/auth/login` 精确路径改为 `/auth/**` 通配符，覆盖全部认证端点
> - 补充 /internal/v1/** 不经 Gateway 的说明
> - 补充 User JWT 签发方说明（main-service IAM 模块）

---

## 目录

1. [路由配置（GW-1）](#1-路由配置gw-1)
2. [过滤器体系（GW-2）](#2-过滤器体系gw-2)
3. [统一错误响应（GW-3）](#3-统一错误响应gw-3)
4. [CORS 配置（GW-4）](#4-cors-配置gw-4)
5. [应用配置（GW-5）](#5-应用配置gw-5)
6. [Actuator 与健康检查（GW-6）](#6-actuator-与健康检查gw-6)
7. [包结构总览](#7-包结构总览)
8. [关键约束速查](#8-关键约束速查)
9. [待确认事项](#9-待确认事项)

---

## 1. 路由配置（GW-1）

### 1.1 路由规则

外部前端统一使用 `/api/{service-name}/**` 访问，StripPrefix=2，下游收到剩余路径：

```
外部：/api/main-service/auth/login/password   → main-service:8081   /auth/login/password
外部：/api/main-service/iam/users             → main-service:8081   /iam/users
外部：/api/billing-service/orders             → billing-service:8082 /orders
外部：/api/device-service/commands            → device-service:8083  /commands

注意：/internal/v1/** 接口不经 Gateway，由微服务间直接调用（携带 Service JWT）
```

### 1.2 各服务标准端口

| 服务              | 端口   |
|-----------------|------|
| gateway         | 8080 |
| main-service    | 8081 |
| billing-service | 8082 |
| device-service  | 8083 |

### 1.3 C-42 放行路径双端对齐（GW-Q-8 已解决）

```
Gateway permit-paths（外部路径）:       /api/main-service/auth/**
main-service permit-paths（Strip 后）:  /auth/**
                                        /internal/v1/**（内部接口不经 Gateway，单独放行）

覆盖的认证端点：
  /auth/login/password
  /auth/wechat/miniprogram/login
  /auth/wechat/miniprogram/bind
  /auth/wechat/web/login
  /auth/wechat/web/bind
  /auth/refresh
  /auth/logout

规则：Gateway 配外部路径（含 /api/{service-name} 前缀），微服务配 StripPrefix 后路径。
两端均不携带 Service JWT，微服务侧若不放行则返回 401。
```

### 1.4 边界约束

- 路由地址禁止在代码中硬编码，统一通过 `GatewayProperties` 注入
- 新增下游服务只需在 `gateway.routes.*` 添加配置并在 `RouteConfig` 增加一条路由

---

## 2. 过滤器体系（GW-2）

### 2.1 过滤器分层与执行顺序

Spring Cloud Gateway 中存在两个独立的过滤器层级：

| 层级                 | 类型                                   | 执行时机                     |
|--------------------|--------------------------------------|--------------------------|
| **WebFilter 层**    | `WebFilter`（Spring WebFlux）          | 路由匹配**之前**执行             |
| **GlobalFilter 层** | `GlobalFilter`（Spring Cloud Gateway） | 路由匹配**之后**，转发到下游**之前**执行 |

**WebFilter 总是先于 GlobalFilter 运行。** Order 值在各自层级内排序，跨层级无法直接比较。

### 2.2 实际执行顺序

```
┌─ WebFilter 层（路由前）─────────────────────────────────────┐
│  ReactiveUserJwtFilter（WebFilter, order=-100）             │
│    ① ensureTraceId（生成/透传 traceId，写 header + attributes）│
│    ② 放行路径判断                                            │
│    ③ 验证 User JWT → 生成 Service JWT → 移除 Authorization  │
└─────────────────────────────────────────────────────────────┘
         ↓ 路由匹配
┌─ GlobalFilter 层（路由后）──────────────────────────────────┐
│  SecurityHeaderCleanFilter（GlobalFilter, order=-200）      │
│    清洗外部不可信 Header（X-Service-Token 等）               │
│  RateLimitFilter（GlobalFilter, order=-150）                 │
│    限流，429 响应携带 traceId（attributes → header → UUID）  │
│  RequestLoggingFilter（GlobalFilter, order=-50）             │
│    记录请求日志，traceId 从 request header 读                │
└─────────────────────────────────────────────────────────────┘
         ↓ 转发到下游微服务
```

**GlobalFilter 内部的 order 含义：** 数字越小优先级越高，-200 先于 -150 先于 -50。

### 2.3 SecurityHeaderCleanFilter（GW-2-A）

**职责**：清洗外部请求中不可信任的 Header，防止外部伪造内部凭证。

**执行时机**：GlobalFilter，在路由匹配后、转发前运行。此时 `ReactiveUserJwtFilter`（WebFilter）已经将合法的
Service JWT 写入 exchange。SecurityHeaderCleanFilter 清洗的是**原始请求中可能存在的伪造 Header**，不影响
ReactiveUserJwtFilter 已生成并设置在 mutated exchange 上的 Service JWT。

**清洗规则**：配置驱动，通过 `gateway.security.strip-request-headers` 读取，当前默认：

```yaml
gateway:
  security:
    strip-request-headers:
      - X-Service-Token    # 防止外部伪造服务间认证凭证
```

### 2.4 RateLimitFilter（GW-2-B）

**职责**：按 IP 维度限流，超限返回 429。

**traceId 读取**：执行时 ReactiveUserJwtFilter 已将 traceId 写入 `exchange.getAttributes()`（
`ATTR_TRACE_ID` 常量），RateLimitFilter 按“attributes → header → UUID 生成”顺序兜底，确保 429 响应
始终包含 traceId：

```java
String traceId = exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID);
if (traceId == null) {
    // 兜底1：Client 请求本身携带了 X-Trace-Id
    traceId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID);
}
if (traceId == null || traceId.isBlank()) {
    // 兜底2：最终生成 traceId，保证 429 错误响应可追踪
    traceId = UUID.randomUUID().toString();
}
// 429 响应体：扁平格式 {"code":"GATEWAY_RATE_LIMIT_EXCEEDED","message":"...","traceId":"..."}
```

**限流参数**：

| 参数            | 默认值  | 说明                 |
|---------------|------|--------------------|
| `capacity`    | 100  | 令牌桶容量              |
| `refill-rate` | 20   | 每秒补充令牌数            |
| `enabled`     | true | 是否开启（local 环境建议关闭） |

### 2.5 RequestLoggingFilter（GW-2-C）

**执行顺序**：GlobalFilter order=-50，在 ReactiveUserJwtFilter（WebFilter）之后，traceId 已在 request
header 中：

```java
String traceId = request.getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID);
// 日志格式：[GATEWAY] traceId=abc123 method=POST path=/api/... ip=1.2.3.4 status=200 duration=45ms
```

---

## 3. 统一错误响应（GW-3）

### 3.1 错误响应格式

所有 Gateway 层错误响应使用**扁平 ErrorResponse 格式**：

```json
{ "code": "GATEWAY_ROUTE_NOT_FOUND", "message": "请求的路径不存在", "traceId": "abc123" }
```

### 3.2 错误码映射

| 场景             | HTTP | 错误码                                         |
|----------------|------|---------------------------------------------|
| User JWT 缺失/无效 | 401  | `IAM_TOKEN_MISSING` / `IAM_TOKEN_EXPIRED` 等 |
| 限流             | 429  | `GATEWAY_RATE_LIMIT_EXCEEDED`               |
| 路由未找到          | 404  | `GATEWAY_ROUTE_NOT_FOUND`                   |
| 下游不可达          | 502  | `GATEWAY_DOWNSTREAM_UNAVAILABLE`            |
| 下游超时           | 504  | `GATEWAY_DOWNSTREAM_TIMEOUT`                |

401 由 `ReactiveUserJwtFilter` 直接写响应；429 由 `RateLimitFilter` 直接写响应；404/502/504 由
`GatewayErrorWebExceptionHandler` 处理。

### 3.3 GatewayErrorWebExceptionHandler（@Order(-2)）

**traceId 读取问题及解决方案：**

`ErrorWebExceptionHandler.handle(exchange, ex)` 收到的是 `originalExchange`（Spring WebFlux 机制，非
ReactiveUserJwtFilter mutate 后的 exchange），无法直接访问 mutated request header。

解决方案：`exchange.mutate()` 构建新 exchange 时，`attributes` map 指向同一实例，原始与 mutated
exchange 共享同一 attributes。因此优先从 `attributes` 读 traceId：

```java
String traceId = exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID);  // 优先
if (traceId == null) {
    // 兜底：Client 原始请求本身携带了 X-Trace-Id
    traceId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID);
}
```

**下游错误透传原则**：`GatewayErrorWebExceptionHandler` 只处理 Gateway 自身异常，下游微服务正常返回的
4xx/5xx 直接透传，不二次包装。

---

## 4. CORS 配置（GW-4）

CORS 统一在 Gateway 处理，下游微服务禁止配置 CORS（避免重复 Header）。

| 配置项                | 值                                   |
|--------------------|-------------------------------------|
| `allowedOrigins`   | 从 `gateway.cors.allowed-origins` 读取 |
| `allowedMethods`   | `*`                                 |
| `allowedHeaders`   | `*`                                 |
| `allowCredentials` | `false`                             |
| `maxAge`           | 3600s                               |

---

## 5. 应用配置（GW-5）

### 5.1 security.* 配置

```yaml
security:
  mode: gateway
  service-name: gateway
  permit-paths:
    - /api/main-service/auth/**     # 对应 main-service /auth/**（全部认证端点）
  jwt:
    user:
      secret: ${JWT_USER_SECRET}    # ⚠️ 必须与 main-service jwt.user.secret 完全相同
      expiration: 1800              # User JWT 由 main-service IAM 的 UserJwtIssuer 签发
    service:
      secret: ${JWT_SERVICE_SECRET}
      expiration: 300
```

### 5.2 三环境关键差异

| 配置项                                                | local                     | dev                       | prod                           |
|----------------------------------------------------|---------------------------|---------------------------|--------------------------------|
| `gateway.routes.*.url`                             | `http://localhost:{port}` | `http://localhost:{port}` | `http://{service-name}:{port}` |
| `gateway.rate-limit.enabled`                       | false（建议关闭）               | true                      | true                           |
| `security.jwt.*.secret`                            | 明文测试密钥                    | 明文测试密钥                    | 环境变量注入                         |
| `spring.cloud.gateway.httpclient.response-timeout` | 30s                       | 10s                       | 10s                            |

---

## 6. Actuator 与健康检查（GW-6）

管理端口 8090 独立，所有环境一致：

```yaml
management:
  server:
    port: 8090
  endpoints:
    web:
      exposure:
        include: health, info, metrics, gateway
```

prod/dev docker compose 不对外映射 8090 端口（内网访问）。

---

## 7. 包结构总览

```
gateway/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/jugu/propertylease/gateway/
    │   │   ├── GatewayApplication.java
    │   │   ├── config/
    │   │   │   ├── GatewayProperties.java
    │   │   │   ├── RouteConfig.java
    │   │   │   └── CorsConfig.java
    │   │   ├── filter/
    │   │   │   ├── SecurityHeaderCleanFilter.java   # GlobalFilter, order=-200
    │   │   │   ├── RateLimitFilter.java             # GlobalFilter, order=-150
    │   │   │   ├── RequestLoggingFilter.java        # GlobalFilter, order=-50
    │   │   │   └── ratelimit/
    │   │   │       ├── RateLimitKeyResolver.java
    │   │   │       └── IpRateLimitKeyResolver.java
    │   │   └── error/
    │   │       └── GatewayErrorWebExceptionHandler.java  # @Order(-2)
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-dev.yml
    │       └── application-prod.yml
    └── test/...
```

> `ReactiveUserJwtFilter`（WebFilter, order=-100）由 security-starter 提供，
> 不在 gateway 包中，但是 Gateway 过滤器体系的核心组件。

---

## 8. 关键约束速查

| #       | 约束                         | 说明                                                                                   |
|---------|----------------------------|--------------------------------------------------------------------------------------|
| GW-C-01 | 路由地址配置驱动                   | 禁止代码硬编码                                                                              |
| GW-C-02 | StripPrefix=2 统一           | 所有路由统一去掉 `/api/{service-name}`                                                       |
| GW-C-03 | C-42 对齐                    | Gateway 配外部路径（通配符）`/api/main-service/auth/**`，微服务配 Strip 后路径 `/auth/**`              |
| GW-C-04 | User JWT 不进内网              | ReactiveUserJwtFilter 移除 Authorization Header                                        |
| GW-C-05 | X-Service-Token 清洗         | SecurityHeaderCleanFilter（GlobalFilter）清洗外部伪造 Header                                 |
| GW-C-06 | CORS 只在 Gateway            | 下游微服务禁止配置 CORS                                                                       |
| GW-C-07 | 下游错误不包装                    | GatewayErrorWebExceptionHandler 只处理 Gateway 自身异常                                     |
| GW-C-08 | Actuator 管理端口隔离            | 所有环境统一 8090，prod/dev 不对外映射                                                           |
| GW-C-09 | gateway.* 强类型绑定            | 禁止在 Gateway 内使用 @Value 读取 gateway.*                                                  |
| GW-C-10 | 限流可扩展                      | RateLimitKeyResolver 接口抽象                                                            |
| GW-C-11 | WebFilter 先于 GlobalFilter  | ReactiveUserJwtFilter（WebFilter）在所有 GlobalFilter 之前运行                                |
| GW-C-12 | traceId 写入 attributes      | ReactiveUserJwtFilter 将 traceId 写入 exchange.attributes，供 ErrorWebExceptionHandler 读取 |
| GW-C-13 | 错误响应扁平格式                   | 全部使用 `{code, message, traceId}`，复用 SecurityResponseUtils                             |
| GW-C-14 | /internal/v1/** 不经 Gateway | 内部接口由微服务间直接调用，Gateway 不路由此前缀                                                         |
| GW-C-15 | jwt.user.secret 两端一致       | Gateway 验证 User JWT 用的 secret 必须与 main-service IAM UserJwtIssuer 签发用的 secret 完全相同    |

---

## 9. 待确认事项

| #      | 事项                                            | 影响模块      | 优先级 | 状态                                                                           |
|--------|-----------------------------------------------|-----------|-----|------------------------------------------------------------------------------|
| GW-Q-6 | 生产前端域名（`gateway.cors.allowed-origins` prod 值） | GW-4、GW-5 | 低   | 待确认                                                                          |
| GW-Q-8 | main-service Controller 路径确认，保证 C-42 双端对齐     | GW-1      | 中   | ✅ 已解决：permit-paths 更新为 `/api/main-service/auth/**`，StripPrefix 后为 `/auth/**` |
