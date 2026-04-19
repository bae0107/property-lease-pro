# Common 模块实现 Spec

> 版本：v2.0 | 所属项目：com.jugu.propertylease / common
> Parent：starter-parent → spring-boot-starter-parent:3.2.0
> Java：17 | 包根：com.jugu.propertylease.common
>
> **定位：** 全系统共用的基础能力库。提供统一异常体系、错误响应格式、Feign 异常收敛、函数式结果包装、jOOQ
> 配置工具，以及全系统公共 OpenAPI Schema 定义。
> 被 security-starter 和所有微服务依赖，**不依赖任何内部模块**。

---

## 目录

1. [模块职责与依赖约束](#1-模块职责与依赖约束)
2. [统一错误响应格式（ErrorResponse）](#2-统一错误响应格式errorresponse)
3. [异常体系（BusinessException）](#3-异常体系businessexception)
4. [全局异常处理器（GlobalExceptionHandler）](#4-全局异常处理器globalexceptionhandler)
5. [函数式结果包装（Result）](#5-函数式结果包装result)
6. [Feign 异常收敛](#6-feign-异常收敛)
7. [jOOQ 配置工具（JooqConfigSupport）](#7-jooq-配置工具jooqconfigsupport)
8. [公共 OpenAPI Schema（common-components.yaml）](#8-公共-openapi-schemacommon-componentsyaml)
9. [自动装配（AutoConfiguration）](#9-自动装配autoconfiguration)
10. [包结构总览](#10-包结构总览)
11. [关键约束速查](#11-关键约束速查)

---

## 1. 模块职责与依赖约束

### 1.1 职责

| 能力                | 类 / 文件                                                        | 使用方                                         |
|-------------------|---------------------------------------------------------------|---------------------------------------------|
| 统一错误响应格式          | `ErrorResponse`                                               | 全系统所有错误响应                                   |
| 业务异常              | `BusinessException`                                           | Service 层抛出                                 |
| 全局异常处理            | `GlobalExceptionHandler`                                      | Servlet 微服务自动装配                             |
| 函数式结果包装           | `Result<T>`                                                   | 需要显式处理成功/失败分支的调用方                           |
| Feign 异常收敛        | `FeignBusinessExceptionErrorDecoder` + `FeignExceptionAspect` | 使用 Feign 的微服务                               |
| jOOQ 配置工具         | `JooqConfigSupport`                                           | 各微服务注册 DataPermissionVisitListener（解决 C-43） |
| 公共 OpenAPI Schema | `common-components.yaml`                                      | 全系统所有微服务的 yaml 定义                           |

### 1.2 依赖约束

```
common 的 Maven 依赖：
  spring-boot-starter-web（web 基础能力）
  spring-boot-starter-validation（Bean Validation）
  spring-boot-starter-aop（Feign AOP 收敛）
  jackson-databind（ErrorResponse 序列化）
  feign-core（可选，仅 Feign 相关类使用）
  jooq（optional=true，仅 JooqConfigSupport 使用，避免污染无 jOOQ 模块）
  spring-boot-autoconfigure（optional=true，仅 JooqConfigSupport 使用）

common 禁止依赖：
  任何内部业务模块（security-starter / 微服务 / clients）
  数据库 JDBC 驱动
  Liquibase
```

---

## 2. 统一错误响应格式（ErrorResponse）

### 2.1 格式定义

所有 4xx / 5xx 响应体统一使用扁平结构：

```json
{
  "code": "USER_NOT_FOUND",
  "message": "用户 ID 999 不存在",
  "traceId": "abc123def456"
}
```

**无 `success` 字段，无嵌套结构。** HTTP 状态码本身表达成功/失败语义，响应体不重复。

`traceId` 为 null 时不序列化（`@JsonInclude(NON_NULL)`），避免空值干扰客户端。

### 2.2 ErrorResponse 规范

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;      // 错误码，格式：{SERVICE}_{RESOURCE}_{REASON}
    private String message;   // 可读错误描述，直接面向调用方
    private String traceId;   // 全链路追踪 ID，null 时不序列化

    // 构造器：traceId 为 blank 时自动置 null
    public ErrorResponse(String code, String message, String traceId) { ... }
}
```

### 2.3 三分法 HTTP 状态码语义

| 状态码 | 含义               | 响应体             |
|-----|------------------|-----------------|
| 2xx | 请求成功执行           | 直接返回强类型业务数据（或空） |
| 4xx | 请求无法执行（重试无意义）    | `ErrorResponse` |
| 5xx | 服务端技术故障（重试可能有意义） | `ErrorResponse` |

### 2.4 错误码命名规范

```
格式：{SERVICE}_{RESOURCE}_{REASON}（全大写，下划线分隔）

示例：
  USER_NOT_FOUND              用户不存在
  INVENTORY_INSUFFICIENT      库存不足
  BILLING_ORDER_CANCELLED     账单服务订单取消
  INTERNAL_SERVER_ERROR       服务端兜底错误码
  COMMON_HTTP_404             框架级 HTTP 错误（见 GlobalExceptionHandler）
  COMMON_REQUEST_VALIDATION_FAILED  请求校验失败
```

---

## 3. 异常体系（BusinessException）

### 3.1 BusinessException 规范

```java
public class BusinessException extends RuntimeException {
    private final HttpStatus httpStatus;  // HTTP 状态码，GlobalExceptionHandler 直接读取
    private final String errorCode;       // 错误码，格式见 2.4

    // 主构造器
    public BusinessException(HttpStatus httpStatus, String errorCode, String message)

    // 带 cause 的构造器（Feign 等场景转换异常时保留原始 cause）
    public BusinessException(HttpStatus httpStatus, String errorCode,
                              String message, Throwable cause)

    // 转换为 ErrorResponse（traceId 由调用方从 MDC 注入）
    public ErrorResponse toErrorResponse(String traceId)
}
```

### 3.2 使用规范

```java
// Service 层直接抛出
throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户 ID 999 不存在");
throw new BusinessException(HttpStatus.BAD_REQUEST, "INVENTORY_INSUFFICIENT", "库存不足");

// GlobalExceptionHandler 自动捕获，读取 httpStatus 作为 HTTP 响应状态码
// errorCode 和 message 填入 ErrorResponse 响应体
```

---

## 4. 全局异常处理器（GlobalExceptionHandler）

### 4.1 设计原则

**继承 `ResponseEntityExceptionHandler`**（Spring 官方扩展点）：父类内置 final `@ExceptionHandler`
统一分发所有 Spring MVC HTTP 类异常，汇聚到 `handleExceptionInternal` 一个出口。覆写此方法即实现"
一处定制，全部覆盖"，Spring 版本升级新增的同类异常自动覆盖。

### 4.2 异常处理矩阵

| 异常类型                                                               | 来源                   | HTTP 状态 | errorCode                          | 处理方式                                            |
|--------------------------------------------------------------------|----------------------|---------|------------------------------------|-------------------------------------------------|
| `BusinessException`                                                | 业务逻辑                 | 异常自带    | 异常自带                               | `@ExceptionHandler`                             |
| Spring MVC HTTP 类异常（`org.springframework.web.ErrorResponse` 接口实现类） | Spring Framework     | 异常自带    | `COMMON_HTTP_{statusCode}`         | 覆写 `handleExceptionInternal`                    |
| `NoResourceFoundException`                                         | Spring Boot 3.2+ 404 | 404     | `COMMON_HTTP_404`                  | 同上（`ErrorResponse` 接口子类）                        |
| `HttpRequestMethodNotSupportedException`                           | 405                  | 405     | `COMMON_HTTP_405`                  | 同上                                              |
| `HttpMediaTypeNotSupportedException`                               | 415                  | 415     | `COMMON_HTTP_415`                  | 同上                                              |
| `MethodArgumentNotValidException`                                  | `@Valid` 失败          | 400     | `COMMON_REQUEST_VALIDATION_FAILED` | 覆写 `handleMethodArgumentNotValid`，聚合字段错误        |
| `HttpMessageNotReadableException`                                  | 请求体不可读               | 400     | `COMMON_REQUEST_BODY_UNREADABLE`   | 覆写 `handleHttpMessageNotReadable`，隐藏 Jackson 细节 |
| `ConstraintViolationException`                                     | `@Validated` 参数校验    | 400     | `COMMON_REQUEST_VALIDATION_FAILED` | `@ExceptionHandler`（不继承 Spring 体系）              |
| `Exception`（兜底）                                                    | 未预期                  | 500     | `INTERNAL_SERVER_ERROR`            | `@ExceptionHandler`，完整堆栈写日志，隐藏细节                |

### 4.3 关于 NoResourceFoundException 的特别说明

Spring Boot 3.2（Spring Framework 6.1）起，路由不存在时抛出
`org.springframework.web.servlet.resource.NoResourceFoundException`（继承 `ServletException` + 实现
`org.springframework.web.ErrorResponse` 接口），**不再是** `NoHandlerFoundException`。

旧的 `NoHandlerFoundException` handler 在 Boot 3.2 环境下几乎不会被触发，已移除。
`NoResourceFoundException` 通过 `handleExceptionInternal` 自动覆盖，无需单独处理。

**注意两个同名类的区别：**

- `org.springframework.web.servlet.resource.NoResourceFoundException`：Servlet MVC，继承
  `ServletException`，Boot 3.2+ 实际 404
- `org.springframework.web.reactive.resource.NoResourceFoundException`：Reactive WebFlux，继承
  `ResponseStatusException`

### 4.4 traceId 读取

```java
private String traceId() {
    return MDC.get("traceId");  // 由 TraceIdFilter 在 SecurityFilterChain 中注入
}
```

**生产环境**：所有请求经过 `TraceIdFilter`，MDC 始终有值，traceId 始终携带。
**单元测试**：不经过完整 Filter 链时，需在 `@BeforeEach` 中手动 `MDC.put("traceId", ...)`，或通过
MockMvc 注册 `TraceIdFilter`。

### 4.5 GlobalExceptionHandler 自动装配

仅在 Servlet Web 应用中注册（`@ConditionalOnWebApplication(type = SERVLET)`），Gateway（WebFlux）不加载。详见第
7 节。

---

## 5. 函数式结果包装（Result）

### 5.1 使用场景

`Result<T>` 供需要**显式处理成功/失败两个分支**的调用方使用。大多数情况下，Feign 调用失败会抛
`BusinessException`（由框架兜底），无需 `Result`。以下场景使用 `Result`：

- 失败时需要降级为默认值（如查用户失败返回游客）
- 需要先处理错误副作用再决定返回值
- 业务逻辑不允许因依赖失败而整体失败

### 5.2 Result API 规范

```java
// 工厂方法
Result.of(Supplier<T>)       // 执行 supplier，捕获 BusinessException 包装为失败 Result
                              // 只捕获 BusinessException，其他异常向上传播
Result.ok(T data)             // 构建成功 Result
Result.fail(ErrorResponse, HttpStatus)  // 构建失败 Result

// 查询方法
boolean isSuccess()
HttpStatus getStatus()        // 成功 = 200；失败 = 原始状态码
T getData()                   // 成功时有值，失败时 null
ErrorResponse getError()      // 失败时有值，成功时 null

// 链式 API
Result<T> onFailure(Consumer<ErrorResponse>)   // 失败时处理副作用，返回 this
T getOrElseGet(Function<ErrorResponse, T>)     // 成功返回数据，失败执行 fallback
```

### 5.3 典型用法

```java
// 失败给默认值
User user = Result.of(() -> userClient.getUserById(id))
    .getOrElseGet(err -> User.guest());

// 先记日志，再降级
User user = Result.of(() -> userClient.getUserById(id))
    .onFailure(err -> log.warn("查询用户失败: code={}", err.getCode()))
    .getOrElseGet(err -> User.guest());
```

---

## 6. Feign 异常收敛

### 6.1 设计背景

Feign 调用失败时有两类根本原因，需要不同的处理方式：

| 类型           | 触发场景         | Feign 机制               | 处理类                                  |
|--------------|--------------|------------------------|--------------------------------------|
| 有 HTTP 响应的失败 | 下游返回 4xx/5xx | `ErrorDecoder`         | `FeignBusinessExceptionErrorDecoder` |
| 无 HTTP 响应的失败 | 连接失败/超时      | 抛 `RetryableException` | `FeignExceptionAspect`               |

两层组合保证：无论 Feign 调用以何种方式失败，调用方收到的都是 `BusinessException`，通过 `Result.of` 或
try-catch 统一处理。

### 6.2 FeignBusinessExceptionErrorDecoder 规范

```
实现 ErrorDecoder（feign.codec.ErrorDecoder）

覆盖范围：所有收到 HTTP 响应的失败（有 status code）

处理逻辑：
  1. 解析 HttpStatus
  2. 尝试反序列化响应体为 ErrorResponse（flat 格式）
     成功 → 用原始 code/message 构建 BusinessException（保留下游错误语义）
     失败（如 502 返回 HTML）→ DOWNSTREAM_ERROR + 友好 message
  3. 返回 BusinessException(status, code, message)

设计要点：
  - 依赖响应体为 ErrorResponse 扁平格式（{code, message, traceId}）
  - 保留下游 HttpStatus，调用方可判断 4xx vs 5xx 做不同处理
```

### 6.3 FeignExceptionAspect 规范

```
@Aspect @Component
切点：@Around("@within(FeignClient)")

覆盖范围：FeignClient 接口上所有方法，捕获 RetryableException

处理逻辑：
  try { return pjp.proceed() }
  catch (RetryableException e) {
      throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
          "SERVICE_UNAVAILABLE", "下游服务暂时不可用，请稍后重试", e)
  }
  // 其他异常（BusinessException 等）直接向上传播，不重复包装

设计要点：
  - 只处理 RetryableException（连接失败/超时）
  - BusinessException 不处理（已由 FeignBusinessExceptionErrorDecoder 转换）
  - @Around + @within(FeignClient) 确保切点精准，不误伤非 Feign Bean
```

### 6.4 自动装配条件

```
FeignBusinessExceptionErrorDecoder + FeignExceptionAspect：
  @ConditionalOnClass(FeignClient.class)   → classpath 有 Feign 时才注册
  通过 CommonFeignAutoConfiguration 装配
```

---

## 7. jOOQ 配置工具（JooqConfigSupport）

**解决 C-43**：`DefaultConfigurationCustomizer` 在 Spring Boot 3.2 + jOOQ 3.19 中的准确 API。

```java
// com.jugu.propertylease.common.jooq.JooqConfigSupport
// 依赖：org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer
// Spring Boot jOOQ AutoConfig 自动收集所有此类型 Bean 并应用到 DefaultConfiguration

public final class JooqConfigSupport {

    private JooqConfigSupport() {}

    /** 注册单个 VisitListener（JDK 8+ lambda）*/
    public static DefaultConfigurationCustomizer withVisitListener(VisitListener listener) {
        return config -> config.set(new DefaultVisitListenerProvider(listener));
    }

    /** 注册多个 VisitListener（JDK 8+ Stream + 方法引用）*/
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

common pom.xml 中设为 optional（避免污染无 jOOQ 的模块）：

```xml
<dependency>
    <groupId>org.jooq</groupId>
    <artifactId>jooq</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <optional>true</optional>
</dependency>
```

---

## 8. 公共 OpenAPI Schema（common-components.yaml）

### 8.1 文件位置与引用

```
common/src/main/resources/openapi/common-components.yaml
```

各微服务通过相对路径引用（以 main-service 视角）：

```yaml
$ref: '../../../../../common/src/main/resources/openapi/common-components.yaml#/components/schemas/ErrorResponse'
```

**禁止各服务重复定义公共 Schema（C-10）。特有 Schema 放各服务自己的
yaml（如 `WechatBindRequiredResponse` 在 main-service-api.yaml）。**

### 8.2 包含的公共 Schema

| Schema            | 说明                                                      |
|-------------------|---------------------------------------------------------|
| `ErrorResponse`   | 全系统统一错误响应（扁平格式）                                         |
| `PageRequest`     | 通用分页请求（pageNo 从 1 开始，含 filters）                         |
| `QueryFilter`     | 过滤容器（stringFilters / idsFilters / enumFilters，**均为数组**） |
| `StringFilter`    | 字符串模糊过滤（LIKE %value%）                                   |
| `IdsFilter`       | 多值 IN 过滤（IN(v1,v2,...)）—— 注意 idsFilters 是数组，支持多字段同时过滤   |
| `EnumFilter`      | 枚举精确过滤（= value，可选值来自 tableMeta options）                 |
| `PageResponse`    | 分页响应基础（含 tableMeta，各资源通过 allOf 追加 items）                |
| `TableMeta`       | 表格字段元数据（随每次分页响应返回，后端内部缓存）                               |
| `FilterFieldMeta` | 单字段元数据（key / label / filterType / options）              |
| `FilterOption`    | 枚举下拉选项（value / label）                                   |
| `BatchRequest`    | 通用批量操作（ids 数组，单个传 1 个）                                  |

### 8.3 tableMeta 机制

```
- 随每次分页响应一起返回，后端内部缓存，前端透明
- 前端根据 filterType 动态渲染控件：
    STRING → 文本输入框（模糊搜索）
    IDS    → 通常由勾选逻辑自动填充（隐藏控件）
    ENUM   → 下拉选择框（选项来自 options）
- 后端根据 tableMeta 定义做 key 白名单校验（非法 key → 400 COMMON_INVALID_FILTER_KEY）
- 前后端使用同一份定义，保证过滤字段名称一致性
```

### 8.4 allOf 生成行为（openapi-generator 7.x）

Spring generator 7.x 对 allOf 生成**扁平类**（所有字段内联，不生成 Java 继承），这是正确行为。需配合
`openApiNullable=false`（在 microservice-starter-parent 统一配置）：

```xml
<configOptions>
    <openApiNullable>false</openApiNullable>  <!-- 禁止 JsonNullable 包装，统一在 starter-parent 设置 -->
    <useSpringBoot3>true</useSpringBoot3>
    <delegatePattern>true</delegatePattern>
</configOptions>
```

分页响应使用方式（各资源）：

```yaml
UserPageResult:
  allOf:
    - $ref: 'common-components.yaml#/components/schemas/PageResponse'
    - type: object
      required: [items]
      properties:
        items:
          type: array
          items: { $ref: '#/components/schemas/User' }
```

---

## 9. 自动装配（AutoConfiguration）

```
CommonAutoConfiguration：
  @ConditionalOnWebApplication(type = SERVLET)
  → 注册 GlobalExceptionHandler Bean

CommonFeignAutoConfiguration：
  @ConditionalOnClass(FeignClient.class)
  → 注册 FeignBusinessExceptionErrorDecoder Bean
  → 注册 FeignExceptionAspect Bean
```

注册文件：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

---

## 10. 包结构总览

```
common/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/jugu/propertylease/common/
    │   │   ├── autoconfigure/
    │   │   │   ├── CommonAutoConfiguration.java
    │   │   │   └── CommonFeignAutoConfiguration.java
    │   │   ├── exception/
    │   │   │   ├── ErrorResponse.java
    │   │   │   └── BusinessException.java
    │   │   ├── feign/
    │   │   │   ├── FeignBusinessExceptionErrorDecoder.java
    │   │   │   └── FeignExceptionAspect.java
    │   │   ├── jooq/
    │   │   │   └── JooqConfigSupport.java       ← 新增，解决 C-43
    │   │   ├── result/
    │   │   │   └── Result.java
    │   │   └── web/
    │   │       └── GlobalExceptionHandler.java
    │   └── resources/
    │       ├── META-INF/spring/
    │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │       └── openapi/
    │           └── common-components.yaml       ← 新增，公共 Schema 唯一来源
    └── test/
        └── java/com/jugu/propertylease/common/
            ├── exception/
            ├── feign/
            ├── jooq/
            │   └── JooqConfigSupportTest.java
            ├── result/
            └── web/
                └── GlobalExceptionHandlerTest.java
```

---

## 11. 关键约束速查

| #     | 约束                                | 说明                                                                                      |
|-------|-----------------------------------|-----------------------------------------------------------------------------------------|
| CM-01 | 扁平错误格式                            | `{code, message, traceId}`，禁止嵌套结构                                                       |
| CM-02 | traceId 按需包含                      | null 时不序列化（`@JsonInclude(NON_NULL)`）                                                    |
| CM-03 | 无内部依赖                             | jOOQ/autoconfigure 设为 optional，避免污染无 jOOQ 模块                                            |
| CM-04 | GlobalExceptionHandler 仅 Servlet  | `@ConditionalOnWebApplication(SERVLET)`，Gateway 不加载                                     |
| CM-05 | 继承 ResponseEntityExceptionHandler | 覆写 `handleExceptionInternal`，Spring 新增异常自动覆盖                                            |
| CM-06 | Boot 3.2 的 404                    | `NoResourceFoundException` 实现 Spring `ErrorResponse` 接口，通过 `handleExceptionInternal` 处理 |
| CM-07 | 两个 ErrorResponse 区分               | Spring 接口 `org.springframework.web.ErrorResponse` vs 本项目类，注意 import                     |
| CM-08 | Feign 两层收敛                        | ErrorDecoder（有响应）+ Aspect（无响应/超时）                                                       |
| CM-09 | Result.of 只捕 BusinessException    | 其他异常向上传播                                                                                |
| CM-10 | traceId 来源                        | MDC，由 TraceIdFilter 注入；单测需手动 `MDC.put`                                                  |
| CM-11 | JooqConfigSupport 解决 C-43         | 封装 `DefaultConfigurationCustomizer` 准确 API，各服务 JooqConfig 使用                            |
| CM-12 | common-components.yaml 唯一来源       | 公共 Schema 只此处定义（C-10），特有 Schema 放各服务 yaml                                               |
| CM-13 | idsFilters 是数组                    | 三种过滤类型均为数组，idsFilters 支持多字段同时 IN 过滤                                                     |
| CM-14 | tableMeta 随分页返回                   | 后端内部缓存，前端透明，用于 UI 渲染和后端白名单校验                                                            |
| CM-15 | JDK 8+ 代码风格                       | Stream/Optional/lambda；优先 record > Lombok > 手写；低于 8+ 须注释原因                              |
