# 项目交接文档 — 供新会话继续工作

> 项目：property-lease-pro / backend
> 技术栈：Spring Boot 3.2.0 / Spring Cloud 2023.0.0 / Java 17 / MySQL / Redis / jOOQ / Liquibase /
> Maven
> 交接时间：2026-03-16

---

## 一、项目背景

多微服务租房管理平台，当前处于**基础设施层建设阶段**。

```
本地路径：D:\workdir\demo\property-lease-pro\backend\
Maven 仓库：C:\Users\A\.m2\repository
OS：Windows 11 + IDEA 2025.3
```

---

## 二、已完成模块（代码 + 测试均通过）

### 2.1 common 模块 ✅

**包根：** `com.jugu.propertylease.common`

**类清单：**

```
exception/
  ErrorResponse.java          统一错误响应体，扁平格式 {code, message, traceId}，@JsonInclude(NON_NULL)
  BusinessException.java      业务异常，持有 HttpStatus + errorCode，提供 toErrorResponse(traceId)
feign/
  FeignBusinessExceptionErrorDecoder.java   有响应的 Feign 失败 → BusinessException
  FeignExceptionAspect.java                 RetryableException → BusinessException(503)
result/
  Result.java                 函数式结果包装，of/ok/fail/onFailure/getOrElseGet
web/
  GlobalExceptionHandler.java 继承 ResponseEntityExceptionHandler，覆写 handleExceptionInternal
autoconfigure/
  CommonAutoConfiguration.java      @ConditionalOnWebApplication(SERVLET)，注册 GlobalExceptionHandler
  CommonFeignAutoConfiguration.java @ConditionalOnClass(FeignClient)，注册 ErrorDecoder + Aspect
```

**关键设计决策：**

- `ErrorResponse` 扁平格式，**禁止** `{"success":false,"error":{...}}` 嵌套结构
- `GlobalExceptionHandler extends ResponseEntityExceptionHandler`，Spring MVC HTTP 类异常通过覆写
  `handleExceptionInternal` 统一处理，errorCode 格式 `COMMON_HTTP_{statusCode}`
- `MethodArgumentNotValidException` 单独覆写（聚合字段错误）
- `HttpMessageNotReadableException` 单独覆写（隐藏 Jackson 细节）
- `ConstraintViolationException` 单独 `@ExceptionHandler`（来自 Bean Validation，不继承 Spring 体系）
- `NoResourceFoundException`（Boot 3.2 的 404）是
  `ServletException implements org.springframework.web.ErrorResponse`，**不是**
  `ResponseStatusException`，通过 `handleExceptionInternal` 自动覆盖
- 注意两个同名类：`org.springframework.web.ErrorResponse`（Spring 接口）vs
  `com.jugu.propertylease.common.exception.ErrorResponse`（本项目类）

**测试：** 48 个测试全部通过（最后一轮修复 `MethodArgumentNotValidTests` 中
`BeanPropertyBindingResult` → `MapBindingResult`）

---

### 2.2 security-starter 模块 ✅

**包根：** `com.jugu.propertylease.security`

**类清单：**

