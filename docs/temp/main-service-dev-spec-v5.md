# Main-Service 开发规格说明书
> 版本：v5.0 | 基于代码快照对齐编写，整合架构决策修订
> 阅读对象：参与 main-service 后续业务模块开发的开发者或 AI

**本版本相对 v4 的核心变化**：
1. 明确「模块间通信方式」原则（见第 0 章），修正 v4 中 Port Interface 与 internal yaml 功能重复的设计矛盾
2. 新增 `customer` 模块设计（企业 + 员工）
3. 确认 `IamTenantPort` 具体实现方案（不新增 biz_ref 字段，沿用手机号去重；`real_name` 字段已存在，仅补充传值逻辑）
4. 修正 `maxOccupancy` 判断——`room_info.LivingNum` 字段已存在，无需建表变更
5. `billing-service` / `device-service` 集成方式确定为 stub 占位，不阻塞其他模块开发
6. RoomStatus 状态机冲突保留为待定 adapter，不在本版本强制对齐

---

## 目录
0. [架构原则：模块间通信方式](#0-架构原则模块间通信方式)
1. [系统定位与整体架构](#1-系统定位与整体架构)
2. [技术栈与开发规范](#2-技术栈与开发规范)
3. [模块总览与依赖关系](#3-模块总览与依赖关系)
4. [iam 模块（已完成 + 待扩展）](#4-iam-模块已完成--待扩展)
5. [propertymgr 模块（已完成，新增 Port 适配层）](#5-propertymgr-模块已完成新增-port-适配层)
6. [customer 模块（新建）](#6-customer-模块新建)
7. [contract 模块（未开发）](#7-contract-模块未开发)
8. [occupancy 模块（未开发）](#8-occupancy-模块未开发)
9. [metering 模块（未开发）](#9-metering-模块未开发)
10. [accounting 模块（未开发）](#10-accounting-模块未开发)
11. [schedule 模块（未开发）](#11-schedule-模块未开发)
12. [外部微服务集成（stub 占位）](#12-外部微服务集成stub-占位)
13. [跨模块业务流程](#13-跨模块业务流程)
14. [待决策清单（持续跟踪）](#14-待决策清单持续跟踪)
15. [Port Interface 汇总](#15-port-interface-汇总)
16. [推荐开发顺序](#16-推荐开发顺序)

---

## 0. 架构原则：模块间通信方式

main-service 是一个**单体部署、模块化设计**的服务，内部按业务领域拆分为多个 module（iam / propertymgr / customer / contract / occupancy / metering / accounting / schedule）。这些 module 之间的通信方式，与 main-service 同其他**真正独立的微服务**（billing-service、device-service）之间的通信方式是**两种不同的机制**，不可混用：

### 0.1 main-service 内部模块间：Java Port Interface（进程内直调）

```
occupancy.CheckInService
    │ 直接 Spring 注入调用，同一事务上下文
    ├─→ ContractQueryPort        (contract 模块实现)
    ├─→ AssetQueryPort/CommandPort  (propertymgr 模块实现)
    ├─→ CustomerQueryPort        (customer 模块实现)
    ├─→ IamTenantPort            (iam 模块实现)
    ├─→ MeteringCommandPort      (metering 模块实现)
    └─→ AccountingCommandPort    (accounting 模块实现)
```

**规则：**
- 每个模块在 `{module}/api/` 包下定义 Port Interface（纯 Java interface + record），这是模块间**唯一**合法依赖点
- 禁止跨模块直接依赖对方的 `service/` `repo/` 包
- 调用方和被调用方在同一 JVM、同一事务，可以用 `@Transactional` 包裹跨模块编排（如 occupancy 的 checkIn 流程）
- **不需要**为这类调用编写 OpenAPI internal yaml；Port Interface 本身就是契约

### 0.2 main-service ↔ 外部微服务：OpenAPI internal yaml + HTTP

```
billing-service ──HTTP──→ POST /internal/v1/accounting/bills/paid     (真实跨进程)
device-service  ──HTTP──→ POST /internal/v1/metering/readings/iot-push (真实跨进程)
billing/device-service ──HTTP──→ POST /internal/v1/users               (iam，已存在)

accounting ──HTTP(stub)──→ billing-service（创建账单/退款，当前 stub 占位）
occupancy/metering ──HTTP(stub)──→ device-service（门锁/设备绑定，当前 stub 占位）
```

**规则：**
- 只有真正跨进程边界（main-service ↔ billing-service / device-service）才使用 OpenAPI internal yaml + Service JWT 鉴权
- 被调用方（如 accounting 收 billing-service 的支付回调）实现 `*ApiDelegate`（Server 端）
- 调用方（如 accounting 调 billing-service 创建账单）目前用 **stub 接口占位**（见第 12 章），后续替换为真实 HTTP Client

### 0.3 ⚠️ 对 v4 文档的修正说明

v4 文档中，`contract-internal.yaml`、`metering-internal.yaml`、`accounting-internal.yaml` 同时定义了 HTTP 接口（如 `createEnterpriseSignBill`、`collectCheckInReadings`）**和**功能完全相同的 Port Interface 方法（如 `AccountingCommandPort.createEnterpriseSignBill()`），这是同一能力被设计了两遍的遗留问题。

**本版本修正**：module 间调用一律走 Port Interface，对应的 internal yaml 接口**删除**。internal yaml 只保留真正给 billing-service / device-service 调用的端点。详见各模块章节。

---

## 1. 系统定位与整体架构

### 1.1 业务定位
**企业住宿租赁管理系统（ToB）**。  
我方管理物业房间，与企业签订合同，企业将房间分配给员工入住。系统全程管理：合同签约 → 房间分配 → 员工入住 → 日常水电计费 → 换宿/退宿 → 合同结算。

### 1.2 微服务拓扑

```
Internet
  └─ Gateway Service（鉴权、灰度、限流）
       ├─ main-service（本文档范围，单体部署、模块化设计）
       │    ├─ iam          已完成，认证授权
       │    ├─ propertymgr  已完成，房源管理
       │    ├─ customer     待开发，企业/员工管理
       │    ├─ contract     待开发，合同生命周期
       │    ├─ occupancy    待开发，分配/入住/退宿/换宿
       │    ├─ metering     待开发，水电计量
       │    ├─ accounting   待开发，账务账单押金
       │    └─ schedule     待开发，定时任务
       ├─ billing-service   独立微服务（支付，当前 stub）
       └─ device-service    独立微服务（IoT 设备，当前 stub）
```

### 1.3 核心业务流程一览

```
企业签约
  ① STAFF 创建合同草稿（含多个房间）
  ② confirmContract → 锁房 + 生成企业签约账单（首月租金 + 企业押金）
  ③ 企业支付账单 → billing-service 回调 accounting → accounting 调 ContractCallbackPort → 合同激活

员工入住
  ④ STAFF 为员工创建 Assignment（分配名额）
  ⑤ 员工自助或 STAFF 代办 checkIn：
       消费 Assignment → 创建 Stay → 采集入住水电底数
       → 创建个人押金账单 → 下发门锁凭证（stub）

日常运营（每日 02:00 自动）
  ⑥ 每日水电日结：读数 → 计费 → 扣减房间账户（企业余额优先，不足由入住员工平摊）

退宿
  ⑦ STAFF/员工发起 checkOut → 采集退宿读数 → 回收门锁（stub）
     → 结算个人押金（退差价）

换宿（跨合同支持）
  ⑧ STAFF 发起 transfer → 旧房间退宿读数 → 新房间入住读数
     → 押金资格迁移（不重缴）→ 余额迁移 → 门锁切换（stub）

退房（合同维度）
  ⑨ 所有员工退宿后，STAFF 发起退房 → 结算房间账户余额 → 退企业押金 → 合同 COMPLETED
```

---

## 2. 技术栈与开发规范

### 2.1 核心依赖
| 组件 | 版本 | 用途 |
|---|---|---|
| Spring Boot | 3.x | 基础框架 |
| jOOQ | 3.19 | 数据访问层（唯一持久化方式） |
| Liquibase | 4.x | 数据库变更管理（唯一 Schema 来源） |
| openapi-generator | 7.17.0 | 从 yaml 生成 API 接口（Delegate 模式，仅用于对外 API 和真实跨进程 internal API） |
| Spring Security | 6.x | 权限控制（@PreAuthorize） |
| Redis | 6.x | Token 缓存、会话管理 |

### 2.2 代码分层（以 iam 为金标准）

```
main/{module}/
├── delegate/         ← *ApiDelegateImpl（仅对外 API；module 间调用不走这层）
├── service/          ← 业务逻辑（@Transactional 在此层，可跨调用其他模块 Port）
├── repo/             ← Repository 接口 + jooq/ 子包（实现）
│   ├── XxxRepository.java
│   ├── model/
│   └── jooq/JooqXxxRepository.java
└── api/              ← Port Interface（供其他模块直接 Spring 注入调用）
    ├── XxxQueryPort.java
    └── XxxCommandPort.java
```

### 2.3 OpenAPI-First 原则（仅适用于对外 API 和真实跨进程 internal API）

每个有**对外 API** 或**真实被外部微服务调用**的模块需要：
1. 在 `src/main/resources/openapi/` 放置 yaml 文件（`{module}-external.yaml` 和/或 `{module}-internal.yaml`）
2. `pom.xml` 中添加对应 `execution`
3. 实现生成的 `*ApiDelegate` 接口

**module 间的相互调用不需要 yaml，直接定义 Port Interface 即可。**

### 2.4 权限声明

所有需鉴权的对外接口在 yaml 中声明 `x-required-permission: {module}:{resource}:{action}`，启动时 `PermissionManifestBootstrap` 自动同步到 `iam_permission` 表。

### 2.5 错误处理

- 业务异常：抛 `BusinessException(HttpStatus, errorCode, message)`
- 跨模块 Port 调用异常：直接向上抛出，由编排方（如 occupancy 的 checkIn）决定是否捕获/补偿
- 错误响应统一格式：`ErrorResponse`（来自 common-components.yaml）

### 2.6 数据库规范

- XML Changelog 放在 `db/changelog/changes/`，主文件通过 `includeAll` 扫描
- 文件命名：`NNN-{desc}.xml`
- jOOQ POJO 通过 codegen 生成，禁止手写 Entity 类

---

## 3. 模块总览与依赖关系

### 3.1 依赖矩阵（全部为 Port Interface，严格单向）

```
schedule ──→ MeteringScheduleTrigger（metering 实现）
         ──→ ContractScheduleTrigger（contract 实现）

occupancy ──→ ContractQueryPort       (contract)
          ──→ AssetQueryPort/CommandPort (propertymgr)
          ──→ CustomerQueryPort       (customer)
          ──→ IamTenantPort           (iam)
          ──→ MeteringCommandPort     (metering)
          ──→ AccountingCommandPort   (accounting)
          ──→ DoorLockPort            (stub，未来对接 device-service)

contract  ──→ AssetCommandPort        (propertymgr)
          ──→ CustomerQueryPort       (customer)
          ──→ AccountingCommandPort   (accounting)
          ──→ AccountingQueryPort     (accounting，部分场景)

metering  ──→ OccupancyQueryPort      (occupancy)
          ──→ AccountingCommandPort   (accounting)
          ──→ AssetQueryPort          (propertymgr，取 storeId)

accounting ──→ ContractCallbackPort   (contract，支付/结算回调通知)
           ──→ BillingServicePort     (stub，未来对接 billing-service)
```

**关键原则：模块间只允许调用对方 `api/` 包下的 Port Interface，不允许调用 service/repo 层。**

### 3.2 模块状态总览

| Module | 状态 | 对外 API | 真实跨进程 internal API |
|---|---|---|---|
| iam | ✅ 已完成 | iam-external.yaml | iam-internal.yaml（被 billing/device 调用） |
| propertymgr | ✅ 已完成（手写 Controller） | 无 OpenAPI（历史遗留） | 无 |
| customer | 🆕 待开发 | customer-external.yaml（待建） | 无 |
| contract | 🆕 待开发 | contract-external.yaml | 无（v4 的 contract-internal.yaml 删除） |
| occupancy | 🆕 待开发 | occupancy-external.yaml | 无 |
| metering | 🆕 待开发 | metering-external.yaml | metering-internal.yaml（仅 iot-push，被 device-service 调用） |
| accounting | 🆕 待开发 | accounting-external.yaml | accounting-internal.yaml（仅 bills/paid，被 billing-service 调用） |
| schedule | 🆕 待开发 | schedule-external.yaml | 无 |

---

## 4. iam 模块（已完成 + 待扩展）

### 4.1 职责
认证（登录、刷新、登出）+ 授权（用户、角色、权限、数据权限）

### 4.2 已实现功能
| 子功能 | 状态 | 关键类 |
|---|---|---|
| 密码登录 / 微信小程序登录 / 微信 Web 登录 | ✅ | `PasswordLoginService`, `AuthApiDelegateImpl` |
| JWT 签发 | ✅ | `UserJwtIssuer`, `AuthVersionService` |
| Refresh Token Rotation | ✅ | `RefreshTokenService` |
| 用户 CRUD（STAFF/CONTRACTOR） | ✅ | `UserMutationService`, `UserLifecycleService` |
| 角色权限管理 | ✅ | `RoleManagementService` |
| 数据权限（AREA/STORE 维度） | ✅ | `IamDataScopeApiDelegateImpl` |
| 启动时权限同步 | ✅ | `PermissionManifestBootstrap` |
| 内部 TENANT 用户预创建（HTTP，给 billing/device 用） | ✅ | `InternalTenantUserService` |

### 4.3 待新增：IamTenantPort（供 occupancy 进程内调用）

**背景澄清**：occupancy 调用 iam 创建 TENANT 用户，本质是"企业员工办理 check-in 后，在 iam 模块里补一条登录账号"，这是 main-service 内部模块调用，**不走 OpenAPI**，直接用 Port Interface。

已有的 `/internal/v1/users`（`InternalTenantUserService.createTenantUser`）服务的是另一个场景——billing-service/device-service 在员工入职阶段**预创建**账号，两者不复用同一实现（语义不同：预创建允许失败报错，occupancy 需要的是"已存在就复用"的幂等语义）。

**确认事项**（已与产品对齐）：
- ❌ 不新增 `biz_ref_id` 字段，继续用 `mobile` 唯一性去重
- ✅ `iam_user.real_name` 字段本来就存在（见 `001-create-iam-tables.xml`），`InternalTenantUserService` 只是当前没传值，occupancy 的新方法需要正确传入
- ✅ `UserLifecycleService.softDeleteUser(userId, operatorUserId, reason)` 的 `operatorUserId` 本来就支持传 `null`（代表系统操作），occupancy 调用退宿注销时直接传 `null` 即可，无需查找 SYSTEM 用户

**需要的代码改动：**

```java
// 1. UserRepository 新增方法（轻量新增，无需改表）
public interface UserRepository {
    // ...已有方法...

    /** 按手机号查询未删除用户，用于幂等创建判断 */
    Optional<IamUser> findActiveByMobile(String mobile);
}

// 2. 新增 Port Interface
package com.jugu.propertylease.main.iam.api;

public interface IamTenantPort {
    /**
     * 入住时调用：若该手机号已有 TENANT 用户则直接复用并返回其 ID；
     * 不存在则创建新用户（写入 realName）。
     * 幂等，不会因为手机号重复而抛异常。
     */
    Long createOrEnableTenantUser(String mobile, String realName);

    /**
     * 退宿后调用：软删除 IAM 用户，注销登录能力。
     * 内部直接调用 UserLifecycleService.softDeleteUser(iamUserId, null, "退宿自动注销")
     */
    void disableTenantUser(Long iamUserId);
}

// 3. 实现类（新建 Service，不复用 InternalTenantUserService）
package com.jugu.propertylease.main.iam.service;

@Service
public class IamTenantPortImpl implements IamTenantPort {

    private final UserRepository userRepo;
    private final UserLifecycleService userLifecycleService;

    @Transactional
    public Long createOrEnableTenantUser(String mobile, String realName) {
        return userRepo.findActiveByMobile(mobile)
            .map(IamUser::getId)
            .orElseGet(() -> {
                String userName = "tenant_" + mobile;
                return userRepo.insert(UserType.TENANT, userName, realName,
                    mobile, /* email */ null, OffsetDateTime.now());
            });
    }

    @Override
    public void disableTenantUser(Long iamUserId) {
        userLifecycleService.softDeleteUser(iamUserId, null, "退宿自动注销");
    }
}
```

### 4.4 用户类型说明
| UserType | 说明 | 登录方式 |
|---|---|---|
| STAFF | 我方内部员工 | 密码 + 微信 |
| CONTRACTOR | 外部承包商 | 密码 |
| TENANT | 企业员工（住宿人） | 微信小程序 |
| SYSTEM | 内部系统账号 | 不可登录 |

---

## 5. propertymgr 模块（已完成，新增 Port 适配层）

### 5.1 职责
管理区域、门店、楼栋、户型、房间。对应设计文档中的"Asset"概念，但保持现有手写 Controller 模式不变。

### 5.2 现状与决策

- **API 路径**：`/main/room/...` 等旧路径，**不重构**，新模块不依赖这些 HTTP 接口
- **房间状态枚举**：现有 `RoomStatus`（WAIT_PUBLISH/EMPTY/WAIT_CHECK_IN/...）与新设计期望的状态机不一致
  - **决策（已确认）**：暂不改动，在 `AssetQueryPort` 实现层放一个 **provisional adapter**，将旧状态映射/简化为新模块需要的语义（如"是否可分配""是否已占满"），具体映射规则留待后续与产品对齐后再定，当前先用保守逻辑（如 `EMPTY` → 可分配，其余 → 不可分配）
- **容量字段**：✅ 已存在，`room_info.LivingNum`（居住人数），校验逻辑 `> 0`，**无需新增字段**，`AssetQueryPort.getMaxOccupancy()` 直接读取此列

### 5.3 新增 Port Interface

```java
package com.jugu.propertylease.main.propertymgr.api;

public interface AssetQueryPort {
    RoomInfo getRoomInfo(Long roomId);
    List<RoomInfo> getRoomsByIds(List<Long> roomIds);
    /** 直接读取 room_info.LivingNum */
    int getMaxOccupancy(Long roomId);
    Long getStoreIdByRoomId(Long roomId);
    /**
     * ⚠️ provisional adapter：当前简化逻辑，待 RoomStatus 状态机对齐后修订。
     * 当前实现：RoomStatus == EMPTY 时返回 true。
     */
    boolean isRoomAvailableForContract(Long roomId);
}

public interface AssetCommandPort {
    /**
     * ⚠️ provisional adapter：当前简化为更新 RoomStatus 为 WAIT_CHECK_IN（占用占位）。
     * 待状态机对齐后修订为正式语义。
     */
    void lockRoom(Long roomId, Long contractId);
    /**
     * ⚠️ provisional adapter：当前简化为更新 RoomStatus 为 EMPTY。
     */
    void releaseRoom(Long roomId, Long contractId);
}

record RoomInfo(Long id, Long storeId, String roomNo, int maxOccupancy, String currentStatus) {}
```

### 5.4 数据库表概览（不变）

| 表名 | 主键 | 关键字段 |
|---|---|---|
| `region_info` | regionId | name |
| `store_info` | storeId | regionId, name |
| `building_info` | buildingId | storeId, floors |
| `floor_plan_info` | planId | buildingId, roomType, livingNum |
| `room_info` | RoomId | BuildingId, Level, RoomNum, RoomStatus, **LivingNum**, EID/WID/HWID |

> `room_info.EID/WID/HWID`（直接挂设备 ID）是老设计，新功能（metering 模块）使用版本化的 `meter_device_binding` 表，两者并存，互不冲突。

---

## 6. customer 模块（新建）

### 6.1 职责
维护企业信息和企业员工信息，是 contract（企业维度）和 occupancy（员工维度）共同依赖的基础数据模块。

### 6.2 数据库表

**enterprise（企业）**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| name | VARCHAR(200) | 企业名称 |
| status | VARCHAR(20) | ACTIVE / INACTIVE |
| contact_name | VARCHAR(100) | 联系人 |
| contact_mobile | VARCHAR(20) | 联系电话 |
| remark | VARCHAR(500) | |
| created_by / created_at / updated_at | | |

**employee（员工，即业务语义上的"tenant"）**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| enterprise_id | BIGINT | |
| name | VARCHAR(100) | 姓名 |
| mobile | VARCHAR(20) | 手机号（不强制全局唯一，同一人可能换企业） |
| status | VARCHAR(20) | ACTIVE / INACTIVE |
| created_by / created_at / updated_at | | |

> **命名约定**：occupancy/contract/accounting 等模块中出现的 `tenant_id`，其值即为 `customer.employee.id`。跨模块统一沿用 `tenant_id` 这个名字（而非改成 `employee_id`），因为它在"入住关系"语境下表达的是"租客"角色，与 customer 模块内部"企业员工"的命名视角不同但指向同一实体。

### 6.3 对外 API（待建 customer-external.yaml）

| Method | Path | operationId | 权限 | 说明 |
|---|---|---|---|---|
| POST | /customer/enterprises | createEnterprise | customer:enterprise:write | 创建企业 |
| POST | /customer/enterprises/query | queryEnterprises | customer:enterprise:read | 分页查询 |
| GET | /customer/enterprises/{id} | getEnterprise | customer:enterprise:read | 详情 |
| PUT | /customer/enterprises/{id} | updateEnterprise | customer:enterprise:write | 更新 |
| POST | /customer/employees | createEmployee | customer:employee:write | 创建员工 |
| POST | /customer/employees/query | queryEmployees | customer:employee:read | 分页查询 |
| GET | /customer/employees/{id} | getEmployee | customer:employee:read | 详情 |
| PUT | /customer/employees/{id} | updateEmployee | customer:employee:write | 更新 |

### 6.4 暴露的 Port Interface

```java
package com.jugu.propertylease.main.customer.api;

public interface CustomerQueryPort {
    EnterpriseInfo getEnterprise(Long enterpriseId);
    EmployeeInfo getEmployee(Long employeeId);
    /** occupancy 分配前校验：该员工是否属于该企业 */
    boolean isEmployeeOfEnterprise(Long employeeId, Long enterpriseId);
}

record EnterpriseInfo(Long id, String name, String status) {}
record EmployeeInfo(Long id, Long enterpriseId, String name, String mobile, String status) {}
```

---

## 7. contract 模块（未开发）

### 7.1 职责
合同是整个系统的业务锚点：创建、确认（锁房）、取消、驱动企业签约账单、部分/全部退房、接收账务回调推进状态。

### 7.2 合同状态机

```
DRAFT
  │ confirmContract() → 锁房 + 生成企业签约账单
  ↓
SIGN_BILL_PENDING
  │ 企业支付签约账单 → accounting 内部调用 ContractCallbackPort.onSignBillPaid()
  ↓
READY_FOR_CHECK_IN          ← 可以开始分配员工、办理入住
  │ applyPartialReturnRooms()
  ↓
PARTIALLY_RETURNED          ← 部分房间退回，剩余继续运营；仍可入住未退房间
  │ 所有房间退完 OR applyFullReturnRooms()
  ↓
FULLY_RETURNED
  │ accounting 启动结算
  ↓
SETTLING
  │ 账单结清 → accounting 内部调用 ContractCallbackPort.onSettlementCompleted()
  ↓
COMPLETED

DRAFT / SIGN_BILL_PENDING → CANCELLED（取消条件：账单未支付 且 无 CHECKED_IN Stay）
```

### 7.3 数据库表

**contract**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| contract_no | VARCHAR(32) UNIQUE | CTR-{yyyyMM}-{seq} |
| enterprise_id | BIGINT | 引用 customer.enterprise.id |
| contract_status | VARCHAR(30) | 见状态机 |
| start_date / end_date | DATE | |
| payment_mode | VARCHAR(20) | MONTHLY / QUARTERLY |
| sign_bill_id | BIGINT | accounting.bill.id |
| remark | VARCHAR(500) | |
| created_by / created_at / updated_at | | |

**contract_room**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_id | BIGINT | |
| room_id | BIGINT | room_info.RoomId |
| signed_rent | DECIMAL(12,2) | |
| lease_start_date / lease_end_date | DATE | |
| status | VARCHAR(20) | ACTIVE / RETURNED |
| returned_at | DATETIME(3) | |

**contract_charge_rule（费用规则快照）**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_id | BIGINT | |
| charge_type | VARCHAR(30) | ENTERPRISE_DEPOSIT / PERSONAL_DEPOSIT / FIRST_RENT / MONTHLY_RENT |
| payer_type | VARCHAR(20) | ENTERPRISE / TENANT |
| amount | DECIMAL(12,2) | |
| rule_snapshot | JSON | |

### 7.4 对外 API（contract-external.yaml）

| Method | Path | operationId | 权限 |
|---|---|---|---|
| POST | /contract/contracts | createContractDraft | contract:contract:write |
| POST | /contract/contracts/query | queryContracts | contract:contract:read |
| GET | /contract/contracts/{id} | getContract | contract:contract:read |
| PUT | /contract/contracts/{id}/confirm | confirmContract | contract:contract:write |
| PUT | /contract/contracts/{id}/cancel | cancelContract | contract:contract:write |
| PUT | /contract/contracts/{id}/partial-return | applyPartialReturnRooms | contract:room:return |
| PUT | /contract/contracts/{id}/full-return | applyFullReturnRooms | contract:room:return |
| POST | /contract/contracts/{id}/rooms | addRoomsToContract | contract:room:write |

### 7.5 ⚠️ v4 变更：不再需要 contract-internal.yaml

v4 中 `contract-internal.yaml` 定义的 `sign-bill-paid`、`settlement-completed` 两个回调端点**删除**。accounting 完成支付/结算处理后，直接调用 `ContractCallbackPort` 的 Java 方法（同进程内 Spring 注入），无需 HTTP。

### 7.6 关键业务逻辑

**confirmContract 动作序列：**
```
1. 校验 status == DRAFT
2. 校验所有 rooms 无活跃合同（contract_room.status=ACTIVE）
3. 调用 AssetCommandPort.lockRoom(roomId) 逐一锁房
4. 快照 ContractChargeRule
5. 调用 AccountingCommandPort.createEnterpriseSignBill(contractId, enterpriseId, totalAmount, depositAmount)
6. 更新 contract.status = SIGN_BILL_PENDING，存入 sign_bill_id

整个过程在一个 @Transactional 内完成（同进程 Port 调用，事务可跨模块）。
```

**applyPartialReturnRooms 动作序列：**
```
1. 校验目标房间均无 CHECKED_IN Stay（OccupancyQueryPort.hasCheckedInStays）
2. 调用 AccountingCommandPort.settlePartialReturn(contractId, returnedRoomIds)
3. 更新 contract_room.status = RETURNED
4. 若所有房间均 RETURNED → contract.status = FULLY_RETURNED，否则 PARTIALLY_RETURNED
5. 调用 AssetCommandPort.releaseRoom(roomId)
```

### 7.7 暴露的 Port Interface

```java
package com.jugu.propertylease.main.contract.api;

public interface ContractQueryPort {
    ContractInfo getContract(Long contractId);
    boolean isContractAllowingAssignment(Long contractId);  // SIGN_BILL_PENDING or READY_FOR_CHECK_IN
    boolean isContractReadyForCheckIn(Long contractId);
    List<ContractRoomInfo> getActiveRoomsByContract(Long contractId);
    List<ContractInfo> findContractsDueBy(LocalDate date);  // schedule 用
}

public interface ContractCallbackPort {
    void onSignBillPaid(Long contractId, Long billId);
    void onSettlementCompleted(Long contractId);
}

public interface ContractScheduleTrigger {
    void checkAndGenerateRentBills(LocalDate date);
    void checkContractExpiry(LocalDate date);
}
```

---

## 8. occupancy 模块（未开发）

### 8.1 职责
管理"居住关系"的全生命周期：分配（Assignment）→ 入住（Stay）→ 退宿 / 换宿。是跨模块编排的核心。

### 8.2 核心概念

**Assignment**：STAFF 为某员工在某合同房间预留入住名额。容量规则：`ASSIGNED数 + CHECKED_IN数 < Room.maxOccupancy`（maxOccupancy 来自 `AssetQueryPort.getMaxOccupancy`，即 `room_info.LivingNum`）。状态：`ASSIGNED → CONSUMED` / `CANCELLED`

**Stay**：实际居住关系，水电计费、押金台账的归属锚点。状态：`CHECKED_IN → TRANSFERRED` / `CHECKED_OUT`

### 8.3 数据库表

**room_assignment**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_id / contract_room_id / room_id | BIGINT | room_id 冗余加速容量查询 |
| tenant_id | BIGINT | 引用 customer.employee.id |
| status | VARCHAR(20) | ASSIGNED / CONSUMED / CANCELLED |
| assigned_at | DATETIME(3) | |
| assigned_by | BIGINT | STAFF user_id |

**stay**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| source_assignment_id | BIGINT UNIQUE | |
| contract_id / contract_room_id / room_id | BIGINT | |
| tenant_id | BIGINT | |
| stay_status | VARCHAR(20) | CHECKED_IN / TRANSFERRED / CHECKED_OUT |
| check_in_at / check_out_at | DATETIME(3) | |
| checked_in_by | BIGINT | |

**transfer_record**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| tenant_id | BIGINT | |
| from_stay_id / from_room_id | BIGINT | |
| to_stay_id / to_room_id / to_contract_id | BIGINT | |
| transfer_at | DATETIME(3) | |
| transferred_by | BIGINT | |

### 8.4 对外 API（occupancy-external.yaml）

| Method | Path | operationId | 权限 |
|---|---|---|---|
| POST | /occupancy/assignments | assignTenantToRoom | occupancy:assignment:write |
| POST | /occupancy/assignments/query | queryAssignments | occupancy:assignment:read |
| PUT | /occupancy/assignments/{id}/cancel | cancelAssignment | occupancy:assignment:write |
| POST | /occupancy/stays/check-in | checkIn | occupancy:stay:checkin |
| POST | /occupancy/stays/query | queryStays | occupancy:stay:read |
| GET | /occupancy/stays/{id} | getStay | occupancy:stay:read |
| PUT | /occupancy/stays/{id}/check-out | checkOut | occupancy:stay:checkout |
| POST | /occupancy/stays/{id}/transfer | transferTenant | occupancy:stay:transfer |

### 8.5 checkIn 编排逻辑（核心，单一 @Transactional）

```
前置校验：
  - assignment.status = ASSIGNED
  - ContractQueryPort.isContractReadyForCheckIn(contractId)
  - CustomerQueryPort.isEmployeeOfEnterprise(tenantId, enterpriseId)
  - CHECKED_IN 数 < AssetQueryPort.getMaxOccupancy(roomId)

编排步骤（同一事务内）：
  1. assignment.status → CONSUMED
  2. 创建 Stay(CHECKED_IN)
  3. MeteringCommandPort.collectCheckInReadings(stayId, roomId)
  4. AccountingCommandPort.createPersonalDepositBill(tenantId, stayId, contractId, roomId)
  5. IamTenantPort.createOrEnableTenantUser(mobile, realName)
  6. DoorLockPort.issueCredential(tenantId, roomId, stayId)  ← stub，当前 no-op + 日志
```

### 8.6 checkOut 编排逻辑

```
1. stay.status → CHECKED_OUT
2. MeteringCommandPort.collectCheckOutReadings(stayId, roomId) → 返回用量摘要
3. DoorLockPort.revokeCredential(tenantId, roomId, stayId)  ← stub
4. AccountingCommandPort.settlePersonalDepositOnCheckout(tenantId, stayId)
5. IamTenantPort.disableTenantUser(iamUserId)
```

### 8.7 transfer（换宿）编排逻辑

```
1. MeteringCommandPort.collectCheckOutReadings(fromStay)
2. fromStay → TRANSFERRED
3. 创建目标 Assignment（ASSIGNED → 立即 CONSUMED）
4. 创建新 Stay(CHECKED_IN)
5. MeteringCommandPort.collectCheckInReadings(toStay)
6. AccountingCommandPort.transferPersonalDepositEligibility(tenantId, fromStayId, toStayId)
7. AccountingCommandPort.transferTenantSubBalance(tenantId, fromRoomId, toRoomId)
8. DoorLockPort: revokeCredential(fromRoom) + issueCredential(toRoom)  ← stub
```

### 8.8 暴露的 Port Interface

```java
package com.jugu.propertylease.main.occupancy.api;

public interface OccupancyQueryPort {
    List<StayInfo> getCheckedInStaysByRoom(Long roomId);
    List<Long> findRoomIdsWithCheckedInStays(LocalDate date);
    boolean hasCheckedInStays(Long roomId);
    StayInfo getStay(Long stayId);
    int countAssignedByRoom(Long roomId);
    int countCheckedInByRoom(Long roomId);
}
```

---

## 9. metering 模块（未开发）

### 9.1 职责
水电表读数采集、锚点管理、每日用量计算、触发 accounting 扣减。

### 9.2 数据库表

**meter_device_binding（版本化）**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| device_id | BIGINT | device-service 设备 ID（stub 阶段先存占位值） |
| meter_type | VARCHAR(20) | WATER / ELECTRICITY / HOT_WATER |
| binding_start_at / binding_end_at | DATETIME(3) | NULL=当前有效 |
| initial_reading | DECIMAL(12,3) | |
| is_active | TINYINT(1) | |

**meter_reading**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id / device_binding_id | BIGINT | |
| meter_type | VARCHAR(20) | |
| reading_value | DECIMAL(12,3) | 表盘累计读数 |
| reading_time | DATETIME(3) | |
| source | VARCHAR(20) | IOT / MANUAL |
| anchor_type | VARCHAR(20) | CHECK_IN / CHECK_OUT / PERIODIC / NULL |
| stay_id | BIGINT | |
| reading_group_id | VARCHAR(64) | |
| device_id_raw | VARCHAR(100) | IoT 幂等键 |

**meter_price_config**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| store_id | BIGINT | NULL=全局默认 |
| meter_type | VARCHAR(20) | |
| unit_price | DECIMAL(10,4) | |
| effective_from / effective_to | DATE | |

**settlement_anchor**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id / stay_id | BIGINT | |
| anchor_type | VARCHAR(20) | CHECK_IN / DAILY_CLOSING / CHECK_OUT |
| reading_group_id | VARCHAR(64) | |
| anchor_time | DATETIME(3) | |

**room_daily_charge**（UNIQUE(room_id, settlement_date)，幂等）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| settlement_date | DATE | |
| water_amount / electric_amount / hot_water_amount / total_amount | DECIMAL(12,2) | |
| status | VARCHAR(20) | PENDING / SETTLED / PARTIAL / FAILED |

**tenant_apportionment**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_daily_charge_id / tenant_id / stay_id | BIGINT | |
| settlement_date | DATE | |
| apportioned_amount | DECIMAL(12,2) | |

### 9.3 对外 API（metering-external.yaml）

| Method | Path | operationId | 权限 |
|---|---|---|---|
| GET | /metering/rooms/{roomId}/bindings | listRoomDeviceBindings | metering:binding:read |
| POST | /metering/rooms/{roomId}/bindings | bindDevice | metering:binding:write |
| PUT | /metering/rooms/{roomId}/bindings/{bindingId}/unbind | unbindDevice | metering:binding:write |
| POST | /metering/readings | submitMeterReading | metering:reading:write |
| POST | /metering/readings/query | queryMeterReadings | metering:reading:read |
| GET | /metering/prices | listMeterPrices | metering:price:read |
| POST | /metering/prices | createMeterPrice | metering:price:write |
| POST | /metering/daily-charges/query | queryRoomDailyCharges | metering:daily-charge:read |
| GET | /metering/daily-charges/{id}/apportionments | getDailyChargeApportionments | metering:daily-charge:read |

> ⚠️ 注意：`listRoomDeviceBindings`、`listMeterPrices`、`getDailyChargeApportionments` 三个接口的响应必须使用具名 wrapper schema（如 `MeterDeviceBindingListResult`），不能用裸 `type: array`，原因见 §9.6。

### 9.4 真实跨进程 internal API（metering-internal.yaml，仅此一个端点）

| Path | operationId | 调用方 |
|---|---|---|
| POST /internal/v1/metering/readings/iot-push | pushIotReading | device-service（stub 阶段暂无真实调用方，先实现 Server 端） |

### 9.5 ⚠️ v4 变更：锚点采集接口改为 Port Interface

v4 中 `metering-internal.yaml` 的 `anchors/check-in`、`anchors/check-out` 两个端点**删除**，occupancy 直接调用 `MeteringCommandPort` 的 Java 方法。

### 9.6 关于 apiDelegate.mustache 模板

之前对话中发现并修复过一个问题：`type: array` 直接作为响应体时，自定义的 `apiDelegate.mustache` 模板生成的 Delegate 返回类型与标准 `api.mustache` 生成的 Controller 返回类型不一致，导致编译期类型不兼容错误。修复方式是让 `apiDelegate.mustache` 直接使用 `{{{returnType}}}`，与 `api.mustache` 保持完全一致。

**请在开始 metering 模块开发前，确认你本地工作区的 `src/main/resources/openapi-templates/JavaSpring/apiDelegate.mustache` 已应用此修复**（这次上传的 zip 快照里仍是修复前的版本，可能只是打包时遗漏，未必代表你本地实际状态）。确认方式：检查模板里返回类型那一行是否还有 `{{#isArray}}java.util.List<{{{returnBaseType}}}>{{/isArray}}` 这种手动拼接逻辑，如果有就是旧版，需要替换。

### 9.7 日结执行逻辑（由 schedule 触发，02:00 每日）

```
runDailySettlement(date = yesterday):

1. OccupancyQueryPort.findRoomIdsWithCheckedInStays(date)
2. FOR EACH room:
   a. 取 DAILY_CLOSING 锚点读数差值计算 usage（按表类型）
   b. 查询有效单价（AssetQueryPort.getStoreIdByRoomId 取 storeId，再查 meter_price_config）
   c. total_amount = Σ(usage × unit_price)
   d. enterpriseCoverAmount = min(企业子余额, total_amount)（通过 AccountingQueryPort 查询）
   e. 剩余金额按 CHECKED_IN Stay 均摊
   f. 调用 AccountingCommandPort.deductForDailySettlement(request)
   g. 写 room_daily_charge（幂等）
3. 写 schedule_task_log
```

### 9.8 暴露的 Port Interface

```java
package com.jugu.propertylease.main.metering.api;

public interface MeteringCommandPort {
    String collectCheckInReadings(CollectReadingsCommand cmd);   // 返回 readingGroupId
    UsageSummary collectCheckOutReadings(CollectReadingsCommand cmd);
    void submitPeriodicReading(RecordReadingCommand cmd);
}

public interface MeteringScheduleTrigger {
    DailySettlementResult runDailySettlement(LocalDate date);
}
```

---

## 10. accounting 模块（未开发）

### 10.1 职责
账务中心：房间水电账户（企业/租客子余额）、账单台账、押金台账、日结扣减、结算、充值、退款。

### 10.2 账单类型

| bill_type | 触发时机 | 付款方 |
|---|---|---|
| ENTERPRISE_SIGN_BILL | confirmContract | 企业 |
| PERSONAL_DEPOSIT_BILL | checkIn | 租客个人 |
| RECHARGE_BILL | 主动充值 | 充值方 |
| SETTLEMENT_BILL | 退房/退宿有欠费 | 欠费方 |
| REFUND_BILL | 退房/退宿有余额 | 我方退还 |

### 10.3 数据库表

**room_account**（1:1 对应房间）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT UNIQUE | |
| current_contract_id | BIGINT | |
| status | VARCHAR(20) | ACTIVE / FROZEN / CLOSED |

**room_account_sub_balance**（UNIQUE(room_account_id, owner_type, owner_id)）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_account_id | BIGINT | |
| owner_type | VARCHAR(20) | ENTERPRISE / TENANT |
| owner_id | BIGINT | enterprise_id 或 tenant_id |
| available_balance / frozen_balance | DECIMAL(12,2) | |

> 扣减优先级：企业子余额 → 不足部分租客子余额均摊

**room_account_entry**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_account_id / sub_balance_id | BIGINT | |
| owner_type / owner_id | | |
| entry_type | VARCHAR(30) | RECHARGE/DAILY_DEDUCT/TRANSFER_OUT/TRANSFER_IN/REFUND/FREEZE/UNFREEZE/ADJUST/INSUFFICIENT |
| amount | DECIMAL(12,2) | 正数入账，负数出账 |
| related_bill_id | BIGINT | |
| occurred_at | DATETIME(3) | |

**bill**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| bill_no | VARCHAR(32) UNIQUE | |
| bill_type | VARCHAR(30) | 见账单类型表 |
| bill_owner_type / bill_owner_id | | |
| contract_id / room_id / tenant_id | BIGINT | |
| total_amount | DECIMAL(12,2) | |
| bill_status | VARCHAR(20) | PENDING / PAID / CANCELLED / REFUNDED |
| billing_service_bill_id | BIGINT | billing-service 侧 ID（stub 阶段为 NULL） |
| paid_at | DATETIME(3) | |

**deposit_ledger**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| deposit_type | VARCHAR(20) | ENTERPRISE / PERSONAL |
| owner_type / owner_id | | |
| contract_id | BIGINT | |
| current_stay_id | BIGINT | 个人押金换宿时更新 |
| original_amount / occupied_amount / refundable_amount | DECIMAL | |
| status | VARCHAR(20) | PENDING_PAYMENT / ACTIVE / REFUND_PENDING / CLOSED |
| related_bill_id | BIGINT | |

**system_config**
| config_key | config_value | 说明 |
|---|---|---|
| personal_deposit_amount | 500.00 | 个人押金金额（元） |

### 10.4 对外 API（accounting-external.yaml）

| Method | Path | operationId | 权限 |
|---|---|---|---|
| POST | /accounting/room-accounts/query | queryRoomAccounts | accounting:room-account:read |
| GET | /accounting/room-accounts/{id} | getRoomAccount | accounting:room-account:read |
| GET | /accounting/room-accounts/by-room/{roomId} | getRoomAccountByRoom | accounting:room-account:read |
| POST | /accounting/room-accounts/{id}/entries/query | queryRoomAccountEntries | accounting:entry:read |
| POST | /accounting/room-accounts/{id}/recharge | initiateRecharge | accounting:recharge:write |
| POST | /accounting/bills/query | queryBills | accounting:bill:read |
| GET | /accounting/bills/{id} | getBill | accounting:bill:read |
| PUT | /accounting/bills/{id}/cancel | cancelBill | accounting:bill:write |
| POST | /accounting/deposit-ledgers/query | queryDepositLedgers | accounting:deposit:read |
| GET | /accounting/deposit-ledgers/{id} | getDepositLedger | accounting:deposit:read |

### 10.5 真实跨进程 internal API（accounting-internal.yaml，仅此一个端点）

| Path | operationId | 调用方 |
|---|---|---|
| POST /internal/v1/accounting/bills/paid | billPaidCallback | billing-service（stub 阶段暂无真实调用方，先实现 Server 端） |

### 10.6 ⚠️ v4 变更：以下 8 个接口改为 Port Interface

v4 中 `accounting-internal.yaml` 的以下端点**全部删除**，改为 `AccountingCommandPort` 方法，由 contract/occupancy/metering 直接调用：
`enterprise-sign-bill`、`personal-deposit-bill`、`personal-deposit/transfer-eligibility`、`personal-deposit/settle-checkout`、`room-accounts/transfer-sub-balance`、`settlement/partial-return`、`settlement/full-return`、`daily-deduction`

### 10.7 billPaidCallback 路由规则

```
billing-service 回调后（真实跨进程，目前 stub），根据 bill.bill_type 路由：

ENTERPRISE_SIGN_BILL：
  → DepositLedger(ENTERPRISE) → ACTIVE
  → 调用 ContractCallbackPort.onSignBillPaid(contractId, billId)   ← Port Interface 调用

PERSONAL_DEPOSIT_BILL：
  → DepositLedger(PERSONAL) → ACTIVE

RECHARGE_BILL：
  → 子余额 available_balance += actualAmount，写 entry(RECHARGE)

SETTLEMENT_BILL：
  → 标记欠费处理完成，若结算完成则回调 ContractCallbackPort.onSettlementCompleted()

幂等键：billing_service_bill_id
```

### 10.8 暴露的 Port Interface

```java
package com.jugu.propertylease.main.accounting.api;

public interface AccountingCommandPort {
    CreateBillResult createEnterpriseSignBill(EnterpriseSignBillCommand cmd);
    CreateBillResult createPersonalDepositBill(PersonalDepositBillCommand cmd);
    void transferPersonalDepositEligibility(Long tenantId, Long fromStayId, Long toStayId);
    TransferSubBalanceResult transferTenantSubBalance(Long tenantId, Long fromRoomId, Long toRoomId);
    DepositSettlementResult settlePersonalDepositOnCheckout(Long tenantId, Long stayId);
    void settlePartialReturn(PartialReturnCommand cmd);
    void settleFullReturn(Long contractId);
    DailyDeductionResult deductForDailySettlement(DailyDeductionCommand cmd);
}

public interface AccountingQueryPort {
    BigDecimal getEnterpriseSubBalance(Long roomId, Long enterpriseId);
    DepositLedgerInfo getPersonalDepositLedger(Long tenantId, Long stayId);
}
```

---

## 11. schedule 模块（未开发）

### 11.1 职责
纯触发器，统一管理定时任务，提供防重和补跑能力。

### 11.2 定时任务清单

| taskType | Cron | 调用目标（Port Interface） | 说明 |
|---|---|---|---|
| DAILY_METER_SETTLEMENT | `0 0 2 * * ?` | MeteringScheduleTrigger.runDailySettlement(yesterday) | 每日 02:00 水电日结 |
| CONTRACT_EXPIRY_CHECK | `0 0 1 * * ?` | ContractScheduleTrigger.checkContractExpiry(today) | 每日 01:00 到期提醒 |

### 11.3 数据库表

**schedule_task_log**（UNIQUE(task_type, scheduled_date)）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| task_type | VARCHAR(50) | |
| scheduled_date | DATE | |
| status | VARCHAR(20) | RUNNING / SUCCESS / PARTIAL_FAILURE / FAILED |
| triggered_by | VARCHAR(20) | CRON / MANUAL |
| total_count / success_count / failure_count | INT | |
| error_summary | VARCHAR(2000) | |
| started_at / finished_at | DATETIME(3) | |

### 11.4 对外 API（schedule-external.yaml）

| Method | Path | operationId | 权限 |
|---|---|---|---|
| POST | /schedule/tasks/{taskType}/trigger | triggerTask | schedule:task:trigger |
| POST | /schedule/tasks/query | queryTaskLogs | schedule:task:read |

---

## 12. 外部微服务集成（stub 占位）

### 12.1 决策

billing-service 和 device-service 当前均**没有可用的 OpenAPI 契约**（既没有对方团队提供的规范，也没有现成代码）。决策为：**先用 stub 接口占位，不阻塞 accounting/occupancy/metering 模块的开发**，待对方契约就绪后再替换为真实 HTTP Client 实现。

### 12.2 BillingServicePort（accounting 依赖）

```java
package com.jugu.propertylease.main.accounting.outer;

/**
 * billing-service 客户端契约。当前为 stub 实现，方法体直接返回模拟数据 / no-op，
 * 不发起真实网络调用。待 billing-service 提供 OpenAPI 规范后替换为真实 HTTP Client。
 */
public interface BillingServicePort {
    /** 创建账单（企业签约账单 / 个人押金账单 / 充值账单 / 追缴账单），返回支付跳转信息 */
    BillingCreateResult createBill(BillingCreateCommand cmd);
    /** 发起退款 */
    void createRefund(BillingRefundCommand cmd);
}

record BillingCreateCommand(String billNo, BigDecimal amount, String billType, String paymentMethod) {}
record BillingCreateResult(Long billingServiceBillId, String paymentUrl) {}
record BillingRefundCommand(String billNo, BigDecimal amount, String reason) {}

// stub 实现
@Service
@Profile("!prod")  // 或用配置开关控制
public class StubBillingServicePort implements BillingServicePort {
    private static final Logger log = LoggerFactory.getLogger(StubBillingServicePort.class);

    @Override
    public BillingCreateResult createBill(BillingCreateCommand cmd) {
        log.warn("[STUB] BillingServicePort.createBill called, no real billing-service integration yet: {}", cmd);
        return new BillingCreateResult(null, "https://stub.billing.local/pay/" + cmd.billNo());
    }

    @Override
    public void createRefund(BillingRefundCommand cmd) {
        log.warn("[STUB] BillingServicePort.createRefund called: {}", cmd);
    }
}
```

### 12.3 DoorLockPort（occupancy 依赖）

```java
package com.jugu.propertylease.main.occupancy.outer;

public interface DoorLockPort {
    void issueCredential(Long tenantId, Long roomId, Long stayId);
    void revokeCredential(Long tenantId, Long roomId, Long stayId);
}

@Service
@Profile("!prod")
public class StubDoorLockPort implements DoorLockPort {
    private static final Logger log = LoggerFactory.getLogger(StubDoorLockPort.class);

    @Override
    public void issueCredential(Long tenantId, Long roomId, Long stayId) {
        log.warn("[STUB] DoorLockPort.issueCredential: tenantId={} roomId={} stayId={}", tenantId, roomId, stayId);
    }

    @Override
    public void revokeCredential(Long tenantId, Long roomId, Long stayId) {
        log.warn("[STUB] DoorLockPort.revokeCredential: tenantId={} roomId={} stayId={}", tenantId, roomId, stayId);
    }
}
```

### 12.4 device-service 设备绑定（metering 依赖）

metering 模块的 `bindDevice` 接口当前接受调用方传入的 `deviceId`（即假设 device-service 侧设备已存在，只是在 main-service 记录绑定关系），不主动校验设备真实性。待 device-service 契约就绪后，可在 `bindDevice` 流程中加入"校验 deviceId 是否存在"的调用。

---

## 13. 跨模块业务流程

> 以下时序图中，凡是 main-service 内部模块间的箭头均为**同进程 Java 方法调用**（Port Interface），不是网络请求；只有标注「HTTP」的箭头才是真实跨进程调用。

### 13.1 合同签约到激活

```
STAFF → POST /contract/contracts                     创建草稿（DRAFT）
STAFF → PUT  /contract/contracts/{id}/confirm
  contract → AssetCommandPort.lockRoom()                          [Port]
  contract → AccountingCommandPort.createEnterpriseSignBill()     [Port]
    accounting → BillingServicePort.createBill()                  [Port，stub]
                                                     → contract.status = SIGN_BILL_PENDING

企业支付（billing-service 处理，当前 stub 无真实触发）
  billing-service → POST /internal/v1/accounting/bills/paid       [HTTP，真实跨进程]
  accounting: DepositLedger(ENTERPRISE) → ACTIVE
  accounting → ContractCallbackPort.onSignBillPaid()               [Port]
                                                     → contract.status = READY_FOR_CHECK_IN
```

### 13.2 员工分配与入住

```
STAFF → POST /occupancy/assignments                  创建 Assignment（ASSIGNED）
  occupancy: 容量校验

TENANT/STAFF → POST /occupancy/stays/check-in
  occupancy: assignment → CONSUMED；Stay(CHECKED_IN)
  occupancy → MeteringCommandPort.collectCheckInReadings()         [Port]
  occupancy → AccountingCommandPort.createPersonalDepositBill()    [Port]
    accounting → BillingServicePort.createBill()                   [Port，stub]
  occupancy → IamTenantPort.createOrEnableTenantUser()              [Port]
  occupancy → DoorLockPort.issueCredential()                        [Port，stub]
```

### 13.3 每日水电日结

```
02:00 CRON → ScheduleTaskRunner.dailyMeterSettlement()
  schedule → MeteringScheduleTrigger.runDailySettlement(yesterday)  [Port]
    metering → OccupancyQueryPort.findRoomIdsWithCheckedInStays()   [Port]
    FOR EACH room:
      metering → AssetQueryPort.getStoreIdByRoomId()                [Port]
      metering → AccountingQueryPort.getEnterpriseSubBalance()      [Port]
      metering → AccountingCommandPort.deductForDailySettlement()   [Port]
      metering: 写 room_daily_charge
```

### 13.4 员工退宿

```
STAFF/TENANT → PUT /occupancy/stays/{id}/check-out
  occupancy: Stay → CHECKED_OUT
  occupancy → MeteringCommandPort.collectCheckOutReadings()         [Port]
  occupancy → DoorLockPort.revokeCredential()                       [Port，stub]
  occupancy → AccountingCommandPort.settlePersonalDepositOnCheckout()[Port]
    accounting → BillingServicePort.createRefund()                  [Port，stub]
  occupancy → IamTenantPort.disableTenantUser()                     [Port]
```

### 13.5 企业退房（全部）

```
STAFF → PUT /contract/contracts/{id}/full-return
  contract → OccupancyQueryPort.hasCheckedInStays()                 [Port]
  contract → AccountingCommandPort.settleFullReturn()                [Port]
    accounting → BillingServicePort.createRefund()                   [Port，stub]
  contract: contract_room.status → RETURNED
  contract → AssetCommandPort.releaseRoom()                          [Port]
  结算完成后：
  billing-service → POST /internal/v1/accounting/bills/paid          [HTTP，真实跨进程，stub 阶段无真实触发]
  accounting → ContractCallbackPort.onSettlementCompleted()          [Port]
  contract: status → COMPLETED
```

---

## 14. 待决策清单（持续跟踪）

| # | 问题 | 状态 | 说明 |
|---|---|---|---|
| 1 | customer 模块是否需要建 | ✅ 已决策 | 新建，本文档已设计 |
| 2 | RoomStatus 状态机与新设计不一致 | 🟡 暂缓 | provisional adapter，保守逻辑先跑通，后续与产品对齐后再决定改哪边 |
| 3 | room_info 缺 maxOccupancy 字段 | ✅ 已澄清 | 实为误判，`LivingNum` 字段已存在，无需改表 |
| 4 | IamTenantPort 设计 | ✅ 已决策 | 不加 biz_ref，沿用手机号去重；`real_name` 字段已存在，补传值即可 |
| 5 | AssetQueryPort/AssetCommandPort | ✅ 已决策 | Port Interface，propertymgr 内实现，含 provisional adapter |
| 6 | DoorLockPort | ✅ 已决策 | stub 占位，不阻塞开发 |
| 7 | apiDelegate.mustache 修复状态 | 🟡 待你确认 | zip 快照里仍是旧版，需确认你本地工作区是否已应用修复 |
| 8 | billing-service / device-service 契约 | ✅ 已决策 | stub 占位，待对方契约就绪后替换 |
| 9 | propertymgr 改造为 OpenAPI-First | 🟡 暂不处理 | 新模块通过 Port Interface 调用，不依赖其 HTTP 接口，未来视需要再迁移 |
| 10 | customer-external.yaml 尚未实际编写 | 🟡 待开发 | 本文档已给出端点清单，需要补 yaml 文件 |

---

## 15. Port Interface 汇总

| Port | 定义位置 | 实现位置 | 状态 |
|---|---|---|---|
| `IamTenantPort` | iam/api/ | iam/service/IamTenantPortImpl（新建） | 📋 已设计，待实现 |
| `AssetQueryPort` / `AssetCommandPort` | propertymgr/api/ | propertymgr/（适配现有 Repository） | 📋 已设计，待实现 |
| `CustomerQueryPort` | customer/api/ | customer/service/ | 📋 已设计，待实现（含模块本身） |
| `ContractQueryPort` / `ContractCallbackPort` / `ContractScheduleTrigger` | contract/api/ | contract/service/ | 📋 已设计 |
| `OccupancyQueryPort` | occupancy/api/ | occupancy/service/ | 📋 已设计 |
| `MeteringCommandPort` / `MeteringScheduleTrigger` | metering/api/ | metering/service/ | 📋 已设计 |
| `AccountingCommandPort` / `AccountingQueryPort` | accounting/api/ | accounting/service/ | 📋 已设计 |
| `BillingServicePort` | accounting/outer/ | accounting/outer/StubBillingServicePort | 📋 stub 已设计 |
| `DoorLockPort` | occupancy/outer/ | occupancy/outer/StubDoorLockPort | 📋 stub 已设计 |

---

## 16. 推荐开发顺序

```
Step 0（前置，工作量小，建议先做）:
  - 确认 apiDelegate.mustache 修复状态（待决策清单 #7）
  - IAM: 新增 UserRepository.findActiveByMobile() + IamTenantPort + 实现
  - propertymgr: 新增 AssetQueryPort/AssetCommandPort + provisional adapter 实现
  - 新建 customer 模块骨架（表 + Repository + Service + CustomerQueryPort + 对外 API）

Step 1（accounting）：
  无上游模块依赖（仅依赖 stub BillingServicePort）
  Liquibase → jOOQ codegen → Repository → Service → Port → Delegate

Step 2（metering）：
  依赖 accounting.AccountingCommandPort/QueryPort（已完成）
  依赖 occupancy.OccupancyQueryPort（日结时需要，可先用接口桩）

Step 3（contract）：
  依赖 propertymgr.AssetCommandPort + accounting.AccountingCommandPort + customer.CustomerQueryPort

Step 4（occupancy）：
  依赖 contract + metering + accounting + iam + propertymgr + customer + stub DoorLockPort

Step 5（schedule）：
  依赖 metering.MeteringScheduleTrigger + contract.ContractScheduleTrigger

Step 6（集成测试）：
  完整链路：企业签约 → 员工分配 → 入住 → 日结 → 退宿 → 退房
  （由于 billing-service 是 stub，支付环节需要手动模拟调用
   POST /internal/v1/accounting/bills/paid 来推进流程）
```

## 附录：权限码汇总

```
customer:enterprise:write / customer:enterprise:read
customer:employee:write   / customer:employee:read
contract:contract:write   / contract:contract:read
contract:room:write       / contract:room:return
occupancy:assignment:write / occupancy:assignment:read
occupancy:stay:checkin    / occupancy:stay:read / occupancy:stay:checkout / occupancy:stay:transfer
metering:binding:write    / metering:binding:read
metering:reading:write    / metering:reading:read
metering:price:write      / metering:price:read
metering:daily-charge:read
accounting:room-account:read
accounting:entry:read
accounting:recharge:write
accounting:bill:write     / accounting:bill:read
accounting:deposit:read
schedule:task:trigger     / schedule:task:read
```
