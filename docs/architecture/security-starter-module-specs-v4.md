# Security-Starter 子模块实现 Spec

> 版本：v4.0 | 所属项目：com.jugu.propertylease / security-starter
> Parent：starter-parent → spring-boot-starter-parent:3.2.0
> Java：17 | 包根：com.jugu.propertylease.security
>
> **核心设计原则：**
> - Zero-Trust 统一 Service JWT 机制
> - 最大化复用 Spring Security 框架能力
> - Authentication 载体：`ServiceJwtAuthenticationToken extends AbstractAuthenticationToken`
> - 不实现 `UserDetails`，不引入 OAuth2 Resource Server

---

## 目录

- [模块 1：constants + properties](#模块-1constants--properties)
- [模块 2：token（JWT 解析与生成）](#模块-2tokenjwt-解析与生成)
- [模块 3：exception + authentication + context](#模块-3exception--authentication--context)
- [模块 4：filter（Servlet + Reactive 过滤器）](#模块-4filterservlet--reactive-过滤器)
- [模块 5：authorization + autoconfigure](#模块-5authorization--autoconfigure)
- [模块 6：interceptor + datapermission](#模块-6interceptor--datapermission)
- [模块 7：audit（审计日志）](#模块-7audit审计日志)
- [模块间依赖关系与实现顺序](#模块间依赖关系与实现顺序)
- [待确认事项](#待确认事项)

---

## 模块 1：constants + properties

### 1.1 SecurityConstants 规范

```java
public static final List<String> DEFAULT_PERMIT_PATHS = List.of(
    "/actuator/**",   // 运维端点，内网访问
    "/error"          // Spring Boot error dispatch 路径
                      // 业务异常（如 405）触发 error dispatch 时，若不放行
                      // Security 会拦截返回 UNAUTHORIZED，掩盖真实错误码
);

public static final String HEADER_SERVICE_TOKEN = "X-Service-Token";
public static final String HEADER_TRACE_ID      = "X-Trace-Id";
public static final String CLAIM_USER_ID        = "userId";
public static final String CLAIM_PERMISSIONS    = "permissions";
```

### 1.2 SecurityProperties 规范

```
@ConfigurationProperties(prefix = "security")
@Validated

字段：
  @NotBlank String mode
    gateway → WebFlux，验证 User JWT，签发 Service JWT
    service → Servlet，验证 Service JWT（生产环境）
    mock    → Servlet，跳过 Token 验证，注入固定 mock 用户（本地开发）

  @NotBlank String serviceName
    当前服务名，写入 Service JWT 的 sub 字段

  List<String> permitPaths    可选，默认空列表
    服务特有放行路径，与 DEFAULT_PERMIT_PATHS 合并后使用

  JwtConfig jwt               可省略（mode=mock 时）
    JwtConfig.user            mode=gateway 时必须配置，由 UserJwtValidator Bean 校验
    JwtConfig.service         mode=service/gateway 时必须配置，由 ServiceJwtValidator Bean 校验
    （注意：JwtConfig 字段无 JSR-303 级联校验注解，存在性由条件 Bean 负责）

  MockUser mockUser           可选，默认 userId=1, permissions=[]
    仅 mode=mock 时生效；其他模式忽略
    MockUser.userId     Long（默认 1L）
    MockUser.permissions List<String>（默认空列表）

辅助方法：
  List<String> getEffectivePermitPaths()
    合并 DEFAULT_PERMIT_PATHS + permitPaths，返回不可变列表
    所有 Filter 和 FilterChain 只调用此方法，禁止直接读取 permitPaths 字段
```

### 1.3 mode=mock 配置说明

`security.mode=mock` 是**安全运行模式**，与部署环境 Profile（local/dev/prod）是两个独立维度：

```yaml
# 典型本地开发配置
spring:
  profiles:
    active: local          # 部署环境 Profile

security:
  mode: mock               # 安全运行模式（独立于 Profile）
  service-name: billing-service
  mock-user:
    user-id: 1
    permissions:
      - "lease:read"
      - "lease:write"      # 配置权限可测试 @PreAuthorize 的 403 场景
```

mock 模式特性：

- 不需要 jwt 配置
- `@PreAuthorize` 方法安全正常生效（`MethodSecurityConfiguration` 导入）
- `CurrentUser.getCurrentUserId()` 返回配置的 userId
- `AuditLogAspect` 正常工作（能记录 userId）
- 出站调用 `ServiceTokenClientInterceptor` 跳过 Service JWT 生成，但仍透传 X-Trace-Id

---

## 模块 2：token（JWT 解析与生成）

### 2.1 设计要点

- 纯 Java，无 Spring 注解，可直接 new 实例单测
- claims 常量统一使用 `SecurityConstants` 中定义的常量，禁止硬编码

### 2.2 JwtTokenParser 规范

```java
// 两个公开方法，secret 每次作为参数传入
UserTokenPayload parseUserToken(String token, String secret)
ServiceTokenPayload parseServiceToken(String token, String secret)

// 异常映射（两个方法相同）
ExpiredJwtException   → InvalidTokenException(TOKEN_EXPIRED)
SignatureException    → InvalidTokenException(TOKEN_INVALID)
MalformedJwtException → InvalidTokenException(TOKEN_MALFORMED)
其他 JwtException     → InvalidTokenException(TOKEN_MALFORMED)
```

### 2.3 ServiceTokenGenerator 规范

```java
String generate(String serviceName, Long userId, List<String> permissions,
                String secret, int expSeconds)
```

- `userId=null` → 不写入 Claims（对应系统调用）
- `permissions` 空/null → 不写入 Claims
- 每次重新生成，不缓存

---

## 模块 3：exception + authentication + context

### 3.1 InvalidTokenException 规范

```java
public class InvalidTokenException extends AuthenticationException

// 内置常量
TOKEN_EXPIRED   = "IAM_TOKEN_EXPIRED"
TOKEN_INVALID   = "IAM_TOKEN_INVALID"
TOKEN_MALFORMED = "IAM_TOKEN_MALFORMED"
TOKEN_MISSING   = "IAM_TOKEN_MISSING"
```

### 3.2 ServiceJwtAuthenticationToken 规范

```java
public class ServiceJwtAuthenticationToken extends AbstractAuthenticationToken

// 工厂方法
static ofUser(Long userId, String callerName, List<String> permissions)
static ofSystem(String serviceName)   // userId=null，空权限

// 业务方法
Long getUserId()       // nullable，null = 系统调用
String getCallerName() // non-null，来自 JWT sub
```

### 3.3 CurrentUser 规范

```java
static Long getCurrentUserId()          // null = 系统调用或未认证
static String getCallerName()           // null = 未认证
static Set<String> getPermissions()     // 未认证时空集合
static boolean isAuthenticated()
static boolean isSystemCall()           // 已认证 + userId==null
static Optional<ServiceJwtAuthenticationToken> getAuthentication()
```

所有方法对"无认证"状态安全返回，不抛 NPE。禁止修改 SecurityContext。

---

## 模块 4：filter（Servlet + Reactive 过滤器）

### 4.1 TraceIdFilter 规范

```java
extends OncePerRequestFilter

doFilterInternal：
  1. 读取 request.getHeader(HEADER_TRACE_ID)
  2. 不存在或为空 → UUID.randomUUID().toString().replace("-", "")
  3. MDC.put("traceId", traceId)
  4. try { chain.doFilter } finally { MDC.remove("traceId") }  // 必须 remove，非 clear

注册位置：SecurityFilterChain 内，addFilterAfter(traceIdFilter, SecurityContextHolderFilter.class)
执行顺序：SecurityContextHolderFilter → TraceIdFilter → ... → ServletServiceJwtFilter
保证：ServletServiceJwtFilter 写 401 时，MDC 中已有 traceId
```

### 4.2 ServletServiceJwtFilter 规范

```java
extends OncePerRequestFilter

shouldNotFilter：
  securityProperties.getEffectivePermitPaths() 中任意 AntPath 匹配 → 返回 true（跳过）

doFilterInternal：
  1. 读取 X-Service-Token Header
     缺失 → write401(response, TOKEN_MISSING, "Service token is required")，return
  2. jwtTokenParser.parseServiceToken(token, secret)
     抛 InvalidTokenException → write401(response, e.getErrorCode(), e.getMessage())，return
  3. 构建 ServiceJwtAuthenticationToken，写入 SecurityContextHolder
  4. chain.doFilter

write401：
  String traceId = MDC.get("traceId")   // 来自 TraceIdFilter
  String body = SecurityResponseUtils.buildErrorJson(code, message, traceId)
  response.setStatus(401)
  response.setContentType("application/json;charset=UTF-8")
  response.getWriter().write(body)

注意：直接写响应，不抛异常（Filter 在 ExceptionTranslationFilter 之前，抛异常会绕过 EntryPoint）
```

### 4.3 MockUserFilter 规范

```java
extends OncePerRequestFilter
// 仅在 mode=mock 时由 MockServletSecurityAutoConfiguration 注册

doFilterInternal：
  1. 构建 ServiceJwtAuthenticationToken.ofUser(
         mockUser.getUserId(), "mock", mockUser.getPermissions())
  2. SecurityContextHolder.getContext().setAuthentication(token)
  3. try { chain.doFilter } finally { SecurityContextHolder.clearContext() }

注册位置：addFilterBefore(mockUserFilter, UsernamePasswordAuthenticationFilter.class)
保证：业务代码执行时 CurrentUser、@PreAuthorize、AuditLog 均正常工作
```

### 4.4 ReactiveUserJwtFilter 规范

```java
implements WebFilter, Ordered
order = -100   // WebFilter，在路由前执行（早于所有 GlobalFilter）

公共常量：
  public static final String ATTR_TRACE_ID = "traceId"
  // 用于 GatewayErrorWebExceptionHandler 从 exchange.attributes 读取 traceId

filter(ServerWebExchange exchange, WebFilterChain chain)：
  1. ensureTraceId(exchange)
     - 读取 X-Trace-Id Header
     - 不存在 → 生成 UUID
     - 写入 request header（透传给下游）
     - 同时写入 exchange.getAttributes().put(ATTR_TRACE_ID, traceId)
       目的：ErrorWebExceptionHandler 收到 originalExchange，无法访问 mutated header，
             但 attributes 在 mutate() 时共享同一实例，可以读到
     - 返回 mutatedExchange（exchange 局部变量重新赋值）

  2. 放行路径 → chain.filter(exchange)

  3. 认证路径：
     - 读取 Authorization: Bearer <token>，失败 → write401
     - Mono.fromCallable(() -> parseUserToken)
     - 成功 → buildForwardExchange（生成 Service JWT，移除 Authorization，添加 X-Service-Token）
     - write401 中 traceId 优先从 exchange.getAttribute(ATTR_TRACE_ID) 读

错误响应格式（扁平）：
  {"code":"IAM_TOKEN_EXPIRED","message":"...","traceId":"abc123"}
```

---

## 模块 5：authorization + autoconfigure

### 5.1 SecurityStarterAutoConfiguration 规范

> 注意：类名为 `SecurityStarterAutoConfiguration`（非 `SecurityAutoConfiguration`），
> 避免与 Spring Boot 内置的
`org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration`
> 产生 Bean 名称冲突。

```
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)

始终装配：
  JwtTokenParser、ServiceTokenGenerator、ServiceTokenClientInterceptor

条件装配：
  mode=service  → ServiceJwtValidator（校验 jwt.service.secret）
  mode=gateway  → ServiceJwtValidator（校验 jwt.service.secret）
               + UserJwtValidator（校验 jwt.user.secret）
  mode=mock     → 不创建任何 Validator（jwt 配置可完全省略）
  @ConditionalOnBean(AuditLogger) → AuditLogAspect（三种 mode 均支持）
```

### 5.2 ServletSecurityAutoConfiguration 规范

```
@AutoConfiguration(after = SecurityStarterAutoConfiguration.class)
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnProperty(name = "security.mode", havingValue = "service")
@Import(MethodSecurityConfiguration.class)

装配：
  TraceIdFilter
  ServletServiceJwtFilter
  SecurityPermissionEvaluator
  DataPermissionInterceptor + WebMvcConfigurer（注册拦截器）
  SecurityFilterChain（使用 AntPathRequestMatcher，非 MvcRequestMatcher）：
    permitMatchers(AntPathRequestMatcher[]) → permitAll
    anyRequest() → authenticated
    addFilterAfter(traceIdFilter, SecurityContextHolderFilter)
    addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter)
    exceptionHandling: EntryPoint + AccessDeniedHandler
```

### 5.3 MockServletSecurityAutoConfiguration 规范

```
@AutoConfiguration(after = SecurityStarterAutoConfiguration.class)
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnProperty(name = "security.mode", havingValue = "mock")
@Import(MethodSecurityConfiguration.class)

与 ServletSecurityAutoConfiguration 的区别：
  - 不注册 ServletServiceJwtFilter（无入站 Token 验证）
  - 注册 MockUserFilter（注入配置的固定 mock 用户）
  - SecurityFilterChain 全部 permitAll（认证由 MockUserFilter 通过 SecurityContext 提供）
  - MethodSecurityConfiguration 同样导入（@PreAuthorize 正常生效，支持权限场景测试）
```

### 5.4 SecurityFilterChain 的 AntPathRequestMatcher 说明

```java
// 正确：AntPathRequestMatcher，不依赖 Spring MVC HandlerMappingIntrospector
AntPathRequestMatcher[] permitMatchers = props.getEffectivePermitPaths().stream()
        .map(AntPathRequestMatcher::new)
        .toArray(AntPathRequestMatcher[]::new);
http.authorizeHttpRequests(auth -> auth
        .requestMatchers(permitMatchers).permitAll()
        .anyRequest().authenticated());

// 错误：不使用 String[] 重载（会触发 MvcRequestMatcher）
// http.authorizeHttpRequests(auth -> auth.requestMatchers(String[]).permitAll())
```

原因：Spring Security 6 在 classpath 有 Spring MVC 时，`requestMatchers(String...)` 默认使用
`MvcRequestMatcher`，需要 `HandlerMappingIntrospector` Bean。`WebApplicationContextRunner` 单元测试不加载完整
MVC 栈，会导致测试失败。

### 5.5 SecurityHandlers 规范

```java
// SecurityAuthenticationEntryPoint（401）
// 注意：ServletServiceJwtFilter 直接写响应，不抛异常，此 EntryPoint 处理 Spring Security 框架级 401
String traceId = MDC.get("traceId");
String code = authException instanceof InvalidTokenException ite
              ? ite.getErrorCode() : "UNAUTHORIZED";
// 扁平格式响应
buildErrorJson(code, message, traceId)

// SecurityAccessDeniedHandler（403）
String traceId = MDC.get("traceId");
buildErrorJson("FORBIDDEN", "Access denied", traceId)
```

### 5.6 SecurityResponseUtils 规范

```java
// 构建统一扁平错误 JSON，供 Servlet 侧和 Reactive 侧共用
public static String buildErrorJson(String code, String message, String traceId)
// 使用 common 模块的 ErrorResponse 序列化，保证格式一致
// 输出格式：{"code":"...","message":"...","traceId":"..."}（traceId 为 null 时不输出）
```

### 5.7 AutoConfiguration.imports 注册

```
com.jugu.propertylease.security.autoconfigure.SecurityStarterAutoConfiguration
com.jugu.propertylease.security.autoconfigure.servlet.ServletSecurityAutoConfiguration
com.jugu.propertylease.security.autoconfigure.servlet.MockServletSecurityAutoConfiguration
com.jugu.propertylease.security.autoconfigure.reactive.ReactiveSecurityAutoConfiguration
```

---

## 模块 6：interceptor + datapermission

### 6.1 ServiceTokenClientInterceptor 规范

```java
implements ClientHttpRequestInterceptor

intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)：

  // Step 1：透传 X-Trace-Id（跨服务调用链 traceId 一致）
  String traceId = MDC.get("traceId");
  if (traceId != null && !traceId.isBlank()) {
      request.getHeaders().set(HEADER_TRACE_ID, traceId);
  }

  // Step 2：mock 模式下 jwt.service 未配置，跳过 Token 附加
  if (properties.getJwt() == null || properties.getJwt().getService() == null) {
      return execution.execute(request, body);
  }

  // Step 3：生成 Service JWT 并附加
  Long userId = CurrentUser.getCurrentUserId();
  List<String> perms = new ArrayList<>(CurrentUser.getPermissions());
  String token = generator.generate(serviceName, userId, perms, secret, expiration);
  request.getHeaders().set(HEADER_SERVICE_TOKEN, token);

  return execution.execute(request, body);
```

**TraceId 透传是 multi-hop 调用链一致性的关键**：A→B→C 调用中，B 处理时 MDC 有 A 传来的 traceId，出站调用
C 时透传同一个 traceId，C 的 TraceIdFilter 读到后继续写 MDC，全链路 traceId 保持一致。

### 6.2 DataPermissionContext / DataPermissionInterceptor / DataPermissionVisitListener

规范与 v3.0 一致，无变更。

---

## 模块 7：audit（审计日志）

规范与 v3.0 一致，补充说明：

**mock 模式下的审计**：`AuditLogAspect` 由 `@ConditionalOnBean(AuditLogger.class)` 控制，与
security.mode 无关。只要微服务提供了 `AuditLogger` 实现 Bean，mock 模式下审计同样正常工作，
`CurrentUser.getCurrentUserId()` 返回 MockUser 配置的 userId，可用于本地调试审计能力。

---

## 模块间依赖关系与实现顺序

```
实现顺序（严格遵守，后者依赖前者）：
  ① 模块 1（constants + properties）← 无依赖，最先
  ② 模块 2（token）← 无 Spring 依赖，纯 Java
  ③ 模块 3（exception + authentication + context）← Spring Security Core
  ④ 模块 6（interceptor + datapermission）← 模块 1、2、3
  ⑤ 模块 7（audit）← 模块 3
  ⑥ 模块 4（filter）← 模块 1、2、3
  ⑦ 模块 5（authorization + autoconfigure）← 模块 1-4，6，7（最后，装配层）
```

---

## 待确认事项

| #    | 事项                                                                      | 影响模块       | 优先级 |
|------|-------------------------------------------------------------------------|------------|-----|
| C-31 | @AuditLog 的 beforeJson 获取方式                                             | 模块 7       | 低   |
| C-32 | DataPermissionVisitListener.queryInvolvesTable() 辅助方法                   | 模块 6       | 低   |
| C-40 | mustache 模板 vendorExtensions key 格式                                     | 外部模板       | 高   |
| C-43 | DefaultConfigurationCustomizer API 在 Spring Boot 3.2 + jOOQ 3.19 中的准确包名 | JooqConfig | 中   |