```
constants/SecurityConstants.java
  DEFAULT_PERMIT_PATHS = ["/actuator/**", "/error"]
  HEADER_SERVICE_TOKEN, HEADER_TRACE_ID, CLAIM_USER_ID, CLAIM_PERMISSIONS

properties/
  SecurityProperties.java      mode(gateway|service|mock) + serviceName + permitPaths + jwt + MockUser
  JwtProperties.java           secret + expiration

token/
  JwtTokenParser.java          parseUserToken / parseServiceToken，无 Spring 依赖
  ServiceTokenGenerator.java   generate(...)，无 Spring 依赖
  UserTokenPayload.java        record
  ServiceTokenPayload.java     record

exception/InvalidTokenException.java
  extends AuthenticationException
  常量：TOKEN_EXPIRED/TOKEN_INVALID/TOKEN_MALFORMED/TOKEN_MISSING

authentication/ServiceJwtAuthenticationToken.java
  extends AbstractAuthenticationToken
  工厂方法：ofUser(userId, callerName, permissions) / ofSystem(serviceName)

context/CurrentUser.java
  静态工具：getCurrentUserId / getCallerName / getPermissions / isAuthenticated / isSystemCall

authorization/SecurityPermissionEvaluator.java
  implements PermissionEvaluator

filter/servlet/
  TraceIdFilter.java           读/生成 X-Trace-Id → MDC("traceId")，finally MDC.remove
  ServletServiceJwtFilter.java shouldNotFilter + 验证 Service JWT + 直接写401响应（不抛异常）
  MockUserFilter.java          mode=mock 时注入 MockUser 到 SecurityContext

filter/reactive/
  ReactiveUserJwtFilter.java   WebFilter order=-100
    public static final String ATTR_TRACE_ID = "traceId"  ← 关键：供ErrorHandler读取
    ensureTraceId() 同时写入 request header 和 exchange.attributes
    write401 从 exchange.getAttribute(ATTR_TRACE_ID) 读 traceId

interceptor/ServiceTokenClientInterceptor.java
  出站：先从 MDC 透传 X-Trace-Id，再附加 X-Service-Token
  mock 模式下 jwt.service==null 时跳过 Token，但仍透传 traceId

datapermission/
  DataPermissionContext.java   ThreadLocal<Long>，set/get/clear
  DataPermissionInterceptor.java   preHandle 设 userId，afterCompletion 清理
  DataPermissionVisitListener.java 抽象类，实现 VisitListener

audit/
  AuditLog.java                @Annotation，action + resource + resourceId
  AuditEvent.java              Lombok @Builder
  AuditLogger.java             interface，实现类需 @Transactional(REQUIRES_NEW)
  AuditLogAspect.java          @Order(LOWEST_PRECEDENCE-1)，@ConditionalOnBean(AuditLogger)

autoconfigure/
  SecurityStarterAutoConfiguration.java  ← 注意：非 SecurityAutoConfiguration（会与 Boot 内置类名冲突）
    始终：JwtTokenParser + ServiceTokenGenerator + ServiceTokenClientInterceptor
    mode=service  → ServiceJwtValidator（验 jwt.service.secret）
    mode=gateway  → ServiceJwtValidator + UserJwtValidator（验 jwt.user.secret）
    mode=mock     → 不创建任何 Validator
    @ConditionalOnBean(AuditLogger) → AuditLogAspect
  ServiceJwtValidator.java     非 local 模式的 jwt.service.secret 存在性校验 Bean
  UserJwtValidator.java        gateway 模式的 jwt.user.secret 存在性校验 Bean
  SecurityResponseUtils.java   buildErrorJson(code, message, traceId) → 扁平 JSON
  servlet/
    ServletSecurityAutoConfiguration.java    @ConditionalOnProperty(mode=service) @Import(MethodSecurity)
      SecurityFilterChain 使用 AntPathRequestMatcher（非 MvcRequestMatcher，测试友好）
    MockServletSecurityAutoConfiguration.java @ConditionalOnProperty(mode=mock) @Import(MethodSecurity)
      全部 permitAll + MockUserFilter，@PreAuthorize 正常生效
    MethodSecurityConfiguration.java  @ConditionalOnMissingBean(MethodSecurityExpressionHandler)
    SecurityHandlers.java     SecurityAuthenticationEntryPoint + SecurityAccessDeniedHandler
  reactive/
    ReactiveSecurityAutoConfiguration.java  @ConditionalOnWebApplication(REACTIVE) + mode=gateway
```

**AutoConfiguration.imports：**

```
com.jugu.propertylease.security.autoconfigure.SecurityStarterAutoConfiguration
com.jugu.propertylease.security.autoconfigure.servlet.ServletSecurityAutoConfiguration
com.jugu.propertylease.security.autoconfigure.servlet.MockServletSecurityAutoConfiguration
com.jugu.propertylease.security.autoconfigure.reactive.ReactiveSecurityAutoConfiguration
```

**关键设计决策：**

- mode=mock：安全运行模式，与部署 Profile（local/dev/prod）完全独立
- mock 模式下 `@PreAuthorize` 正常生效（导入了 MethodSecurityConfiguration）
- mock 模式下 AuditLog 正常工作
- `AntPathRequestMatcher` 替代 `MvcRequestMatcher`，避免测试依赖完整 MVC 栈
- `TraceIdFilter` 在 SecurityFilterChain 内 `addFilterAfter(_, SecurityContextHolderFilter)`，早于
  `ServletServiceJwtFilter`
- `ServletServiceJwtFilter` 直接写401响应（不抛异常），避免 ExceptionTranslationFilter 截获导致错误码丢失
- `ReactiveUserJwtFilter.ATTR_TRACE_ID` 公开常量，供 Gateway 侧 ErrorHandler 读取

**测试：** 117 个测试全部通过

---

### 2.3 gateway 模块 ✅（代码完成，测试最后状态待确认）

**包根：** `com.jugu.propertylease.gateway`

**类清单：**

