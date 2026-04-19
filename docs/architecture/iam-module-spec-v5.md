# IAM 模块编码规格文档 v5.0

> 模块：`main-service` / `iam` 子包
> 技术栈：Spring Boot 3.2 / Java 17 / MySQL + H2（本地）/ jOOQ 3.19 / Liquibase 4.x
> 基准规范：`system-architecture-spec-v10` + `security-starter-module-specs-v4` +
`gateway-module-spec-v2`
> OpenAPI generator：7.17.0，配置要求 `openApiNullable=false`
> 更新：2026-03-24

---

## 目录

1. [模块概览与基本面](#1-模块概览与基本面)
2. [核心设计原则](#2-核心设计原则)
3. [用户与角色能力矩阵](#3-用户与角色能力矩阵)
4. [数据模型（完整字段）](#4-数据模型完整字段)
5. [OpenAPI 契约规范](#5-openapi-契约规范)
6. [权限同步机制（PermissionSyncRunner）](#6-权限同步机制permissionsyncrunner)
7. [Redis 数据结构](#7-redis-数据结构)
8. [对象映射规范](#8-对象映射规范)
9. [核心业务流程](#9-核心业务流程)
10. [分层架构与类设计](#10-分层架构与类设计)
11. [错误码规范](#11-错误码规范)
12. [User JWT 规范](#12-user-jwt-规范)
13. [配置规范](#13-配置规范)
14. [C-40 / C-43 解决方案](#14-c-40--c-43-解决方案)
15. [约束与禁止事项](#15-约束与禁止事项)
16. [实现顺序](#16-实现顺序)

---

## 1. 模块概览与基本面

### 1.1 两个基本面

```
基本面 1：OpenAPI yaml 是对外能力的唯一来源
  - 所有 /iam/** 接口必须有 x-required-permission（登录接口除外）
  - /internal/v1/** 接口不需要（Service JWT + 调用方白名单替代）
  - 权限码集合以 yaml 为准，PermissionSyncRunner 启动时自动同步

基本面 2：Liquibase XML 是数据库的唯一来源
  - 禁止手写任何 entity 类
  - jOOQ 在编译期从数据库生成 Record 和 POJO（Liquibase 先跑，jOOQ 后生成）
  - IamMapper 只负责 jOOQ POJO → OpenAPI DTO 的转换

Liquibase 使用 XML 格式的理由：
  1. 内置抽象类型（BIGINT/VARCHAR/DATETIME），自动翻译为 H2/MySQL 方言，一份文件兼容两库
  2. XSD 约束，IDEA/VSCode 有字段补全和实时校验
  3. 高级属性（runOnChange/failOnError）支持最完整
```

### 1.2 实现范围

| 功能域             | 接口路径前缀                       | yaml 文件                          |
|-----------------|------------------------------|----------------------------------|
| 认证（登录/绑定/刷新/登出） | `/auth/**`                   | `main-service-api.yaml`          |
| 用户管理            | `/iam/users/**`              | `main-service-api.yaml`          |
| 数据权限管理          | `/iam/users/{id}/data-scope` | `main-service-api.yaml`          |
| 角色管理            | `/iam/roles/**`              | `main-service-api.yaml`          |
| 权限查询            | `/iam/permissions/**`        | `main-service-api.yaml`          |
| 微信绑定管理          | `/iam/users/me/wechat/**`    | `main-service-api.yaml`          |
| TENANT 预创建（内部）  | `/internal/v1/users`         | `main-service-internal-api.yaml` |

### 1.3 路由约定（GW-Q-8 解决）

Gateway StripPrefix=2，Controller 收到 Strip 后路径：

```
外部路径（Gateway 侧）                     main-service Controller
/api/main-service/auth/**           →      /auth/**
/api/main-service/iam/**            →      /iam/**
（内部接口不经 Gateway）                    /internal/v1/**
```

C-42 双端对齐：

- Gateway `permit-paths`：`/api/main-service/auth/**`
- main-service `security.permit-paths`：`/auth/**`、`/internal/v1/**`

---

## 2. 核心设计原则

```
原则 1：系统先有用户，再有登录
        任何登录端点均不触发新用户创建。
        未找到对应用户 → IAM_AUTH_USER_NOT_REGISTERED

原则 2：绑定是首次微信登录的必经路径，绑定即登录
        TENANT  小程序：服务端解密手机号 → mobile 匹配预创建用户 → 写 Identity → 签发 JWT
        STAFF   小程序：mobile 匹配优先；无 mobile → bindToken 两步绑定（用户名密码）
        ADMIN/STAFF Web：首次必须 bindToken 两步绑定
        Web 端不支持 TENANT 首次绑定

原则 3：两步绑定用 bindToken（Redis 5 分钟，一次性）
        第一步：无 Identity → 生成 bindToken → HTTP 403 + bindToken
        第二步：提交 { bindToken, username, password } → 验证 → 写 Identity → 签发 JWT

原则 4：权限以 OpenAPI yaml 的 x-required-permission 为唯一来源
        PermissionSyncRunner 在应用启动时幂等同步（upsert + 软删除）
        BUILTIN 角色的权限关联由 Runner 全量维护，不走 API

原则 5：内置角色（BUILTIN）两类约束
        - 权限不可修改：role_type=BUILTIN → PUT /iam/roles/{id}/permissions 拒绝
        - 分配可锁定：lock_role_assignment=1 → PUT /iam/users/{id}/roles 拒绝
        （两个约束相互独立：SYSTEM_ADMIN 权限不可改但可以分配，
         INSTALLER 权限不可改且不可变更用户分配）

原则 6：jOOQ 生成所有数据库映射类，禁止手写 entity
        jOOQ POJO 即为 Domain Model，IamMapper 负责 POJO ↔ OpenAPI DTO 转换
        复杂业务（登录/绑定）的中间状态用 Java record 表达，不建独立 Domain Model

原则 7：数据权限绑定在用户上
        仅 STAFF 类型用户有数据权限（iam_user_data_scope）
        ADMIN 全量访问（代码特判），TENANT 只看自身数据（由业务层过滤）
        scope_type=ALL 使用特殊标记而非枚举所有 ID，新增区域/门店自动生效

原则 8：ID 透明化，未来兼容雪花算法
        当前使用 AUTO_INCREMENT，未来通过 jOOQ ExecuteListener 统一拦截 INSERT 注入 ID
        业务代码无感知
```

---

## 3. 用户与角色能力矩阵

### 3.1 登录方式

| 用户类型   | 密码登录 | 微信小程序                | 微信 Web 扫码  | 创建方式                     |
|--------|------|----------------------|------------|--------------------------|
| ADMIN  | ✅    | ❌                    | ✅（首次需两步绑定） | Liquibase 初始化            |
| STAFF  | ✅    | ✅（首次 mobile 匹配或两步绑定） | ✅（首次需两步绑定） | Admin 调 POST /iam/users  |
| TENANT | ❌    | ✅（首次 mobile 匹配，唯一路径） | ✅（已绑定后）    | 业务系统调 /internal/v1/users |
| SYSTEM | ❌    | ❌                    | ❌          | Liquibase 初始化            |

### 3.2 内置角色清单

| id | name  | code         | role_type | lock_role_assignment | data_scope_dimension |
|----|-------|--------------|-----------|----------------------|----------------------|
| 1  | 系统管理员 | SYSTEM_ADMIN | BUILTIN   | 0（可分配）               | NULL（全量访问）           |
| 2  | 安装工   | INSTALLER    | BUILTIN   | 1（锁定）                | NULL                 |
| 3  | 租客    | TENANT_ROLE  | BUILTIN   | 1（锁定）                | NULL                 |

> CUSTOM 角色通过 POST /iam/roles 创建，id 从 4+ 开始自增。

### 3.3 数据权限维度

```
层级：区域（area）> 门店（store）
      一个区域下有多个门店

适用用户：STAFF 类型
维度枚举：
  AREA  → 区域维度角色（总部管理员/运营管理员/区域管理员）
  STORE → 门店维度角色（财务/门店管家/管家）

范围类型：
  ALL      → 全部资源，scope_type=ALL，resource_id=NULL，新增资源后自动生效
  SPECIFIC → 指定具体资源，scope_type=SPECIFIC，每条记录对应一个 resource_id

ADMIN  → 全量访问，代码特判，不使用 iam_user_data_scope
TENANT → 只看自身数据，由业务层过滤，不使用 iam_user_data_scope
```

---

## 4. 数据模型（完整字段）

### 4.1 表关系

```
iam_user (1) ──(1:1)──► iam_credential         仅 ADMIN/STAFF 有密码记录
iam_user (1) ──(1:N)──► iam_identity            每种登录方式一条
iam_user (M) ──(M:N)──► iam_role    → iam_user_role
iam_role (M) ──(M:N)──► iam_permission → iam_role_permission
iam_user (1) ──(1:N)──► iam_user_data_scope     仅 STAFF 有记录
```

### 4.2 iam_user

| 列          | 类型           | 约束                         | 说明                              |
|------------|--------------|----------------------------|---------------------------------|
| id         | BIGINT       | PK, AUTO_INCREMENT         | 当前自增，可通过 ExecuteListener 切换雪花   |
| type       | VARCHAR(20)  | NOT NULL                   | ADMIN / STAFF / TENANT / SYSTEM |
| status     | VARCHAR(20)  | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / INACTIVE               |
| real_name  | VARCHAR(100) | NULL                       | 真实姓名，选填                         |
| mobile     | VARCHAR(20)  | NULL, UNIQUE               | TENANT 必填；ADMIN/STAFF 选填        |
| email      | VARCHAR(200) | NULL, UNIQUE               | 邮箱，选填，全局唯一                      |
| source     | VARCHAR(50)  | NOT NULL                   | MANUAL / WECHAT / 业务系统标识        |
| created_by | BIGINT       | NULL                       | 创建者 user_id                     |
| created_at | DATETIME(3)  | NOT NULL                   |                                 |
| updated_at | DATETIME(3)  | NOT NULL                   |                                 |

### 4.3 iam_identity

| 列                | 类型           | 约束                 | 说明                              |
|------------------|--------------|--------------------|---------------------------------|
| id               | BIGINT       | PK, AUTO_INCREMENT |                                 |
| user_id          | BIGINT       | NOT NULL           | FK → iam_user                   |
| provider         | VARCHAR(20)  | NOT NULL           | password / wechat               |
| provider_user_id | VARCHAR(100) | NOT NULL           | password→username；wechat→openid |
| union_id         | VARCHAR(100) | NULL               | 微信 unionid（跨小程序/公众号唯一）          |
| app_id           | VARCHAR(100) | NULL               | 来源 appId                        |
| created_at       | DATETIME(3)  | NOT NULL           |                                 |

唯一约束：`UNIQUE(provider, provider_user_id)`  
索引：`idx_iam_identity_user_id`、`idx_iam_identity_union_id`

### 4.4 iam_credential

| 列             | 类型           | 约束       | 说明             |
|---------------|--------------|----------|----------------|
| user_id       | BIGINT       | PK       | 1:1 → iam_user |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt 强度 10   |
| created_at    | DATETIME(3)  | NOT NULL |                |
| updated_at    | DATETIME(3)  | NOT NULL |                |

### 4.5 iam_role

| 列                    | 类型           | 约束                         | 说明                  |
|----------------------|--------------|----------------------------|---------------------|
| id                   | BIGINT       | PK, AUTO_INCREMENT         |                     |
| name                 | VARCHAR(50)  | NOT NULL                   | 显示名                 |
| code                 | VARCHAR(50)  | NOT NULL, UNIQUE           | 大写下划线，创建后不可修改       |
| role_type            | VARCHAR(20)  | NOT NULL, DEFAULT 'CUSTOM' | BUILTIN / CUSTOM    |
| lock_role_assignment | TINYINT(1)   | NOT NULL, DEFAULT 0        | 1=角色分配锁定            |
| data_scope_dimension | VARCHAR(20)  | NULL                       | AREA / STORE / NULL |
| description          | VARCHAR(200) | NULL                       |                     |
| created_at           | DATETIME(3)  | NOT NULL                   |                     |
| updated_at           | DATETIME(3)  | NOT NULL                   |                     |

### 4.6 iam_permission

| 列           | 类型           | 约束                 | 说明                        |
|-------------|--------------|--------------------|---------------------------|
| id          | BIGINT       | PK, AUTO_INCREMENT |                           |
| code        | VARCHAR(100) | NOT NULL, UNIQUE   | `{resource}:{action}` 全小写 |
| name        | VARCHAR(100) | NOT NULL           | 初始值 = code，可手动更新          |
| resource    | VARCHAR(100) | NOT NULL           | code 最后冒号前（如 `iam:user`）  |
| action      | VARCHAR(50)  | NOT NULL           | code 最后冒号后（如 `read`）      |
| description | VARCHAR(200) | NULL               |                           |
| deleted_at  | DATETIME(3)  | NULL               | 软删除，NULL=有效               |
| created_at  | DATETIME(3)  | NOT NULL           |                           |
| updated_at  | DATETIME(3)  | NOT NULL           |                           |

### 4.7 iam_user_role

| 列          | 类型          | 约束       |
|------------|-------------|----------|
| user_id    | BIGINT      | NOT NULL |
| role_id    | BIGINT      | NOT NULL |
| created_at | DATETIME(3) | NOT NULL |

PK: `(user_id, role_id)`

### 4.8 iam_role_permission

| 列             | 类型     | 约束       |
|---------------|--------|----------|
| role_id       | BIGINT | NOT NULL |
| permission_id | BIGINT | NOT NULL |

PK: `(role_id, permission_id)`

### 4.9 iam_user_data_scope

| 列               | 类型          | 约束                 | 说明                             |
|-----------------|-------------|--------------------|--------------------------------|
| id              | BIGINT      | PK, AUTO_INCREMENT |                                |
| user_id         | BIGINT      | NOT NULL           | FK → iam_user（仅 STAFF）         |
| scope_dimension | VARCHAR(20) | NOT NULL           | AREA / STORE                   |
| scope_type      | VARCHAR(20) | NOT NULL           | ALL / SPECIFIC                 |
| resource_id     | BIGINT      | NULL               | area_id 或 store_id，ALL 时为 NULL |
| created_at      | DATETIME(3) | NOT NULL           |                                |

索引：`idx_iam_user_data_scope_user_id`、`idx_iam_user_data_scope_user_dim(user_id, scope_dimension)`

> **覆写逻辑：** PUT 时先 `DELETE WHERE user_id=? AND scope_dimension=?`，再批量 INSERT。
> 不用数据库唯一约束（`resource_id=NULL` 时 MySQL UNIQUE 不生效），由 Service 层保证。

---

## 5. OpenAPI 契约规范

### 5.1 文件结构

```
common/src/main/resources/openapi/
  common-components.yaml                  公共 Schema（所有服务共用）

main-service/src/main/resources/openapi/
  main-service-api.yaml                   对外接口（经 Gateway）
  main-service-internal-api.yaml          内部接口（服务间直调）
```

公共组件 $ref 路径（main-service 视角）：

```
../../../../../common/src/main/resources/openapi/common-components.yaml#/components/schemas/Xxx
```

### 5.2 common-components.yaml 包含

| Schema            | 说明                                                  |
|-------------------|-----------------------------------------------------|
| `ErrorResponse`   | 全系统统一错误响应（扁平格式）                                     |
| `PageRequest`     | 通用分页请求（pageNo 从 1 开始，含 filters）                     |
| `QueryFilter`     | 过滤容器（stringFilters / idsFilters / enumFilters，均为数组） |
| `StringFilter`    | 字符串模糊过滤（LIKE %value%）                               |
| `IdsFilter`       | 多值 IN 过滤（IN(v1,v2,...)）                             |
| `EnumFilter`      | 枚举精确过滤（= value）                                     |
| `PageResponse`    | 分页响应基础（含 tableMeta，各资源 allOf 追加 items）              |
| `TableMeta`       | 表格字段元数据（随每次分页响应返回，后端缓存）                             |
| `FilterFieldMeta` | 单字段元数据（key/label/filterType/options）                |
| `FilterOption`    | 枚举下拉选项（value/label）                                 |
| `BatchRequest`    | 通用批量操作（ids 数组，单个传 1 个）                              |

> `WechatBindRequiredResponse` 是 IAM 特有响应，**定义在 main-service-api.yaml 中**，不在 common。

### 5.3 openapi-generator 配置要求

```xml
<configOptions>
    <openApiNullable>false</openApiNullable>  <!-- 禁止 JsonNullable 包装 -->
    <useSpringBoot3>true</useSpringBoot3>
    <delegatePattern>true</delegatePattern>
    <interfaceOnly>true</interfaceOnly>
    <useTags>true</useTags>
</configOptions>
```

`openApiNullable=false` 原因：yaml 中 `nullable: true` 有三态语义（未传/null/有值），
但过滤字段"未传"和"传 null"效果相同，不需要 JsonNullable 区分，关闭后生成干净的 `List<T>`。

### 5.4 allOf 行为说明

Spring generator 7.x 对 allOf 默认生成**扁平类**（所有字段内联，不生成 Java 继承）：

```java
// WechatBindRequiredResponse 实际生成（正确且符合预期）
class WechatBindRequiredResponse {
    String code;       // 来自 ErrorResponse
    String message;    // 来自 ErrorResponse
    String traceId;    // 来自 ErrorResponse
    String bindToken;  // 自身字段
}
```

分页响应通过 allOf 继承 PageResponse 追加 items 字段，生成扁平类（需 generator 7.x）。

### 5.5 x-required-permission 完整清单（共 9 个唯一权限码）

| 权限码                    | 对应接口                                                                                             |
|------------------------|--------------------------------------------------------------------------------------------------|
| `iam:user:create`      | POST /iam/users                                                                                  |
| `iam:user:read`        | GET /iam/users/query、GET /iam/users/{id}、GET /iam/users/{id}/data-scope                          |
| `iam:user:write`       | PUT /iam/users/{id}/status、PUT /iam/users/batch/status、PUT /iam/users/{id}/roles                 |
| `iam:user:scope:write` | PUT /iam/users/{id}/data-scope                                                                   |
| `iam:role:read`        | POST /iam/roles/query                                                                            |
| `iam:role:write`       | POST /iam/roles、PUT /iam/roles/{id}、POST /iam/roles/batch-delete、PUT /iam/roles/{id}/permissions |
| `iam:permission:read`  | POST /iam/permissions/query                                                                      |
| `iam:wechat:bind`      | POST /iam/users/me/wechat/bind                                                                   |
| `iam:wechat:unbind`    | DELETE /iam/users/me/wechat/bind                                                                 |

---

## 6. 权限同步机制（PermissionSyncRunner）

### 6.1 与 Liquibase 职责边界

| 职责                | Liquibase XML | PermissionSyncRunner |
|-------------------|---------------|----------------------|
| 建表 DDL            | ✅             | ❌                    |
| SYSTEM 用户         | ✅             | ❌                    |
| BUILTIN 角色（结构）    | ✅             | ❌                    |
| 初始 ADMIN 账号       | ✅             | ❌                    |
| iam_permission 数据 | ❌             | ✅（全量管理）              |
| BUILTIN 角色权限关联    | ❌             | ✅（全量维护）              |

### 6.2 同步逻辑

```
@Component @Order(1)
class PermissionSyncRunner implements ApplicationRunner {

  @Override @Transactional
  void run(ApplicationArguments args):

    ① 扫描 classpath openapi/ 目录下所有 yaml
       解析 paths → operations → x-required-permission
       收集 Set<String> yamlCodes（共 9 个唯一码）

    ② 对每个 yamlCode：
       resource = code.substring(0, code.lastIndexOf(':'))
       action   = code.substring(code.lastIndexOf(':') + 1)
       DB 查 iam_permission WHERE code = yamlCode：
         - 不存在      → INSERT (code, name=code, resource, action, now, now)
         - deleted_at ≠ NULL → UPDATE SET deleted_at=NULL, updated_at=now  （恢复）
         - deleted_at IS NULL → 跳过

    ③ 软删除 yaml 中已移除的权限：
       UPDATE iam_permission
       SET deleted_at=NOW(3), updated_at=NOW(3)
       WHERE code NOT IN (:yamlCodes) AND deleted_at IS NULL

    ④ 维护 BUILTIN 角色权限（全量替换为当前所有有效权限）：
       activeIds = SELECT id FROM iam_permission WHERE deleted_at IS NULL
       builtinIds = SELECT id FROM iam_role WHERE role_type='BUILTIN'
       FOR EACH builtinRoleId:
         DELETE FROM iam_role_permission WHERE role_id = builtinRoleId
         INSERT INTO iam_role_permission VALUES (builtinRoleId, each activeId)

    ⑤ 日志：新增 N 个，恢复 K 个，软删除 M 个
}
```

---

## 7. Redis 数据结构

### 7.1 Refresh Token

```
Key:   iam:rt:{UUID}
Value: JSON { "userId": 42, "username": "zhangsan", "userType": "STAFF" }
TTL:   604800 秒（7 天）
```

Rotation 机制：`/auth/refresh` 时 DEL 旧 key + SET 新 key（旧 token 立即失效）

### 7.2 Bind Token（两步绑定专用）

```
Key:   iam:bt:{UUID}
Value: JSON {
  "openid":    "wx_openid_xxx",
  "unionId":   "wx_unionid_xxx",   // nullable
  "appId":     "wx...",
  "loginType": "WEB" | "MINIPROGRAM"
}
TTL:   300 秒（5 分钟）
一次性: consumeAndGet 先 GET 后立即 DEL，防重放
```

### 7.3 常量类

```java
// infrastructure/redis/IamRedisKeys.java
public final class IamRedisKeys {
    public static final long REFRESH_TOKEN_TTL_SECONDS = 604800L;
    public static final long BIND_TOKEN_TTL_SECONDS    = 300L;
    private static final String RT_PREFIX = "iam:rt:";
    private static final String BT_PREFIX = "iam:bt:";

    private IamRedisKeys() {}

    public static String refreshToken(String token) { return RT_PREFIX + token; }
    public static String bindToken(String token)    { return BT_PREFIX + token; }
}
```

---

## 8. 对象映射规范

### 8.1 分层规则

```
OpenAPI DTO/VO（openapi-generator 生成，不手写）
       ↕  IamMapper（静态方法，手写，负责 POJO ↔ DTO 转换）
jOOQ POJO（Liquibase → jOOQ 编译期生成，record.into(XxxPojo.class)）
       ↕  jOOQ 内置转换
jOOQ Record（编译期生成）
```

### 8.2 两条映射路径

**路径 A — 简单 CRUD（Role/Permission 增删改查）：**

```
DTO → Service → Repository（jOOQ POJO）→ IamMapper → DTO
```

**路径 B — 复杂业务（登录/绑定/Token）：**

```
DTO → Service（持有 POJO 做业务决策，中间状态用 record）→ Repository
           ↓ IamMapper（返回前转换）
         DTO
```

### 8.3 IamMapper 规范

```java
// infrastructure/mapper/IamMapper.java
// 静态方法，无 Spring 注解，纯转换，JDK 8+ 语法
// record / Lombok @Builder 优先，禁止 手写 setter

public final class IamMapper {
    private IamMapper() {}

    // jOOQ POJO → OpenAPI DTO
    public static User toUser(IamUserPojo pojo) { ... }
    public static UserDetail toUserDetail(IamUserPojo pojo, List<IamRolePojo> roles,
                                          List<IamUserDataScopePojo> scopes) { ... }
    public static Role toRole(IamRolePojo pojo) { ... }
    public static RoleDetail toRoleDetail(IamRolePojo pojo, List<IamPermissionPojo> perms) { ... }
    public static Permission toPermission(IamPermissionPojo pojo) { ... }
    public static DataScopeItem toDataScopeItem(IamUserDataScopePojo pojo) { ... }

    // OpenAPI DTO → jOOQ POJO（INSERT 用）
    public static IamUserPojo fromCreateStaff(CreateStaffRequest req, long createdBy) { ... }
    public static IamRolePojo fromCreateRole(CreateRoleRequest req) { ... }
}
```

### 8.4 代码风格

1. **优先 JDK 8+ 语法**：Stream、Optional、方法引用、lambda
2. **优先 Java record**：值对象（BindTokenPayload、WechatAuthContext 等）
3. **次选 Lombok**：需要 Builder 或可变对象时
4. **最后手写**：仅 Lombok 无法满足时，需注释说明原因

---

## 9. 核心业务流程

### 9.1 密码登录

```
PasswordLoginService.login(username, password)

① IdentityRepo.findByProviderAndProviderUserId("password", username)
   └── 未找到 → throw(401, IAM_AUTH_INVALID_CREDENTIALS)   // 不区分用户名/密码错误，防枚举

② user = UserRepo.findById(identity.userId)

③ user.type ∈ {ADMIN, STAFF}
   └── 否 → throw(403, IAM_AUTH_LOGIN_METHOD_NOT_ALLOWED)

④ user.status == ACTIVE
   └── 否 → throw(403, IAM_USER_ACCOUNT_DISABLED)

⑤ credential = CredentialRepo.findByUserId(userId)
   BCrypt.matches(password, credential.passwordHash)
   └── 失败 → throw(401, IAM_AUTH_INVALID_CREDENTIALS)

⑥ permissions = PermissionRepo.findCodesByUserId(userId)

⑦ accessToken  = UserJwtIssuer.issue(userId, username, permissions)
   refreshToken = RefreshTokenService.create(userId, username, user.type)

⑧ return LoginResult
```

### 9.2 微信小程序登录

```
WechatLoginService.loginByMiniprogram(code, appId, encryptedData, iv)

① WechatClient.code2Session(code, appId) → { openid, unionid, sessionKey }
   └── 失败 → throw(400, IAM_AUTH_WECHAT_CODE_INVALID)

② identity = IdentityDomainService.findWechatIdentity(openid, unionid)

③ identity 已存在 → 走【直接登录（9.5）】

④ identity 不存在（首次绑定）：

   有 encryptedData + iv：
     mobile = WechatDecryptor.decryptPhone(encryptedData, iv, sessionKey)
     └── 失败 → throw(400, IAM_AUTH_WECHAT_DECRYPT_FAILED)
     user = UserRepo.findByMobile(mobile)
     └── 未找到 → throw(403, IAM_AUTH_USER_NOT_REGISTERED)  // 不自动创建
     user.type == ADMIN → throw(403, IAM_AUTH_LOGIN_METHOD_NOT_ALLOWED)
     @Transactional → IdentityRepo.save(userId, "wechat", openid, unionid, appId)
     → 走【直接登录（9.5）】

   无 encryptedData：
     user.type == TENANT → throw(403, IAM_AUTH_WECHAT_PHONE_REQUIRED)  // TENANT 无降级
     bindToken = BindTokenService.create(openid, unionid, appId, "MINIPROGRAM")
     throw WechatBindRequiredException(bindToken)  // → HTTP 403 + bindToken
```

### 9.3 两步绑定第二步（小程序/Web 通用）

```
WechatLoginService.bindByCredential(bindToken, username, password)

① payload = BindTokenService.consumeAndGet(bindToken)
   └── 未找到 → throw(401, IAM_AUTH_BIND_TOKEN_INVALID)
   （GET 后立即 DEL，一次性消费）

② identity = IdentityRepo.findByProviderAndProviderUserId("password", username)
   └── 未找到 → throw(401, IAM_AUTH_INVALID_CREDENTIALS)

③ user = UserRepo.findById(identity.userId)

④ loginType=MINIPROGRAM 时 user.type == STAFF，否则 → throw(403, IAM_AUTH_LOGIN_METHOD_NOT_ALLOWED)
   loginType=WEB 时      user.type ∈ {ADMIN, STAFF}，否则 → throw(403)

⑤ user.status == ACTIVE
   └── 否 → throw(403, IAM_USER_ACCOUNT_DISABLED)

⑥ BCrypt.matches(password, credential.passwordHash)
   └── 失败 → throw(401, IAM_AUTH_INVALID_CREDENTIALS)

⑦ payload.unionId 已绑定其他用户 → throw(409, IAM_IDENTITY_WECHAT_ALREADY_BOUND)

⑧ @Transactional
   IdentityRepo.save(userId, "wechat", payload.openid, payload.unionId, payload.appId)

⑨ 走【直接登录（9.5）】
```

### 9.4 微信 Web 扫码登录

```
WechatLoginService.loginByWeb(code, appId)

① WechatClient.getWebUserInfo(code, appId) → { openid, unionid }  // 服务端 code exchange
   └── 失败 → throw(400, IAM_AUTH_WECHAT_CODE_INVALID)

② identity = IdentityDomainService.findWechatIdentity(openid, unionid)

③ identity 已存在 → 走【直接登录（9.5）】

④ identity 不存在：
   → bindToken = BindTokenService.create(openid, unionid, appId, "WEB")
   → throw WechatBindRequiredException(bindToken)
   （Web 端不支持 TENANT 首次绑定，在两步绑定第二步时通过 user.type 校验拒绝）
```

### 9.5 直接登录（有 Identity 后的公共流程）

```
① user = UserRepo.findById(identity.userId)
② user.status == ACTIVE，否则 → throw(403, IAM_USER_ACCOUNT_DISABLED)
③ permissions = PermissionRepo.findCodesByUserId(userId)
④ sub 确定：
   ADMIN/STAFF → identity.providerUserId（username）
   TENANT      → identity.unionId ?? identity.providerUserId（openid）
⑤ accessToken  = UserJwtIssuer.issue(userId, sub, permissions)
   refreshToken = RefreshTokenService.create(userId, sub, user.type)
⑥ return LoginResult(accessToken, refreshToken, 1800, userId, user.type)
```

### 9.6 Token 刷新

```
① payload = RefreshTokenService.validate(refreshToken)
   └── 未找到 → throw(401, IAM_AUTH_REFRESH_TOKEN_INVALID)
② user = UserRepo.findById(payload.userId)
③ user.status == ACTIVE，否则 → throw(403, IAM_USER_ACCOUNT_DISABLED)
④ permissions = PermissionRepo.findCodesByUserId(userId)  // 重新加载，权限可能已变更
⑤ newAccessToken  = UserJwtIssuer.issue(userId, payload.username, permissions)
   newRefreshToken = RefreshTokenService.rotate(oldToken, userId, payload.username, user.type)
⑥ return RefreshResult(newAccessToken, newRefreshToken, 1800)
```

### 9.7 TENANT 预创建（内部接口）

```
InternalUserService.createTenant(mobile, source)

① CurrentUser.getCallerName() ∈ 白名单（billing-service/device-service/main-service）
   └── 否 → throw(403, IAM_INTERNAL_CALLER_NOT_ALLOWED)
② UserRepo.findByMobile(mobile)
   └── 已存在 → throw(409, IAM_USER_MOBILE_CONFLICT)
③ @Transactional
   userId = UserRepo.save(IamUserPojo{type=TENANT, status=ACTIVE, mobile, source})
④ return CreateTenantResult{userId, mobile, type=TENANT, status=ACTIVE}
```

### 9.8 权限查询 SQL（PermissionRepo.findCodesByUserId）

```sql
SELECT DISTINCT p.code
FROM iam_permission p
    JOIN iam_role_permission rp ON rp.permission_id = p.id
    JOIN iam_user_role       ur ON ur.role_id = rp.role_id
WHERE ur.user_id    = :userId
  AND p.deleted_at IS NULL
```

---

## 10. 分层架构与类设计

### 10.1 包结构

```
com.jugu.propertylease.main.iam/
│
├── controller/
│   ├── AuthController.java              /auth/**
│   ├── IamController.java               /iam/**
│   └── InternalUserController.java      /internal/v1/users
│
├── application/
│   ├── PasswordLoginService.java
│   ├── WechatLoginService.java          登录 + 两步绑定
│   ├── RefreshTokenService.java
│   ├── BindTokenService.java
│   ├── PermissionSyncRunner.java        ApplicationRunner，启动时同步权限
│   ├── UserApplicationService.java
│   ├── InternalUserService.java
│   └── RoleApplicationService.java
│   （DTO 由 openapi-generator 生成，不手写）
│
├── domain/
│   └── service/
│       └── IdentityDomainService.java   微信 Identity 查找/绑定逻辑
│   （无手写 Model 类，jOOQ 生成的 POJO 即为数据模型）
│
├── infrastructure/
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── IdentityRepository.java
│   │   ├── CredentialRepository.java
│   │   ├── RoleRepository.java
│   │   ├── PermissionRepository.java
│   │   ├── DataScopeRepository.java
│   │   └── impl/（6 个 RepositoryImpl）
│   │
│   ├── mapper/
│   │   └── IamMapper.java
│   │
│   ├── external/
│   │   ├── WechatClient.java            interface
│   │   ├── dto/
│   │   │   ├── WechatSessionResponse.java   record
│   │   │   └── WechatUserInfo.java          record
│   │   ├── WechatDecryptor.java         AES-128-CBC 手机号解密
│   │   └── WechatClientStub.java        占位实现（wechat.stub.enabled=true）
│   │
│   ├── jwt/
│   │   └── UserJwtIssuer.java           签发 User JWT（jwt.user.secret）
│   │
│   └── redis/
│       └── IamRedisKeys.java
│
├── config/
│   └── JooqConfig.java                  注册 IamDataPermissionVisitListener
│
├── exception/
│   └── WechatBindRequiredException.java RuntimeException，携带 bindToken
│
└── audit/
    └── IamAuditLogger.java              implements AuditLogger
```

### 10.2 关键类契约

#### UserJwtIssuer

```java
// ⚠️ 使用 security.jwt.user.secret，不是 jwt.service.secret
// ⚠️ 不复用 ServiceTokenGenerator（User JWT ≠ Service JWT）
@Component
public class UserJwtIssuer {
    // 注入 SecurityProperties.getJwt().getUser().getSecret() / getExpiration()

    /**
     * @param userId      iam_user.id
     * @param username    ADMIN/STAFF: credential username；TENANT: unionId ?? openid
     * @param permissions permission code 列表
     */
    public String issue(long userId, String username, List<String> permissions) { ... }
}
```

#### BindTokenService

```java
@Service
public class BindTokenService {

    public String create(String openid, @Nullable String unionId,
                         String appId, String loginType)

    /**
     * 原子消费：GET 后立即 DEL，防重放
     * @throws BusinessException(401, IAM_AUTH_BIND_TOKEN_INVALID)
     */
    public BindTokenPayload consumeAndGet(String bindToken)

    public record BindTokenPayload(
        String openid,
        @Nullable String unionId,
        String appId,
        String loginType   // "WEB" | "MINIPROGRAM"
    ) {}
}
```

#### WechatBindRequiredException

```java
public class WechatBindRequiredException extends RuntimeException {
    private final String bindToken;
    public WechatBindRequiredException(String bindToken) { this.bindToken = bindToken; }
    public String getBindToken() { return bindToken; }
}

// GlobalExceptionHandler 扩展处理：
@ExceptionHandler(WechatBindRequiredException.class)
public ResponseEntity<WechatBindRequiredResponse> handleBindRequired(
        WechatBindRequiredException ex) {
    String traceId = MDC.get("traceId");
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new WechatBindRequiredResponse(
            IamErrorCodes.AUTH_WECHAT_BIND_REQUIRED,
            "请使用账号密码完成绑定",
            traceId,
            ex.getBindToken()));
}
```

#### WechatDecryptor

```java
@Component
public class WechatDecryptor {
    /**
     * AES-128-CBC + PKCS7，Base64 编解码
     * 解密微信 wx.getPhoneNumber() 返回的加密手机号
     * @throws BusinessException(400, IAM_AUTH_WECHAT_DECRYPT_FAILED)
     */
    public String decryptPhone(String encryptedData, String iv, String sessionKey) { ... }
}
```

### 10.3 Repository 接口契约

```java
interface UserRepository {
    Optional<IamUserPojo> findById(long id);
    Optional<IamUserPojo> findByMobile(String mobile);
    long save(IamUserPojo user);           // 返回 generated id
    void update(IamUserPojo user);
    Page<IamUserPojo> query(QueryFilter filter, int pageNo, int pageSize);
}

interface IdentityRepository {
    Optional<IamIdentityPojo> findByProviderAndProviderUserId(String provider, String puid);
    Optional<IamIdentityPojo> findByUnionId(String provider, String unionId);
    List<IamIdentityPojo> findByUserId(long userId);
    Optional<IamIdentityPojo> findPasswordIdentity(long userId);
    void save(IamIdentityPojo identity);
    void deleteByUserIdAndProvider(long userId, String provider);
    boolean existsByUnionId(String unionId);
}

interface CredentialRepository {
    Optional<IamCredentialPojo> findByUserId(long userId);
    void save(IamCredentialPojo credential);
}

interface PermissionRepository {
    List<String> findCodesByUserId(long userId);   // 含 deleted_at IS NULL 条件
    Page<IamPermissionPojo> query(QueryFilter filter, int pageNo, int pageSize);
    List<IamPermissionPojo> findByIds(List<Long> ids);
    List<Long> findAllActiveIds();
}

interface RoleRepository {
    Page<IamRolePojo> query(QueryFilter filter, int pageNo, int pageSize);
    Optional<IamRolePojo> findById(long id);
    Optional<IamRolePojo> findByCode(String code);
    long save(IamRolePojo role);
    void update(IamRolePojo role);
    void deleteByIds(List<Long> ids);
    boolean hasUsers(long roleId);
    List<IamRolePojo> findByUserId(long userId);
    List<IamPermissionPojo> findPermissionsByRoleId(long roleId);
    void replaceUserRoles(long userId, List<Long> roleIds);
    void replaceRolePermissions(long roleId, List<Long> permissionIds);
    List<Long> findBuiltinRoleIds();
}

interface DataScopeRepository {
    List<IamUserDataScopePojo> findByUserId(long userId);
    void replaceByUserIdAndDimension(long userId, String dimension,
                                     List<IamUserDataScopePojo> newScopes);
    void deleteByUserId(long userId);
}
```

---

## 11. 错误码规范

格式：`{SERVICE}_{RESOURCE}_{REASON}`，全大写下划线。

| 错误码                                      | HTTP | 说明                                |
|------------------------------------------|------|-----------------------------------|
| `IAM_AUTH_INVALID_CREDENTIALS`           | 401  | 用户名或密码错误（不区分，防枚举）                 |
| `IAM_AUTH_LOGIN_METHOD_NOT_ALLOWED`      | 403  | 用户类型不支持此登录方式                      |
| `IAM_AUTH_USER_NOT_REGISTERED`           | 403  | 系统中不存在对应用户                        |
| `IAM_AUTH_WECHAT_CODE_INVALID`           | 400  | 微信 code 无效或过期                     |
| `IAM_AUTH_WECHAT_PHONE_REQUIRED`         | 403  | TENANT 首次登录必须授权手机号                |
| `IAM_AUTH_WECHAT_DECRYPT_FAILED`         | 400  | 手机号解密失败                           |
| `IAM_AUTH_WECHAT_BIND_REQUIRED`          | 403  | 需要两步绑定（响应含 bindToken）             |
| `IAM_AUTH_BIND_TOKEN_INVALID`            | 401  | bindToken 不存在/已过期/已使用             |
| `IAM_AUTH_REFRESH_TOKEN_INVALID`         | 401  | Refresh Token 不存在或已过期             |
| `IAM_USER_ACCOUNT_DISABLED`              | 403  | 账号已禁用                             |
| `IAM_USER_NOT_FOUND`                     | 404  | 用户 ID 不存在                         |
| `IAM_USER_USERNAME_CONFLICT`             | 409  | 用户名已被使用                           |
| `IAM_USER_MOBILE_CONFLICT`               | 409  | 手机号已被使用                           |
| `IAM_USER_EMAIL_CONFLICT`                | 409  | 邮箱已被使用                            |
| `IAM_USER_TYPE_NOT_CREATABLE`            | 400  | 不允许通过 API 创建该类型用户                 |
| `IAM_USER_CANNOT_DISABLE_SELF`           | 400  | 不允许禁用操作者自身                        |
| `IAM_USER_SYSTEM_IMMUTABLE`              | 400  | SYSTEM 用户不可修改                     |
| `IAM_USER_ROLE_ASSIGNMENT_LOCKED`        | 400  | 用户角色分配已锁定（lock_role_assignment=1） |
| `IAM_USER_ROLE_CANNOT_BE_EMPTY`          | 400  | 用户至少需要一个角色                        |
| `IAM_IDENTITY_WECHAT_ALREADY_BOUND`      | 409  | 该微信已绑定其他账号                        |
| `IAM_IDENTITY_CANNOT_UNBIND_LAST_METHOD` | 400  | 不允许解绑最后一种登录方式                     |
| `IAM_ROLE_NOT_FOUND`                     | 404  | 角色不存在                             |
| `IAM_ROLE_CODE_CONFLICT`                 | 409  | 角色 code 已存在                       |
| `IAM_ROLE_IN_USE`                        | 409  | 角色已分配给用户，无法删除                     |
| `IAM_ROLE_BUILTIN_IMMUTABLE`             | 400  | 内置角色不可修改或删除                       |
| `IAM_PERMISSION_NOT_FOUND`               | 404  | 权限 ID 不存在                         |
| `IAM_DATA_SCOPE_DIMENSION_MISMATCH`      | 400  | 数据权限维度与用户角色不匹配                    |
| `IAM_INTERNAL_CALLER_NOT_ALLOWED`        | 403  | 内部接口调用方不在白名单                      |

---

## 12. User JWT 规范

### 12.1 Claims 结构

```json
{
  "sub":         "zhangsan",
  "userId":      42,
  "permissions": ["iam:user:read", "order:read"],
  "iat":         1742700000,
  "exp":         1742701800
}
```

| 字段            | 说明                                                           |
|---------------|--------------------------------------------------------------|
| `sub`         | ADMIN/STAFF: credential username；TENANT: `unionId ?? openid` |
| `userId`      | iam_user.id                                                  |
| `permissions` | 登录时加载；`/auth/refresh` 时重新加载（权限可能变更）                          |
| `exp`         | iat + `security.jwt.user.expiration`（默认 1800 秒）              |

### 12.2 密钥规则

- `security.jwt.user.secret`：main-service（签发）和 gateway（验证）**必须配置相同值**
- `security.jwt.service.secret`：所有微服务验证入站 Service JWT（独立密钥）

---

## 13. 配置规范

### 13.1 application-local.yml（H2 + mock 模式）

```yaml
spring:
  datasource:
    # H2 必须启用 MySQL 兼容模式，支持 DATETIME(3)、AUTO_INCREMENT、ENGINE=InnoDB 语法
    url: jdbc:h2:mem:property_lease;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password:
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
      path: /h2-console
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  data:
    redis:
      host: localhost
      port: 6379

security:
  mode: mock
  service-name: main-service
  permit-paths:
    - /auth/**
    - /internal/v1/**
  mock-user:
    user-id: 2
    permissions:
      - "iam:user:create"
      - "iam:user:read"
      - "iam:user:write"
      - "iam:user:scope:write"
      - "iam:role:read"
      - "iam:role:write"
      - "iam:permission:read"
      - "iam:wechat:bind"
      - "iam:wechat:unbind"
  jwt:
    user:
      secret: "dev-user-jwt-secret-must-be-at-least-32-chars!!"
      expiration: 1800
    service:
      secret: "dev-service-jwt-secret-must-be-at-least-32-chars!"
      expiration: 300

wechat:
  stub:
    enabled: true   # 本地使用 WechatClientStub，固定返回 openid/unionid
```

### 13.2 application-prod.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:3306/${DB_NAME}?useSSL=true&serverTimezone=UTC
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

security:
  mode: service
  service-name: main-service
  permit-paths:
    - /auth/**
    - /internal/v1/**
  jwt:
    user:
      secret: ${JWT_USER_SECRET}
      expiration: 1800
    service:
      secret: ${JWT_SERVICE_SECRET}
      expiration: 300

wechat:
  stub:
    enabled: false
  miniprogram:
    app-id:     ${WECHAT_MINIPROGRAM_APP_ID}
    app-secret: ${WECHAT_MINIPROGRAM_APP_SECRET}
  web:
    app-id:     ${WECHAT_WEB_APP_ID}
    app-secret: ${WECHAT_WEB_APP_SECRET}
```

### 13.3 Gateway 对应配置（C-42 对齐）

```yaml
security:
  mode: gateway
  service-name: gateway
  permit-paths:
    - /api/main-service/auth/**    # 对应 main-service /auth/**
  jwt:
    user:
      secret: ${JWT_USER_SECRET}   # ⚠️ 必须与 main-service jwt.user.secret 完全相同
      expiration: 1800
    service:
      secret: ${JWT_SERVICE_SECRET}
      expiration: 300
```

---

## 14. C-40 / C-43 解决方案

### 14.1 C-40：mustache vendorExtensions key 格式

openapi-generator 将 `x-required-permission` 原样保留为 vendorExtensions 的 key（连字符格式）。

```mustache
{{! apiDelegate.mustache}}
{{#vendorExtensions.x-required-permission}}
@PreAuthorize("hasPermission(null, '{{vendorExtensions.x-required-permission}}')")
{{/vendorExtensions.x-required-permission}}
```

调试技巧：临时加 `{{#vendorExtensions}}{{.}}{{/vendorExtensions}}` 打印所有 key 确认。

### 14.2 C-43：DefaultConfigurationCustomizer（在 common 模块新增）

```java
// common/.../jooq/JooqConfigSupport.java
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

// main-service/config/JooqConfig.java
@Configuration
public class JooqConfig {
    @Bean
    public DefaultConfigurationCustomizer jooqCustomizer(
            IamDataPermissionVisitListener listener) {
        return JooqConfigSupport.withVisitListener(listener);
    }
}
```

common pom.xml 中 jOOQ 和 spring-boot-autoconfigure 设为 `<optional>true</optional>`，
避免污染非 jOOQ 服务。

---

## 15. 约束与禁止事项

### 安全约束

| 约束                            | 说明                                              |
|-------------------------------|-------------------------------------------------|
| ❌ 登录不触发注册                     | 任何登录端点不创建新 User                                 |
| ❌ 防枚举                         | 密码登录用户名/密码错误统一返回 `IAM_AUTH_INVALID_CREDENTIALS` |
| ❌ sessionKey 不持久化             | 仅在当次请求内存中使用                                     |
| ❌ password_hash 不外泄           | 不得出现在任何 DTO / 响应 / 日志                           |
| ✅ BCrypt 强度固定 10              | `new BCryptPasswordEncoder(10)`                 |
| ✅ Refresh Token Rotation      | 每次 /auth/refresh 必须作废旧 token                    |
| ✅ bindToken 一次性               | consumeAndGet 先 GET 后立即 DEL，防重放                 |
| ✅ TENANT sub 优先 unionId       | `unionId ?? openid`，保证多端 sub 一致                 |
| ✅ Web OAuth 服务端 code exchange | appSecret 不下发前端                                 |

### 架构约束

| 约束                              | 说明                                                                                |
|---------------------------------|-----------------------------------------------------------------------------------|
| ❌ 手写 entity                     | jOOQ 编译期生成 Record 和 POJO                                                          |
| ❌ Record 出 RepositoryImpl 层     | IamMapper 负责 POJO ↔ DTO 转换                                                        |
| ❌ Service 层操作 jOOQ DSL          | 通过 Repository 接口                                                                  |
| ❌ BUILTIN 角色权限通过 API 修改         | 由 PermissionSyncRunner 全量维护                                                       |
| ❌ lock_role_assignment=1 的用户改角色 | Service 层校验，拒绝并返回 IAM_USER_ROLE_ASSIGNMENT_LOCKED                                 |
| ❌ 角色清空（roleIds 传空数组）            | 至少保留一个角色，返回 IAM_USER_ROLE_CANNOT_BE_EMPTY                                         |
| ✅ `@Transactional` 在 Service 层  | 跨表操作加事务                                                                           |
| ✅ C-42 双端对齐                     | main-service: `/auth/**` + `/internal/v1/**`；gateway: `/api/main-service/auth/**` |
| ✅ openApiNullable=false         | 全项目统一，在 microservice-starter-parent 设置                                            |
| ✅ H2 MySQL 兼容模式                 | 本地必须配置 `MODE=MySQL`                                                               |
| ✅ TENANT 数据权限                   | 由业务查询层过滤自身数据，不使用 iam_user_data_scope                                              |

---

## 16. 实现顺序

### 阶段 1：数据层（立即可开始）

```
① Liquibase XML 4 个文件跑通（H2 建表验证）
② mvn generate-sources（jOOQ 从 H2 生成 Record/POJO）
③ JooqConfig（JooqConfigSupport，解决 C-43）
④ 6 个 RepositoryImpl（jOOQ DSL，record.into() 转换）
⑤ IamMapper（POJO ↔ DTO 静态方法）
⑥ 单元测试：RepositoryImpl（H2 内存数据库）
```

### 阶段 2：权限同步

```
⑦ PermissionSyncRunner（扫描 yaml → upsert iam_permission → 维护 BUILTIN 角色权限）
⑧ 集成测试：启动后验证 9 个 permission code 写入，BUILTIN 角色权限关联正确
```

### 阶段 3：密码登录

```
⑨  UserJwtIssuer（jwt.user.secret，区别于 jwt.service.secret）
⑩  RefreshTokenService + IamRedisKeys
⑪  PasswordLoginService
⑫  AuthController：/auth/login/password、/auth/refresh、/auth/logout
⑬  集成测试：登录 → refresh → logout 链路（mock 模式）
```

### 阶段 4：微信登录 + 两步绑定

```
⑭  WechatDecryptor（AES-128-CBC）
⑮  WechatClient 接口 + WechatClientStub
⑯  BindTokenService
⑰  WechatBindRequiredException + GlobalExceptionHandler 扩展
⑱  IdentityDomainService（findWechatIdentity / bindByMobile / bindByCredential）
⑲  WechatLoginService
⑳  AuthController 补充 4 个微信端点
㉑  集成测试：TENANT 首次/再次登录；STAFF 两步绑定；ADMIN Web 两步绑定
```

### 阶段 5：用户/角色/数据权限管理 + 内部接口

```
㉒  InternalUserService + InternalUserController
㉓  UserApplicationService（含数据权限相关方法）
㉔  RoleApplicationService
㉕  IamController（全部 /iam/** 端点）
㉖  IamAuditLogger（用户创建/角色变更/状态修改）
```

### 阶段 6：OpenAPI 化

```
㉗  openapi-generator 配置（microservice-starter-parent pom）
㉘  apiDelegate.mustache（验证 C-40，生成 @PreAuthorize）
㉙  Controller 改为 DelegateImpl，@PreAuthorize 迁移至 Delegate 接口
```
