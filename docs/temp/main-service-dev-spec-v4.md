# Main-Service 开发规格说明书
> 版本：v4.0 | 基于代码快照（2026-05）对齐编写  
> 阅读对象：参与 main-service 后续业务模块开发的开发者或 AI  
> 本文档同时记录了代码与设计之间的已知冲突（见各节"⚠️ 冲突"标注）

---

## 目录
1. [系统定位与整体架构](#1-系统定位与整体架构)
2. [技术栈与开发规范](#2-技术栈与开发规范)
3. [模块总览与依赖关系](#3-模块总览与依赖关系)
4. [iam 模块（已完成）](#4-iam-模块已完成)
5. [propertymgr 模块（已完成，待对齐）](#5-propertymgr-模块已完成待对齐)
6. [contract 模块（未开发）](#6-contract-模块未开发)
7. [occupancy 模块（未开发）](#7-occupancy-模块未开发)
8. [metering 模块（未开发）](#8-metering-模块未开发)
9. [accounting 模块（未开发）](#9-accounting-模块未开发)
10. [schedule 模块（未开发）](#10-schedule-模块未开发)
11. [跨模块业务流程](#11-跨模块业务流程)
12. [⚠️ 已知冲突与待决策清单](#12-已知冲突与待决策清单)
13. [Port Interface 汇总](#13-port-interface-汇总)

---

## 1. 系统定位与整体架构

### 1.1 业务定位
**企业住宿租赁管理系统（ToB）**。  
我方管理物业房间，与企业签订合同，企业将房间分配给员工入住。系统全程管理：合同签约 → 房间分配 → 员工入住 → 日常水电计费 → 换宿/退宿 → 合同结算。

### 1.2 微服务拓扑

```
Internet
  └─ Gateway Service（鉴权、灰度、限流）
       ├─ main-service（本文档范围）
       │    ├─ iam          已完成，认证授权
       │    ├─ propertymgr  已完成，房源管理（旧模式）
       │    ├─ contract     待开发，合同生命周期
       │    ├─ occupancy    待开发，分配/入住/退宿/换宿
       │    ├─ metering     待开发，水电计量
       │    ├─ accounting   待开发，账务账单押金
       │    └─ schedule     待开发，定时任务
       ├─ billing-service   独立微服务（支付）
       └─ device-service    独立微服务（IoT 设备）
```

### 1.3 核心业务流程一览

```
企业签约
  ① STAFF 创建合同草稿（含多个房间）
  ② confirmContract → 锁房 + 生成企业签约账单（首月租金 + 企业押金）
  ③ 企业支付账单 → billing-service 回调 → 合同激活（READY_FOR_CHECK_IN）

员工入住
  ④ STAFF 为员工创建 Assignment（分配名额）
  ⑤ 员工自助或 STAFF 代办 checkIn：
       消费 Assignment → 创建 Stay → 采集入住水电底数
       → 创建个人押金账单 → 下发门锁凭证

日常运营（每日 02:00 自动）
  ⑥ 每日水电日结：读数 → 计费 → 扣减房间账户（企业余额优先，不足由入住员工平摊）

退宿
  ⑦ STAFF/员工发起 checkOut → 采集退宿读数 → 回收门锁
     → 结算个人押金（退差价）

换宿（跨合同支持）
  ⑧ STAFF 发起 transfer → 旧房间退宿读数 → 新房间入住读数
     → 押金资格迁移（不重缴）→ 余额迁移 → 门锁切换

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
| openapi-generator | 7.17.0 | 从 yaml 生成 API 接口（Delegate 模式） |
| Spring Security | 6.x | 权限控制（@PreAuthorize） |
| Redis | 6.x | Token 缓存、会话管理 |

### 2.2 代码分层（以 iam 为金标准）

```
main/{module}/
├── delegate/         ← *ApiDelegateImpl（薄转接层，只做参数解构和结果映射）
├── service/          ← 业务逻辑（@Transactional 只在此层）
├── repo/             ← Repository 接口 + jooq/ 子包（实现）
│   ├── XxxRepository.java      ← 接口
│   ├── model/                  ← Repo 层专用 POJO（非 jOOQ 生成类）
│   └── jooq/JooqXxxRepository.java  ← DSLContext 实现
└── api/              ← Port Interface（供其他模块调用，唯一跨模块依赖点）
    ├── XxxQueryPort.java
    └── XxxCommandPort.java
```

### 2.3 OpenAPI-First 原则

每个模块必须：
1. 在 `src/main/resources/openapi/` 放置 yaml 文件
2. `pom.xml` 中添加对应 `execution`（参照已有 iam-external、iam-internal）
3. 实现生成的 `*ApiDelegate` 接口
4. **禁止** 手写 `@RestController`（propertymgr 是历史包袱，新模块不延续此模式）

### 2.4 权限声明

所有需鉴权的接口在 yaml 中声明 `x-required-permission: {module}:{resource}:{action}`，启动时 `PermissionManifestBootstrap` 自动同步到 `iam_permission` 表。

### 2.5 错误处理

- 业务异常：抛 `BusinessException(HttpStatus, errorCode, message)`
- 参数校验失败：框架层 Bean Validation 自动处理
- 错误响应统一格式：`ErrorResponse`（来自 common-components.yaml）

### 2.6 数据库规范

- XML Changelog 放在 `db/changelog/changes/`，主文件通过 `includeAll` 扫描
- 文件命名：`NNN-{desc}.xml`（序号保证执行顺序）
- **jOOQ POJO 通过 codegen 生成，禁止手写 Entity 类**
- 表名使用 snake_case，列名使用 snake_case

---

## 3. 模块总览与依赖关系

### 3.1 依赖矩阵（严格单向）

```
schedule ──────────────────────────────────────────────→ metering.MeteringScheduleTrigger
                                                        → contract.ContractScheduleTrigger

occupancy ─→ contract.ContractQueryPort
           ─→ propertymgr(asset).AssetQueryPort / AssetCommandPort      ⚠️ Port 待定义
           ─→ iam.IamTenantPort                                          ⚠️ Port 待提取
           ─→ metering.MeteringCommandPort
           ─→ accounting.AccountingCommandPort
           ─→ device-service.DoorLockPort                               ⚠️ 待定义

contract  ─→ propertymgr(asset).AssetCommandPort
           ─→ accounting.AccountingCommandPort
           ─→ customer(enterprise).CustomerQueryPort                     ⚠️ 模块不存在

metering  ─→ occupancy.OccupancyQueryPort
           ─→ accounting.AccountingCommandPort

accounting ─→ billing-service（HTTP Client）
            ─→ contract.ContractCallbackPort（回调）
```

**关键原则：模块间只允许调用对方 `api/` 包下的 Port Interface，不允许调用 service/repo 层。**

---

## 4. iam 模块（已完成）

### 4.1 职责
认证（登录、刷新、登出）+ 授权（用户、角色、权限、数据权限）

### 4.2 已实现功能
| 子功能 | 状态 | 关键类 |
|---|---|---|
| 密码登录 / 微信小程序登录 / 微信 Web 登录 | ✅ | `PasswordLoginService`, `AuthApiDelegateImpl` |
| JWT 签发（User JWT → Service JWT by Gateway） | ✅ | `UserJwtIssuer`, `AuthVersionService` |
| Refresh Token Rotation | ✅ | `RefreshTokenService` |
| 用户 CRUD（STAFF/CONTRACTOR） | ✅ | `UserMutationService`, `UserLifecycleService` |
| 角色权限管理 | ✅ | `RoleManagementService` |
| 数据权限（AREA/STORE 维度） | ✅ | `IamDataScopeApiDelegateImpl` |
| 启动时权限同步 | ✅ | `PermissionManifestBootstrap` |
| 内部 TENANT 用户预创建 | ✅ | `InternalTenantUserService` |

### 4.3 对外 Port（待提取为接口）

`InternalTenantUserService.createTenantUser()` 已实现，但尚未封装为跨模块 Port Interface。  
occupancy 模块需要时，需在 `iam/api/` 下提取：

```java
// iam/api/IamTenantPort.java  ← 待创建
public interface IamTenantPort {
    /**
     * 分配入住资格时调用：创建或激活 TENANT 用户，使其可以自助入住（小程序扫码登录）。
     * 实现：调用 InternalTenantUserService.createTenantUser()，已存在则幂等返回。
     */
    Long createOrEnableTenantUser(Long tenantId, String mobile, String realName);

    /**
     * 退宿后调用：软删除 IAM 用户，注销登录能力。
     * 实现：调用 UserLifecycleService.softDelete()。
     */
    void disableTenantUser(Long iamUserId);
}
```

### 4.4 用户类型说明
| UserType | 说明 | 登录方式 |
|---|---|---|
| STAFF | 我方内部员工（管理员/客服/运维等） | 密码 + 微信 |
| CONTRACTOR | 外部承包商/维修人员 | 密码 |
| TENANT | 企业员工（住宿人） | 微信小程序 |
| SYSTEM | 内部系统账号 | 不可登录 |

---

## 5. propertymgr 模块（已完成，待对齐）

### 5.1 职责
管理物业的区域（region）、门店（store）、楼栋（building）、户型（floor_plan）、房间（room_info）。  
这是设计文档中"Asset Service"的对应实现，但使用的是老开发模式。

### 5.2 现状
- **模式**：手写 `@RestController`，**不是** OpenAPI-First
- **API 路径前缀**：`/main/room/...`、`/main/store/...` 等（非新设计的 `/occupancy/...` 路径）
- **数据库**：`room_info`、`store_info`、`building_info`、`floor_plan_info`（已存在）
- **房间状态枚举（现有）**：

```
WAIT_PUBLISH, EMPTY, WAIT_CHECK_IN, CHECKED_IN, WAIT_CHECK_OUT, OVER_DUE, NORMAL
```

### ⚠️ 冲突 5.1：房间状态枚举与新设计不一致

新设计（来自 spec 讨论）期望的状态机为：
```
MAINTENANCE → AVAILABLE → ALLOCATED → PENDING_CHECKING → OCCUPIED → PENDING_CHECKOUT → AVAILABLE
```
与现有 `RoomStatus` 完全不对应。

**需要决策**：
1. 是否在 `room_info` 表新增/修改 `RoomStatus` 列的枚举值映射？
2. 或者在 contract/occupancy 模块内部维护房间状态（不改动 propertymgr 的 room_info 表，而是通过 contract_room 表的状态来推断）？

**推荐方案**：由 contract_room.status（ACTIVE/RETURNED）+ stay 的实时统计来对外呈现房间状态，不改 room_info 原有字段，在 propertymgr 的 AssetQueryPort 实现中做计算映射。

### ⚠️ 冲突 5.2：room_info 表缺少 maxOccupancy 字段

occupancy 模块的容量校验需要 `room.maxOccupancy`，但当前 `room_info` 表没有此字段。  
**解决方案**：新增 Liquibase changeSet，为 `room_info` 添加 `max_occupancy INT DEFAULT 1`。

### 5.3 需要暴露的 Port Interface

新开发的 contract 和 occupancy 模块需要调用 propertymgr 的能力，但必须通过 Port Interface，不能直接引用 propertymgr 的 Service/Repository。

```java
// propertymgr/api/AssetQueryPort.java  ← 待创建
public interface AssetQueryPort {
    RoomInfo getRoomInfo(Long roomId);
    List<RoomInfo> getRoomsByIds(List<Long> roomIds);
    int getMaxOccupancy(Long roomId);
    Long getStoreIdByRoomId(Long roomId);
    boolean isRoomAvailableForContract(Long roomId);  // room_info 无 ACTIVE contract
}

// propertymgr/api/AssetCommandPort.java  ← 待创建
public interface AssetCommandPort {
    // 以下方法在 propertymgr 实现时只需记录到 room_info 的新 status 字段（可选）
    // 或者实现为空操作，由 contract_room 状态驱动
    void lockRoom(Long roomId, Long contractId);
    void releaseRoom(Long roomId, Long contractId);
}

// Port 内的 Record
record RoomInfo(Long id, Long storeId, String roomNo, int maxOccupancy, String currentStatus) {}
```

### 5.4 数据库表概览

| 表名 | 主键 | 关键字段 |
|---|---|---|
| `region_info` | regionId | name |
| `store_info` | storeId | regionId, name |
| `building_info` | buildingId | storeId, floors |
| `floor_plan_info` | planId | buildingId, roomType, livingNum |
| `room_info` | RoomId(BIGINT) | BuildingId, Level, RoomNum, RoomStatus, LivingNum, EID/WID/HWID |

> **注意**：`room_info.EID/WID/HWID` 是老设计中直接存设备 ID。新设计使用 `meter_device_binding` 表做版本化绑定，两者并存，推荐新功能使用 `meter_device_binding`。

---

## 6. contract 模块（未开发）

### 6.1 职责
合同是整个系统的业务锚点。contract 模块负责：
- 合同的创建、确认（锁房）、取消
- 驱动企业签约账单生成
- 部分退房 / 全部退房
- 接收账务回调推进合同状态

### 6.2 合同状态机

```
DRAFT
  │ confirmContract() → 锁房 + 生成企业签约账单
  ↓
SIGN_BILL_PENDING
  │ 企业支付签约账单 → accounting 回调 /internal/v1/contract/sign-bill-paid
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
  │ 所有账单结清 → accounting 回调 /internal/v1/contract/settlement-completed
  ↓
COMPLETED

DRAFT / SIGN_BILL_PENDING → CANCELLED（取消条件：账单未支付 且 无 CHECKED_IN Stay）
```

### 6.3 数据库表

**contract（合同主表）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| contract_no | VARCHAR(32) UNIQUE | CTR-{yyyyMM}-{seq} |
| enterprise_id | BIGINT | 企业 ID（引用 customer 模块，⚠️ 见冲突 12.1） |
| contract_status | VARCHAR(30) | 见状态机枚举 |
| start_date | DATE | |
| end_date | DATE | |
| payment_mode | VARCHAR(20) | MONTHLY / QUARTERLY |
| sign_bill_id | BIGINT | accounting.bill.id（企业签约账单） |
| remark | VARCHAR(500) | |
| created_by / created_at / updated_at | | |

**contract_room（合同房间）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_id | BIGINT | |
| room_id | BIGINT | room_info.RoomId |
| signed_rent | DECIMAL(12,2) | 本房间月租金 |
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
| rule_snapshot | JSON | 其他规则参数 |

### 6.4 对外 API（来自 contract-external.yaml）

| Method | Path | operationId | 权限 | 说明 |
|---|---|---|---|---|
| POST | /contract/contracts | createContractDraft | contract:contract:write | 创建草稿（不锁房） |
| POST | /contract/contracts/query | queryContracts | contract:contract:read | 分页查询 |
| GET | /contract/contracts/{id} | getContract | contract:contract:read | 详情（含房间+规则） |
| PUT | /contract/contracts/{id}/confirm | confirmContract | contract:contract:write | 确认 → SIGN_BILL_PENDING |
| PUT | /contract/contracts/{id}/cancel | cancelContract | contract:contract:write | 取消 |
| PUT | /contract/contracts/{id}/partial-return | applyPartialReturnRooms | contract:room:return | 部分退房 |
| PUT | /contract/contracts/{id}/full-return | applyFullReturnRooms | contract:room:return | 全部退房 |
| POST | /contract/contracts/{id}/rooms | addRoomsToContract | contract:room:write | 草稿添加房间 |

### 6.5 内部回调接口（来自 contract-internal.yaml）

| Path | operationId | 调用方 | 触发时机 |
|---|---|---|---|
| POST /internal/v1/contract/sign-bill-paid | contractSignBillPaid | accounting | 企业签约账单支付成功 → READY_FOR_CHECK_IN |
| POST /internal/v1/contract/settlement-completed | contractSettlementCompleted | accounting | 退房结算完成 → COMPLETED |

### 6.6 关键业务逻辑

**confirmContract 动作序列：**
```
1. 校验 status == DRAFT
2. 校验所有 rooms 无活跃合同（contract_room.status=ACTIVE）
3. 调用 AssetCommandPort.lockRoom(roomId) 逐一锁房
4. 快照 ContractChargeRule（从请求体写入 DB，后续不跟随全局配置变化）
5. 调用 AccountingCommandPort.createEnterpriseSignBill(contractId, enterpriseId, totalAmount, depositAmount)
6. 更新 contract.status = SIGN_BILL_PENDING，存入 sign_bill_id
```

**applyPartialReturnRooms 动作序列：**
```
1. 校验目标房间均无 CHECKED_IN Stay（通过 OccupancyQueryPort.hasCheckedInStays）
2. 调用 AccountingCommandPort.settlePartialReturn(contractId, returnedRoomIds)（异步结算）
3. 更新 contract_room.status = RETURNED
4. 若所有房间均 RETURNED → contract.status = FULLY_RETURNED，否则 PARTIALLY_RETURNED
5. 调用 AssetCommandPort.releaseRoom(roomId)
```

### 6.7 暴露的 Port Interface

```java
// contract/api/ContractQueryPort.java
public interface ContractQueryPort {
    ContractInfo getContract(Long contractId);
    boolean isContractAllowingAssignment(Long contractId);  // SIGN_BILL_PENDING or READY_FOR_CHECK_IN
    boolean isContractReadyForCheckIn(Long contractId);
    List<ContractRoomInfo> getActiveRoomsByContract(Long contractId);
    List<Long> findRoomsWithCheckedInStays();              // metering 日结用
    Long getSharedAccountIdByRoom(Long roomId);            // metering 日结扣款用
    List<ContractInfo> findContractsDueBy(LocalDate date); // schedule 用
}

// contract/api/ContractCallbackPort.java（由 accounting 调用）
public interface ContractCallbackPort {
    void onSignBillPaid(Long contractId, Long billId);
    void onSettlementCompleted(Long contractId);
}
```

---

## 7. occupancy 模块（未开发）

### 7.1 职责
管理"居住关系"的全生命周期：分配（Assignment）→ 入住（Stay）→ 退宿 / 换宿。  
occupancy 是跨模块编排的核心，入住和退宿流程需要协调 metering + accounting + iam + device。

### 7.2 核心概念

**Assignment（分配）**：STAFF 为某员工在某合同房间预留入住名额。  
- 容量规则：`ASSIGNED数 + CHECKED_IN数 < Room.maxOccupancy`
- 状态：`ASSIGNED → CONSUMED`（check-in 时消费）/ `CANCELLED`

**Stay（入住记录）**：实际居住关系，水电计费、押金台账的归属锚点。  
- 状态：`CHECKED_IN → TRANSFERRED`（换宿）/ `CHECKED_OUT`

### 7.3 数据库表

**room_assignment**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_id | BIGINT | |
| contract_room_id | BIGINT | |
| room_id | BIGINT | 冗余，加速容量查询 |
| tenant_id | BIGINT | 企业员工 ID（引用 customer 模块，⚠️ 见冲突 12.1） |
| status | VARCHAR(20) | ASSIGNED / CONSUMED / CANCELLED |
| assigned_at | DATETIME(3) | |
| assigned_by | BIGINT | STAFF user_id |

**stay**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| source_assignment_id | BIGINT | 消费的 assignment（UNIQUE） |
| contract_id | BIGINT | |
| contract_room_id | BIGINT | |
| room_id | BIGINT | 冗余 |
| tenant_id | BIGINT | |
| stay_status | VARCHAR(20) | CHECKED_IN / TRANSFERRED / CHECKED_OUT |
| check_in_at | DATETIME(3) | |
| check_out_at | DATETIME(3) | |
| checked_in_by | BIGINT | 操作者 |

**transfer_record**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| tenant_id | BIGINT | |
| from_stay_id / from_room_id | BIGINT | |
| to_stay_id / to_room_id / to_contract_id | BIGINT | 跨合同 |
| transfer_at | DATETIME(3) | |
| transferred_by | BIGINT | STAFF |

### 7.4 对外 API（来自 occupancy-external.yaml）

**分配管理（STAFF 专属）**

| Method | Path | operationId | 权限 | 说明 |
|---|---|---|---|---|
| POST | /occupancy/assignments | assignTenantToRoom | occupancy:assignment:write | 创建 Assignment |
| POST | /occupancy/assignments/query | queryAssignments | occupancy:assignment:read | 分页查询 |
| PUT | /occupancy/assignments/{id}/cancel | cancelAssignment | occupancy:assignment:write | 取消 |

**入住/退宿/换宿**

| Method | Path | operationId | 权限 | 说明 |
|---|---|---|---|---|
| POST | /occupancy/stays/check-in | checkIn | occupancy:stay:checkin | 入住（自助或代办） |
| POST | /occupancy/stays/query | queryStays | occupancy:stay:read | 分页查询 |
| GET | /occupancy/stays/{id} | getStay | occupancy:stay:read | 详情 |
| PUT | /occupancy/stays/{id}/check-out | checkOut | occupancy:stay:checkout | 退宿 |
| POST | /occupancy/stays/{id}/transfer | transferTenant | occupancy:stay:transfer | 换宿（跨合同） |

### 7.5 checkIn 编排逻辑（核心，@Transactional）

```
前置校验：
  - assignment.status = ASSIGNED
  - contract.status = READY_FOR_CHECK_IN（通过 ContractQueryPort）
  - CHECKED_IN 数 < Room.maxOccupancy（通过 AssetQueryPort + stay 表统计）

编排步骤：
  1. assignment.status → CONSUMED
  2. 创建 Stay(CHECKED_IN)
  3. 调用 MeteringCommandPort.collectCheckInReadings(stayId, roomId)
     → 采集入住底数，建立 CHECK_IN 锚点
  4. 调用 AccountingCommandPort.createPersonalDepositBill(tenantId, stayId, contractId, roomId)
     → 生成个人押金账单（金额取 system_config.personal_deposit_amount）
  5. 调用 IamTenantPort.createOrEnableTenantUser(tenantId, mobile, realName)
     → 激活小程序登录能力
  6. 调用 DoorLockPort.issueCredential(tenantId, roomId, stayId)
     → 下发门锁凭证（调用 device-service）

注意：步骤 3-6 均为跨模块/跨服务调用，失败需整体回滚。
      建议对 device-service 调用做熔断处理（凭证下发非强一致性需求）。
```

### 7.6 checkOut 编排逻辑

```
1. stay.status → CHECKED_OUT，记录 check_out_at
2. 调用 MeteringCommandPort.collectCheckOutReadings(stayId, roomId)
   → 采集退宿读数，建立 CHECK_OUT 锚点，返回用量摘要
3. 调用 DoorLockPort.revokeCredential(tenantId, roomId, stayId)
4. 调用 AccountingCommandPort.settlePersonalDepositOnCheckout(tenantId, stayId)
   → 计算押金退差价，生成退款账单
5. 调用 IamTenantPort.disableTenantUser(iamUserId)
   → 注销小程序登录能力
```

### 7.7 transfer（换宿）编排逻辑

```
前置校验：
  - fromStay.status = CHECKED_IN
  - accounting 确认个人押金 DepositLedger.status = ACTIVE
  - 目标合同 status = READY_FOR_CHECK_IN
  - 目标房间 effectiveOccupied < maxOccupancy

编排步骤：
  1. collectCheckOutReadings(fromStay)    ← 原房间退宿读数
  2. fromStay → TRANSFERRED
  3. 创建目标 Assignment（ASSIGNED → 立即 CONSUMED）
  4. 创建新 Stay(CHECKED_IN)
  5. collectCheckInReadings(toStay)       ← 新房间入住读数
  6. AccountingCommandPort.transferPersonalDepositEligibility(tenantId, fromStayId, toStayId)
     → 押金资格迁移（不重缴）
  7. AccountingCommandPort.transferTenantSubBalance(tenantId, fromRoomId, toRoomId)
     → 个人水电余额迁移
  8. DoorLock: revokeCredential(fromRoom) + issueCredential(toRoom)
```

### 7.8 暴露的 Port Interface

```java
// occupancy/api/OccupancyQueryPort.java
public interface OccupancyQueryPort {
    List<StayInfo> getCheckedInStaysByRoom(Long roomId);      // metering 日结用
    List<Long> findRoomIdsWithCheckedInStays(LocalDate date); // metering 日结用
    boolean hasCheckedInStays(Long roomId);                   // contract 退房前校验
    StayInfo getStay(Long stayId);
    int countAssignedByRoom(Long roomId);
    int countCheckedInByRoom(Long roomId);
}
```

---

## 8. metering 模块（未开发）

### 8.1 职责
水电表读数采集、锚点管理（入住/退宿时刻的底数）、每日用量计算、触发 accounting 扣减。

### 8.2 数据库表

**meter_device_binding（设备绑定，版本化）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| device_id | BIGINT | device-service 设备 ID |
| meter_type | VARCHAR(20) | WATER / ELECTRICITY / HOT_WATER |
| binding_start_at | DATETIME(3) | |
| binding_end_at | DATETIME(3) | NULL=当前有效 |
| initial_reading | DECIMAL(12,3) | 新绑定时的起始表盘读数 |
| is_active | TINYINT(1) | |

> **注意**：旧 `room_info.EID/WID/HWID` 仍存在但不再使用，新功能统一走 `meter_device_binding`。

**meter_reading（读数记录）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| device_binding_id | BIGINT | |
| meter_type | VARCHAR(20) | |
| reading_value | DECIMAL(12,3) | 表盘累计读数（非增量） |
| reading_time | DATETIME(3) | |
| source | VARCHAR(20) | IOT / MANUAL |
| anchor_type | VARCHAR(20) | CHECK_IN / CHECK_OUT / PERIODIC / NULL |
| stay_id | BIGINT | 锚点关联 stay |
| reading_group_id | VARCHAR(64) | 同一入住/退宿动作的水/电/热水共享一组 ID |
| device_id_raw | VARCHAR(100) | IoT 幂等键 |

**meter_price_config（计费单价）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| store_id | BIGINT | NULL=全局默认 |
| meter_type | VARCHAR(20) | |
| unit_price | DECIMAL(10,4) | |
| effective_from / effective_to | DATE | |

**settlement_anchor（结算锚点）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| stay_id | BIGINT | CHECK_IN/CHECK_OUT 时关联 |
| anchor_type | VARCHAR(20) | CHECK_IN / DAILY_CLOSING / CHECK_OUT |
| reading_group_id | VARCHAR(64) | |
| anchor_time | DATETIME(3) | |

**room_daily_charge（日结产出）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| settlement_date | DATE | |
| water_amount / electric_amount / hot_water_amount | DECIMAL(12,2) | |
| total_amount | DECIMAL(12,2) | |
| status | VARCHAR(20) | PENDING / SETTLED / PARTIAL / FAILED |
> UNIQUE(room_id, settlement_date)，幂等重跑安全

**tenant_apportionment（分摊明细）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_daily_charge_id | BIGINT | |
| tenant_id / stay_id | BIGINT | |
| settlement_date | DATE | |
| apportioned_amount | DECIMAL(12,2) | |

### 8.3 对外 API（来自 metering-external.yaml）

| Method | Path | operationId | 权限 | 说明 |
|---|---|---|---|---|
| GET | /metering/rooms/{roomId}/bindings | listRoomDeviceBindings | metering:binding:read | 查询绑定列表 |
| POST | /metering/rooms/{roomId}/bindings | bindDevice | metering:binding:write | 绑定/更换设备 |
| PUT | /metering/rooms/{roomId}/bindings/{bindingId}/unbind | unbindDevice | metering:binding:write | 解绑 |
| POST | /metering/readings | submitMeterReading | metering:reading:write | 人工录入 PERIODIC 读数 |
| POST | /metering/readings/query | queryMeterReadings | metering:reading:read | 分页查询 |
| GET | /metering/prices | listMeterPrices | metering:price:read | 单价列表 |
| POST | /metering/prices | createMeterPrice | metering:price:write | 新增单价版本 |
| POST | /metering/daily-charges/query | queryRoomDailyCharges | metering:daily-charge:read | 日费用查询 |
| GET | /metering/daily-charges/{id}/apportionments | getDailyChargeApportionments | metering:daily-charge:read | 分摊明细 |

### 8.4 内部 API（来自 metering-internal.yaml）

| Path | operationId | 调用方 | 说明 |
|---|---|---|---|
| POST /internal/v1/metering/readings/iot-push | pushIotReading | device-service | IoT 读数推送，幂等 |
| POST /internal/v1/metering/anchors/check-in | collectCheckInReadings | occupancy | 入住锚点采集 |
| POST /internal/v1/metering/anchors/check-out | collectCheckOutReadings | occupancy | 退宿锚点采集 |

### 8.5 日结执行逻辑（由 schedule 触发，02:00 每日）

```
runDailySettlement(date = yesterday):

1. 从 OccupancyQueryPort 获取所有有 CHECKED_IN Stay 的 room_id 列表
2. FOR EACH room:
   a. 取 date 最新 DAILY_CLOSING 锚点读数（无则取当日最新 PERIODIC 读数）
      取 date-1 的对应读数（无则取 CHECK_IN 锚点读数）
   b. usage = end_reading - start_reading（按表类型分别计算）
   c. 查询有效单价（store 级别优先，否则全局默认）
   d. total_amount = Σ(usage_type × unit_price_type)
   e. 构建 DailyDeductionRequest：
      - enterpriseCoverAmount = min(enterprise_sub_balance, total_amount)
      - tenantApportionments = 剩余金额按 CHECKED_IN Stay 均摊
   f. 调用 AccountingCommandPort.deductForDailySettlement(request)
   g. 写 room_daily_charge（幂等，unique on room_id+date）
3. 写 schedule_task_log，汇报 success/partial/failed 数

扣减顺序（由 accounting 执行）：
  企业子余额 → 不足部分由各 CHECKED_IN 租客均摊 → 仍不足则记欠费（INSUFFICIENT 流水）
```

### 8.6 暴露的 Port Interface

```java
// metering/api/MeteringCommandPort.java
public interface MeteringCommandPort {
    String collectCheckInReadings(CollectReadingsCommand cmd);   // 返回 readingGroupId
    UsageSummary collectCheckOutReadings(CollectReadingsCommand cmd); // 返回用量摘要
    void submitPeriodicReading(RecordReadingCommand cmd);
}

// metering/api/MeteringScheduleTrigger.java
public interface MeteringScheduleTrigger {
    DailySettlementResult runDailySettlement(LocalDate date);
}
```

---

## 9. accounting 模块（未开发）

### 9.1 职责
账务中心：
- 房间水电账户（含企业/租客子余额）
- 所有类型账单的台账管理
- 个人押金和企业押金台账
- 日结扣减、结算、充值、退款

### 9.2 账单类型

| bill_type | 触发时机 | 付款方 |
|---|---|---|
| ENTERPRISE_SIGN_BILL | confirmContract | 企业（首月租金 + 企业押金合计） |
| PERSONAL_DEPOSIT_BILL | checkIn | 租客个人 |
| RECHARGE_BILL | 企业/租客主动充值 | 充值方 |
| SETTLEMENT_BILL | 退房/退宿有欠费时 | 欠费方 |
| REFUND_BILL | 退房/退宿有余额时 | 我方退还 |

### 9.3 数据库表

**room_account（房间水电账户，1:1 对应房间）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT UNIQUE | |
| current_contract_id | BIGINT | |
| status | VARCHAR(20) | ACTIVE / FROZEN / CLOSED |

**room_account_sub_balance（子余额）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_account_id | BIGINT | |
| owner_type | VARCHAR(20) | ENTERPRISE / TENANT |
| owner_id | BIGINT | enterprise_id 或 tenant_id |
| available_balance | DECIMAL(12,2) | |
| frozen_balance | DECIMAL(12,2) | |
> UNIQUE(room_account_id, owner_type, owner_id)

**扣减优先级：** 企业子余额 → 不足部分租客子余额均摊

**room_account_entry（流水）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_account_id | BIGINT | |
| sub_balance_id | BIGINT | |
| owner_type / owner_id | | |
| entry_type | VARCHAR(30) | RECHARGE/DAILY_DEDUCT/TRANSFER_OUT/TRANSFER_IN/REFUND/FREEZE/UNFREEZE/ADJUST/INSUFFICIENT |
| amount | DECIMAL(12,2) | 正数入账，负数出账 |
| related_bill_id | BIGINT | |
| occurred_at | DATETIME(3) | |

**bill（账单）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| bill_no | VARCHAR(32) UNIQUE | |
| bill_type | VARCHAR(30) | 见账单类型表 |
| bill_owner_type / bill_owner_id | | |
| contract_id / room_id / tenant_id | BIGINT | |
| total_amount | DECIMAL(12,2) | |
| bill_status | VARCHAR(20) | PENDING / PAID / CANCELLED / REFUNDED |
| billing_service_bill_id | BIGINT | billing-service 侧 ID |
| paid_at | DATETIME(3) | |

**deposit_ledger（押金台账）**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| deposit_type | VARCHAR(20) | ENTERPRISE / PERSONAL |
| owner_type / owner_id | | |
| contract_id | BIGINT | |
| current_stay_id | BIGINT | 个人押金换宿时更新此字段 |
| original_amount / occupied_amount / refundable_amount | DECIMAL | |
| status | VARCHAR(20) | PENDING_PAYMENT / ACTIVE / REFUND_PENDING / CLOSED |
| related_bill_id | BIGINT | |

**system_config（全局配置）**
| config_key | config_value | 说明 |
|---|---|---|
| personal_deposit_amount | 500.00 | 个人押金金额（元），全局统一 |

### 9.4 对外 API（来自 accounting-external.yaml）

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

### 9.5 内部 API（来自 accounting-internal.yaml）

| Path | operationId | 调用方 | 说明 |
|---|---|---|---|
| POST /internal/v1/accounting/bills/paid | billPaidCallback | billing-service | 支付成功统一回调（路由各 billType） |
| POST /internal/v1/accounting/enterprise-sign-bill | createEnterpriseSignBill | contract | 创建企业签约账单 |
| POST /internal/v1/accounting/personal-deposit-bill | createPersonalDepositBill | occupancy | 创建个人押金账单 |
| POST /internal/v1/accounting/personal-deposit/transfer-eligibility | transferPersonalDepositEligibility | occupancy | 换宿押金资格迁移 |
| POST /internal/v1/accounting/personal-deposit/settle-checkout | settlePersonalDepositOnCheckout | occupancy | 退宿押金结算 |
| POST /internal/v1/accounting/room-accounts/transfer-sub-balance | transferTenantSubBalance | occupancy | 换宿余额迁移 |
| POST /internal/v1/accounting/settlement/partial-return | settlePartialReturn | contract | 部分退房结算 |
| POST /internal/v1/accounting/settlement/full-return | settleFullReturn | contract | 全部退房结算 |
| POST /internal/v1/accounting/daily-deduction | deductForDailySettlement | metering | 日结扣减 |

### 9.6 billPaidCallback 路由规则

```
billing-service 回调后，根据 bill.bill_type 路由：

ENTERPRISE_SIGN_BILL：
  → DepositLedger(ENTERPRISE) → ACTIVE
  → 调用 ContractCallbackPort.onSignBillPaid(contractId, billId)

PERSONAL_DEPOSIT_BILL：
  → DepositLedger(PERSONAL) → ACTIVE

RECHARGE_BILL：
  → 找到对应 room_account_sub_balance
  → available_balance += actualAmount
  → 写 entry(RECHARGE, IN)

SETTLEMENT_BILL（追缴账单）：
  → 找欠费的 room_account_entry(INSUFFICIENT)
  → 标记处理完成
  → 若结算完成则回调 ContractCallbackPort.onSettlementCompleted

幂等键：billing_service_bill_id，重复回调直接返回 200
```

### 9.7 暴露的 Port Interface

```java
// accounting/api/AccountingCommandPort.java
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

// accounting/api/AccountingQueryPort.java
public interface AccountingQueryPort {
    BigDecimal getEnterpriseSubBalance(Long roomId, Long enterpriseId);
    DepositLedgerInfo getPersonalDepositLedger(Long tenantId, Long stayId);
}
```

---

## 10. schedule 模块（未开发）

### 10.1 职责
纯触发器，不含任何业务逻辑。统一管理定时任务，提供防重和补跑能力。

### 10.2 定时任务清单

| taskType | Cron | 调用目标 | 说明 |
|---|---|---|---|
| DAILY_METER_SETTLEMENT | `0 0 2 * * ?` | MeteringScheduleTrigger.runDailySettlement(yesterday) | 每日 02:00 水电日结 |
| CONTRACT_EXPIRY_CHECK | `0 0 1 * * ?` | ContractScheduleTrigger.checkContractExpiry(today) | 每日 01:00 到期提醒 |

### 10.3 数据库表

**schedule_task_log**
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| task_type | VARCHAR(50) | |
| scheduled_date | DATE | 业务日期（幂等键之一） |
| status | VARCHAR(20) | RUNNING / SUCCESS / PARTIAL_FAILURE / FAILED |
| triggered_by | VARCHAR(20) | CRON / MANUAL |
| total_count / success_count / failure_count | INT | |
| error_summary | VARCHAR(2000) | |
| started_at / finished_at | DATETIME(3) | |
> UNIQUE(task_type, scheduled_date)，核心防重约束

### 10.4 对外 API（来自 schedule-external.yaml）

| Method | Path | operationId | 权限 |
|---|---|---|---|
| POST | /schedule/tasks/{taskType}/trigger | triggerTask | schedule:task:trigger |
| POST | /schedule/tasks/query | queryTaskLogs | schedule:task:read |

### 10.5 ScheduleTaskRunner 核心逻辑

```java
@Scheduled(cron = "0 0 2 * * ?")
public void dailyMeterSettlement() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    // 1. 检查 (DAILY_METER_SETTLEMENT, yesterday) 是否已有 SUCCESS 记录（DB 唯一约束）
    // 2. INSERT schedule_task_log(status=RUNNING)，失败说明已在跑
    // 3. 调用 meteringScheduleTrigger.runDailySettlement(yesterday)
    // 4. UPDATE schedule_task_log(status=SUCCESS/PARTIAL_FAILURE/FAILED)
    // 注意：日志更新使用 REQUIRES_NEW 事务（即使业务失败也必须记录）
}
```

---

## 11. 跨模块业务流程

### 11.1 合同签约到激活

```
STAFF → POST /contract/contracts                     创建草稿（DRAFT）
STAFF → PUT  /contract/contracts/{id}/confirm        锁房 + 账单
  contract → AssetCommandPort.lockRoom()
  contract → AccountingCommandPort.createEnterpriseSignBill()
  accounting → billing-service（创建账单 + 返回支付 URL）
                                                     → contract.status = SIGN_BILL_PENDING
企业支付（billing-service 处理）
  billing-service → POST /internal/v1/accounting/bills/paid
  accounting: DepositLedger(ENTERPRISE) → ACTIVE
  accounting → ContractCallbackPort.onSignBillPaid()
                                                     → contract.status = READY_FOR_CHECK_IN
```

### 11.2 员工分配与入住

```
STAFF → POST /occupancy/assignments                  创建 Assignment（ASSIGNED）
  occupancy: 容量校验（ASSIGNED + CHECKED_IN < maxOccupancy）

TENANT/STAFF → POST /occupancy/stays/check-in        办理入住
  occupancy: assignment → CONSUMED
  occupancy: Stay(CHECKED_IN)
  occupancy → MeteringCommandPort.collectCheckInReadings()    采集底数
  occupancy → AccountingCommandPort.createPersonalDepositBill()
    accounting → billing-service（创建押金账单）
  occupancy → IamTenantPort.createOrEnableTenantUser()       激活小程序
  occupancy → DoorLockPort.issueCredential()                 下发门锁

TENANT 支付押金（小程序）
  billing-service → POST /internal/v1/accounting/bills/paid
  accounting: DepositLedger(PERSONAL) → ACTIVE
```

### 11.3 每日水电日结

```
02:00 CRON → ScheduleTaskRunner.dailyMeterSettlement()
  schedule → MeteringScheduleTrigger.runDailySettlement(yesterday)
    metering → OccupancyQueryPort.findRoomIdsWithCheckedInStays()
    FOR EACH room:
      metering: 计算用量 × 单价 = total_amount
      metering: 构建扣减计划（企业优先，剩余均摊）
      metering → AccountingCommandPort.deductForDailySettlement()
        accounting: SELECT FOR UPDATE 子余额，顺序扣减
        accounting: 写 room_account_entry（DAILY_DEDUCT / INSUFFICIENT）
      metering: 写 room_daily_charge（SETTLED / PARTIAL / FAILED）
```

### 11.4 员工退宿

```
STAFF/TENANT → PUT /occupancy/stays/{id}/check-out
  occupancy: Stay → CHECKED_OUT
  occupancy → MeteringCommandPort.collectCheckOutReadings()   退宿读数
  occupancy → DoorLockPort.revokeCredential()                回收门锁
  occupancy → AccountingCommandPort.settlePersonalDepositOnCheckout()
    accounting: 计算应退 = original - occupied
    accounting: DepositLedger → REFUND_PENDING
    accounting → billing-service（创建退款账单）
  occupancy → IamTenantPort.disableTenantUser()              注销小程序
```

### 11.5 企业退房（全部）

```
STAFF → PUT /contract/contracts/{id}/full-return    （前提：所有房间无 CHECKED_IN Stay）
  contract: 校验 OccupancyQueryPort.hasCheckedInStays = false
  contract → AccountingCommandPort.settleFullReturn(contractId)
    accounting: 汇总所有 ROOM_SHARED 子余额 → 退还企业
    accounting: DepositLedger(ENTERPRISE) → REFUND_PENDING
    accounting → billing-service（退款账单）
  contract: contract_room.status → RETURNED
  contract: AssetCommandPort.releaseRoom()
  结算完成后：
  billing-service → POST /internal/v1/accounting/bills/paid
  accounting → ContractCallbackPort.onSettlementCompleted()
  contract: status → COMPLETED
```

---

## 12. ⚠️ 已知冲突与待决策清单

### 🔴 冲突 12.1：缺少 customer（企业+员工）模块

**现象**：contract 模块创建草稿需要 `enterpriseId`，occupancy 模块分配需要校验 `tenant 属于该企业`，但代码中**完全没有** customer/enterprise 模块。

**contract-external.yaml** 的 `CreateContractDraftRequest` 有 `enterpriseId` 字段，但没有任何实现对应的企业管理接口。

**需要决策**：
1. 是否需要新建 `customer` 模块（包含企业信息、员工信息管理 API）？
2. 或者企业/员工信息由外部系统管理，main-service 只存 ID 不做校验？
3. `CustomerQueryPort.isTenantOfEnterprise()` 的实现策略是什么？

**临时建议**：先在 `customer` 包下创建简单的 `EnterpriseRepository` 和 `TenantRepository`，提供 `CustomerQueryPort` 的实现（只读查询），不对外暴露 API，数据通过管理员直接写入 DB 或由其他系统同步。

---

### 🔴 冲突 12.2：propertymgr 的 RoomStatus 枚举与新设计不匹配

**现象**：propertymgr 的 `RoomStatus.java` 定义的状态（WAIT_PUBLISH, EMPTY 等）与设计文档期望的状态（MAINTENANCE, AVAILABLE 等）完全不同。

**需要决策**：
1. 直接修改 `RoomStatus.java` 并迁移历史数据？
2. 不修改 propertymgr，新增 `AssetQueryPort` 时在适配层做映射？

**推荐方案 2**：在 `propertymgr/api/AssetQueryPortImpl` 中，将 propertymgr 的老状态映射为新设计需要的含义（如 `EMPTY → AVAILABLE`），对上层模块透明。

---

### 🟡 冲突 12.3：metering-external.yaml 中有 3 个接口返回 inline type:array

**现象**：`listRoomDeviceBindings`、`listMeterPrices`、`getDailyChargeApportionments` 响应 schema 仍是 `type: array`（直接返回数组），与 apiDelegate.mustache 的修复后行为可能仍有问题。

**当前状态**：上传的 zip 中的 yaml 文件是旧版本，尚未应用之前对话中已修复的版本。

**解决方案**：使用本次已修复的 `metering-external.yaml`（已把三个端点的响应改为 named wrapper schema：`MeterDeviceBindingListResult`、`MeterPriceConfigListResult`、`TenantApportionmentListResult`）。

---

### 🟡 冲突 12.4：room_info 表缺少 maxOccupancy 字段

**现象**：occupancy 容量校验需要知道房间最大入住人数，但 `room_info` 表没有此字段。

**解决方案**：新增 Liquibase changeSet（放在 `005-add-room-max-occupancy.xml`）：
```xml
<addColumn tableName="room_info">
  <column name="max_occupancy" type="INT" defaultValueNumeric="1" remarks="最大入住人数"/>
</addColumn>
```

---

### 🟡 冲突 12.5：IamTenantPort 未提取为 Port Interface

**现象**：`InternalTenantUserService` 已实现创建 TENANT 用户的逻辑，但 occupancy 模块需要调用时没有 Port Interface 可依赖，不符合模块隔离原则。

**解决方案**：在 `iam/api/` 下新建 `IamTenantPort.java`，由 `InternalTenantUserService` 实现（需补充 `disableTenantUser` 方法，调用已有的 `UserLifecycleService.softDelete()`）。

---

### 🟡 冲突 12.6：DoorLockPort 未定义

**现象**：occupancy 的 checkIn/checkOut/transfer 需要调用 device-service 的门锁 API，但 `DoorLockPort` 接口未定义，device-service 的调用方式（HTTP URL、鉴权方式）未确定。

**解决方案**：在 `outer/device/DoorLockPort.java` 下定义接口，用 Spring `RestClient` 或 `WebClient` 实现，具体 URL 从 application.yml 配置。

---

### 🟢 冲突 12.7：propertymgr 使用旧开发模式

**现象**：propertymgr 的代码使用手写 `@RestController`，API 路径（`/main/room/...`）与新设计不一致，且没有 OpenAPI yaml 定义。

**决策**：**暂不重构 propertymgr**。新模块只通过 `AssetQueryPort`/`AssetCommandPort` 调用 propertymgr，对外 API 的差异由前端适配（或后续在 BFF 层统一）。未来有需要时再迁移为 OpenAPI-First 模式。

---

## 13. Port Interface 汇总

> 这是新模块开发前需要确认或实现的所有跨模块 Port 接口清单：

| Port | 定义位置 | 实现位置 | 状态 |
|---|---|---|---|
| `IamTenantPort` | iam/api/ | iam/service/InternalTenantUserService + UserLifecycleService | ⚠️ 待提取 |
| `AssetQueryPort` | propertymgr/api/ | propertymgr/room/ 现有 Repository | ⚠️ 待创建 |
| `AssetCommandPort` | propertymgr/api/ | propertymgr/room/ 现有 Repository | ⚠️ 待创建 |
| `CustomerQueryPort` | customer/api/ | customer 模块（待建）| ❌ 模块不存在 |
| `ContractQueryPort` | contract/api/ | contract/service/ | 📋 设计已定义 |
| `ContractCallbackPort` | contract/api/ | contract/service/ | 📋 设计已定义 |
| `ContractScheduleTrigger` | contract/api/ | contract/service/ | 📋 设计已定义 |
| `OccupancyQueryPort` | occupancy/api/ | occupancy/service/ | 📋 设计已定义 |
| `MeteringCommandPort` | metering/api/ | metering/service/ | 📋 设计已定义 |
| `MeteringScheduleTrigger` | metering/api/ | metering/service/ | 📋 设计已定义 |
| `AccountingCommandPort` | accounting/api/ | accounting/service/ | 📋 设计已定义 |
| `AccountingQueryPort` | accounting/api/ | accounting/service/ | 📋 设计已定义 |
| `DoorLockPort` | outer/device/ | outer/device/（HTTP Client） | ❌ 未定义 |

---

## 附录 A：推荐开发顺序

按依赖从底层到上层：

```
Step 0（前置）:
  - 提取 IamTenantPort，补充 disableTenantUser 实现
  - 创建 AssetQueryPort / AssetCommandPort，在 propertymgr 实现
  - 新建 customer 模块骨架 + CustomerQueryPort（哪怕只是 stub）
  - 新增 max_occupancy 列到 room_info
  - 定义 DoorLockPort（可先用 stub 实现）

Step 1（accounting）：
  无上游依赖，最先开发
  Liquibase → jOOQ codegen → Repository → Service → Port → Delegate

Step 2（metering）：
  依赖 accounting.AccountingCommandPort（stub 可替代）
  依赖 occupancy.OccupancyQueryPort（日结时需要）

Step 3（contract）：
  依赖 asset + accounting + customer Port

Step 4（occupancy）：
  依赖 contract + metering + accounting + iam + asset + customer

Step 5（schedule）：
  依赖 metering + contract 的 ScheduleTrigger

Step 6（集成测试）：
  完整链路测试：签约 → 入住 → 日结 → 退宿 → 退房
```

## 附录 B：权限码汇总

所有 `x-required-permission` 会在启动时自动同步，以下供角色配置参考：

```
contract:contract:write  / contract:contract:read
contract:room:write      / contract:room:return
occupancy:assignment:write / occupancy:assignment:read
occupancy:stay:checkin   / occupancy:stay:read / occupancy:stay:checkout / occupancy:stay:transfer
metering:binding:write   / metering:binding:read
metering:reading:write   / metering:reading:read
metering:price:write     / metering:price:read
metering:daily-charge:read
accounting:room-account:read
accounting:entry:read
accounting:recharge:write
accounting:bill:write    / accounting:bill:read
accounting:deposit:read
schedule:task:trigger    / schedule:task:read
```