```
GatewayApplication.java
config/
  GatewayProperties.java       @ConfigurationProperties(prefix="gateway")
  RouteConfig.java             RouteLocatorBuilder，StripPrefix=2
  CorsConfig.java              CorsWebFilter Bean
filter/
  SecurityHeaderCleanFilter.java  GlobalFilter order=-200，配置驱动清洗 Header
  RateLimitFilter.java            GlobalFilter order=-150，令牌桶，429 响应携带 traceId
    从 exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID) 读 traceId
  RequestLoggingFilter.java       GlobalFilter order=-50，结构化日志
  ratelimit/RateLimitKeyResolver.java
  ratelimit/IpRateLimitKeyResolver.java
error/
  GatewayErrorWebExceptionHandler.java  @Order(-2)，处理 Gateway 自身异常
    从 exchange.getAttribute(ATTR_TRACE_ID) 读 traceId（attributes 共享原理）
    兜底从 request header 读
```

**过滤器执行顺序（关键）：**

```
① WebFilter 层（路由前）：
   ReactiveUserJwtFilter（WebFilter order=-100，security-starter 提供）
   ↓ ensureTraceId → 写 request header + exchange.attributes

② 路由匹配

③ GlobalFilter 层（路由后）：
   SecurityHeaderCleanFilter（order=-200）
   RateLimitFilter（order=-150）
   RequestLoggingFilter（order=-50）

WebFilter 总是先于 GlobalFilter 运行，两层 order 不可比较
```

**三个 TraceId Bug 已修复：**

- Bug1：`ServiceTokenClientInterceptor` 出站透传 X-Trace-Id（多服务链路一致）
- Bug2：`RateLimitFilter` 429 响应携带 traceId
- Bug3：`GatewayErrorWebExceptionHandler` 从 exchange.attributes 读 traceId（originalExchange 问题）

---

## 三、设计规范文档（已更新）

位于输出目录（或用户本地）：

```
system-architecture-spec-v10.md     系统架构总规范（v9 → v10）
common-module-spec-v1.md            Common 模块规范（全新）
security-starter-module-specs-v4.md Security-Starter 规范（v3 → v4）
gateway-module-spec-v2.md           Gateway 规范（v1 → v2）
```

---

## 四、尚未实现的模块

### 4.1 微服务（main-service / billing-service / device-service）

**当前状态：** 未开始

**每个微服务需要实现：**

1. **pom.xml**：parent = microservice-starter-parent
2. **application.yml 配置**：
   ```yaml
   security:
     mode: service   # 生产；本地开发改 mock
     service-name: billing-service
     jwt:
       service:
         secret: ${JWT_SERVICE_SECRET}
         expiration: 300
   ```
3. **OpenAPI yaml**：
    - `{service}-api.yaml`（/api/v1/**，含 x-required-permission）
    - `{service}-internal-api.yaml`（/internal/v1/**，无权限鉴权）
4. **代码生成**：openapi-generator-maven-plugin（mustache 模板）→ Delegate 接口
5. **业务分层**：DelegateImpl → Service → Repository（接口）→ RepositoryImpl（jOOQ）→ Model
6. **AuditLogger 实现**：各服务 audit 包，@Transactional(REQUIRES_NEW)
7. **DataPermissionVisitListener 实现**：各服务 datapermission 包
8. **JooqConfig**：注册 VisitListener（⚠️ C-43 待确认 DefaultConfigurationCustomizer API）
9. **Liquibase changelog**：建表脚本
10. **jOOQ 代码生成**：jooq-codegen-maven-plugin（基于 Liquibase changelog）

**main-service 额外需要（含 IAM 模块）：**

- 用户登录接口（permit-paths: /auth/login）
- User JWT 签发
- 用户-角色-权限 CRUD

### 4.2 clients 模块

**当前状态：** 未开始

```
clients/
  billing-service-client/    引用 billing-service-internal-api.yaml
  device-service-client/     引用 device-service-internal-api.yaml
```

每个 client 模块：

- openapi-generator → Feign 接口 + DTO
- AutoConfiguration：`@ConditionalOnBean(ServiceTokenClientInterceptor.class)` 自动注册

### 4.3 microservice-starter-parent

**当前状态：** 未开始（参考 system-architecture-spec-v10 3.3 节）

---

## 五、关键待确认事项（C-xx 编号）

| #      | 事项                                                                         | 优先级  | 影响范围                   |
|--------|----------------------------------------------------------------------------|------|------------------------|
| C-40   | mustache 模板 vendorExtensions key 格式（`x-required-permission` 在模板中的实际 key）   | 🔴 高 | 所有微服务 @PreAuthorize 生成 |
| C-43   | `DefaultConfigurationCustomizer` 在 Spring Boot 3.2 + jOOQ 3.19 中的准确包名和方法签名 | 🟡 中 | 各服务 JooqConfig         |
| C-31   | `@AuditLog` 的 `beforeJson` 获取方式（AOP 自动查 DB？手动传入？SpEL？）                     | 🟢 低 | AuditLogAspect         |
| C-32   | `DataPermissionVisitListener.queryInvolvesTable()` 辅助方法设计                  | 🟢 低 | 各服务 VisitListener 实现   |
| GW-Q-8 | main-service Controller 路径是否为 `/auth/login`（C-42 双端对齐验证）                   | 🟡 中 | Gateway permit-paths   |

---

## 六、约定与禁止事项（最重要的）

### 命名禁止

- ❌ 不能有类叫 `SecurityAutoConfiguration`（会与 Spring Boot 内置类名冲突，已改为
  `SecurityStarterAutoConfiguration`）
- ❌ 不能在 SecurityFilterChain 中用 `requestMatchers(String...)` 重载（会触发 MvcRequestMatcher，改用
  `AntPathRequestMatcher[]`）

### 错误响应格式

- ✅ 扁平：`{"code":"...","message":"...","traceId":"..."}`
- ❌ 禁止：`{"success":false,"error":{"code":"...","message":"..."}}`
- traceId 为 null 时不序列化（@JsonInclude(NON_NULL)）

### mode=mock 注意事项

- `security.mode=mock` 与 `spring.profiles.active=local` 是两个独立维度
- mock 模式下 `@PreAuthorize` 正常生效（可测试权限场景）
- mock 模式下 `jwt.*` 配置可完全省略
- mock 模式下 `ServiceTokenClientInterceptor` 跳过 Service JWT 但仍透传 X-Trace-Id

### TraceId 链路

- 微服务侧：`TraceIdFilter` 读/生成 → MDC，所有后续 Filter 和业务代码通过 `MDC.get("traceId")` 读取
- 出站调用：`ServiceTokenClientInterceptor` 从 MDC 读 traceId → 设置 X-Trace-Id Header
- Gateway 错误响应：从 `exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID)` 读，**不能**直接读
  originalExchange 的 request header（attributes 共享，header 不共享）

### 测试注意事项

- `GlobalExceptionHandlerTest`：不经过 Filter 链，需手动 `MDC.put("traceId", ...)` 在 `@BeforeEach`
- `MethodArgumentNotValidException` 测试：用 `MapBindingResult`（非 `BeanPropertyBindingResult`，后者需要真实
  Bean 属性）

---

## 七、下一步工作建议

### 立即可开始（无前置依赖）

1. **microservice-starter-parent**：定义公共依赖（security-starter、common、spring-boot-starter-web
   等）和公共插件（openapi-generator、liquibase、jooq-codegen）
2. **openapi-generator mustache 模板**：实现 `apiDelegate.mustache`，含 `@PreAuthorize` 生成逻辑，验证
   C-40

### 之后按序推进

3. **billing-service**（最简单，无 IAM 依赖，适合作为模板验证整体流程）：
    - Liquibase changelog → jOOQ 生成 → OpenAPI → DelegateImpl → Service → Repository
4. **main-service**（含 IAM，最复杂）
5. **device-service**
6. **clients 模块**（billing/device client）
7. **集成测试**（启动全链路验证 TraceId、JWT、权限）

---

## 八、代码位置索引

```
本地路径：D:\workdir\demo\property-lease-pro\backend\

已完成模块：
  common/              所有类 + 测试 ✅
  security-starter/    所有类 + 测试 ✅
  gateway/             所有类 + 测试（最终构建待确认）

规范文档（最新版）：
  system-architecture-spec-v10.md
  common-module-spec-v1.md
  security-starter-module-specs-v4.md
  gateway-module-spec-v2.md

重要配置：
  security-starter/src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
  （注册 SecurityStarterAutoConfiguration、ServletSecurityAutoConfiguration、
   MockServletSecurityAutoConfiguration、ReactiveSecurityAutoConfiguration）
```

---

## 九、新会话启动检查清单

1. 读取四份规范文档（specs-v10.zip）了解整体设计
2. 确认本地代码 common / security-starter / gateway 已更新至最新版本
3. 执行 `mvn clean install` 验证三个模块全部绿灯
4. 确认 gateway 模块最后一轮 traceId bug fix 已合入（traceid-bugfix.zip）
5. 开始实施第七节建议的下一步工作
