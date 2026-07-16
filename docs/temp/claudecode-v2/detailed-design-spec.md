# Main-Service 详细设计 Spec v3
> 基于 propertylease-spec-puml-v2 修正，覆盖 contract / occupancy / metering / accounting / schedule 五个模块  
> payment 对应 billing-service（独立微服务），本文档不覆盖其内部实现，仅定义与其交互的回调接口  
> asset / customer / device 已就绪，本文档仅定义 main-service 对其的调用 Interface

---

## 目录
1. [模块总览与依赖](#1-模块总览与依赖)
2. [Contract 模块](#2-contract-模块)
3. [Occupancy 模块](#3-occupancy-模块)
4. [Metering 模块](#4-metering-模块)
5. [Accounting 模块](#5-accounting-模块)
6. [Schedule 模块](#6-schedule-模块)
7. [跨模块时序](#7-跨模块时序)
8. [已就绪模块 Interface 约定](#8-已就绪模块-interface-约定)

---

## 1. 模块总览与依赖

### 1.1 模块职责

| Module | 定位 | 核心职责 |
|---|---|---|
| `contract` | 合同生命周期 | 合同 CRUD、锁房、生成企业签约账单、发起退房/合同结算 |
| `occupancy` | 居住关系管理 | 分配（Assignment）、入住（Stay）、退宿、换宿 |
| `metering` | 用量计量 | 设备读数、锚点管理、日结计算、生成房间日费用与分摊 |
| `accounting` | 账务中心 | 房间账户、子余额、账单、押金台账、日结扣减、结算退款 |
| `schedule` | 任务调度 | 纯触发器，驱动 metering 日结等周期任务 |
| `iam` | 已完成 | 认证授权，新增 IamTenantPort 供 occupancy 调用 |
| `asset` | 已就绪 | 房间/楼栋/门店，状态管理 |
| `customer` | 已就绪 | 企业、员工（tenant）档案 |
| billing-service | 独立微服务 | 支付单创建、第三方支付、支付回调通知 |

### 1.2 依赖方向（严格单向）

```
schedule
  └─→ metering (MeteringScheduleTrigger)

occupancy
  ├─→ contract  (ContractQueryPort)
  ├─→ customer  (CustomerQueryPort)
  ├─→ asset     (AssetQueryPort / AssetCommandPort)
  ├─→ iam       (IamTenantPort)
  ├─→ metering  (MeteringCommandPort)
  ├─→ accounting (AccountingCommandPort)
  └─→ device-service (DoorLockPort)

contract
  ├─→ asset     (AssetCommandPort - lock/release rooms)
  ├─→ customer  (CustomerQueryPort - validate enterprise)
  └─→ accounting (AccountingCommandPort - create sign bill / settle)

metering
  ├─→ asset     (AssetQueryPort - load room-device bindings)
  ├─→ occupancy (OccupancyQueryPort - load active stays)
  └─→ accounting (AccountingCommandPort - deduct room account)

accounting
  └─→ billing-service (HTTP - create/cancel bills, create payment orders)
```

**不允许逆向依赖。双向场景通过回调接口（internal API）解耦。**

---

## 2. Contract 模块

### 2.1 职责边界

**负责：** 合同生命周期、锁定/释放房间、生成企业签约账单、发起退房与合同结算  
**不负责：** 办理入住、设备读数、账户扣减

### 2.2 合同状态机

```
DRAFT ──[confirmContract]──→ SIGN_BILL_PENDING ──[sign bill paid callback]──→ READY_FOR_CHECK_IN
  │                                 │                                                │
  │[cancel]                   [cancel]*                                    [applyPartialReturn]
  ▼                                 ▼                                                ▼
CANCELLED                      CANCELLED                                  PARTIALLY_RETURNED
                                                                                    │
                                                                     [applyPartialReturn 直至全退]
                                                                                    │
                                                          [applyFullReturn] ←────────┘
                                                                    ▼
                                                           FULLY_RETURNED ──[generate settlement]──→ SETTLING
                                                                                                         │
                                                                                             [settlement closed]
                                                                                                         ▼
                                                                                                    COMPLETED
```
> *SIGN_BILL_PENDING 取消条件：签约账单未支付 且 无任何 CHECKED_IN stay

**ContractRoom 状态：** `ACTIVE` → `RETURNED`（退房后）

### 2.3 核心实体

#### Contract
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_no | VARCHAR(32) UK | CTR-{yyyyMM}-{seq} |
| enterprise_id | BIGINT | 引用 customer 模块 |
| contract_status | VARCHAR(30) | 见状态机 |
| sign_date | DATE | 签约日期 |
| start_date | DATE | 合同起始日 |
| end_date | DATE | 合同截止日 |
| payment_mode | VARCHAR(20) | MONTHLY / QUARTERLY |
| sign_bill_id | BIGINT | billing-service 企业签约账单 ID |
| remark | VARCHAR(500) | |
| created_by / created_at / updated_at | | |

#### ContractRoom
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_id | BIGINT | |
| room_id | BIGINT | |
| signed_rent | DECIMAL(12,2) | 本房间月租金 |
| lease_start_date | DATE | |
| lease_end_date | DATE | |
| status | VARCHAR(20) | ACTIVE / RETURNED |
| returned_at | DATETIME(3) | |

> **唯一约束：** `(room_id, status=ACTIVE)` — 同一房间同时只能在一份 ACTIVE 合同中。  
> DRAFT 不锁房，只有 SIGN_BILL_PENDING 及之后才占用。

#### ContractChargeRule
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_id | BIGINT | |
| charge_type | VARCHAR(30) | ENTERPRISE_DEPOSIT / PERSONAL_DEPOSIT / FIRST_RENT |
| payer_type | VARCHAR(20) | ENTERPRISE / TENANT |
| amount | DECIMAL(12,2) | 金额 |
| rule_snapshot | JSON | 其他规则参数快照 |

> 用于记录合同中约定的各类费用金额与承担方，确认合同时快照固化，后续不随全局配置变化。

### 2.4 关键命令说明

| 命令 | 前置条件 | 主要动作 |
|---|---|---|
| createContractDraft | 企业存在 | 创建合同，status=DRAFT，不锁房 |
| confirmContract | DRAFT，所有房间可锁 | 锁房（asset）+ 生成企业签约账单（accounting）→ SIGN_BILL_PENDING |
| cancelContract | DRAFT 或 (SIGN_BILL_PENDING 且账单未付且无 CHECKED_IN stay) | 释放房间 + 作废账单 → CANCELLED |
| activateContractAfterSignBillPaid | 来自 billing-service 回调 | → READY_FOR_CHECK_IN |
| applyPartialReturnRooms | 目标房间无 CHECKED_IN stay | 触发 accounting 结算 → PARTIALLY_RETURNED |
| applyFullReturnRooms | 所有房间无 CHECKED_IN stay | 触发 accounting 合同结算 → FULLY_RETURNED → SETTLING |

---

## 3. Occupancy 模块

### 3.1 职责边界

**负责：** Assignment 生命周期、Stay 生命周期、换宿  
**不负责：** 合同状态流转、账单生成、账户扣减

### 3.2 核心概念

```
Assignment（分配）
  = STAFF 预安排动作，消耗容量名额，为 Tenant 创建入住资格

Stay（入住关系）
  = 消费 Assignment 产生的实际居住记录，是水电计量、账单归属的锚点

容量规则：
  effectiveOccupied = ASSIGNED 数 + CHECKED_IN 数 <= Room.maxOccupancy
```

### 3.3 状态机

**RoomAssignment：** `ASSIGNED` → `CONSUMED`（check-in 消费）/ `CANCELLED`

**Stay：** `CHECKED_IN` → `TRANSFERRED`（换宿转出）/ `CHECKED_OUT`

### 3.4 核心实体

#### RoomAssignment
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| contract_id | BIGINT | |
| contract_room_id | BIGINT | |
| room_id | BIGINT | 冗余，加速容量查询 |
| tenant_id | BIGINT | 引用 customer 模块员工/租客 |
| status | VARCHAR(20) | ASSIGNED / CONSUMED / CANCELLED |
| assigned_at | DATETIME(3) | |
| assigned_by | BIGINT | STAFF user_id |
| cancelled_at | DATETIME(3) | |
| cancelled_by | BIGINT | |

> **索引：** `(room_id, status=ASSIGNED)` 用于容量计算

#### Stay
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| source_assignment_id | BIGINT | 入住消费的 assignment |
| contract_id | BIGINT | |
| contract_room_id | BIGINT | |
| room_id | BIGINT | 冗余 |
| tenant_id | BIGINT | |
| stay_status | VARCHAR(20) | CHECKED_IN / TRANSFERRED / CHECKED_OUT |
| check_in_at | DATETIME(3) | |
| check_out_at | DATETIME(3) | CHECKED_OUT / TRANSFERRED 时填写 |
| checked_in_by | BIGINT | 操作者（self or STAFF） |

#### TransferRecord
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| tenant_id | BIGINT | |
| from_stay_id | BIGINT | |
| from_contract_id | BIGINT | |
| from_room_id | BIGINT | |
| to_stay_id | BIGINT | 新 stay |
| to_contract_id | BIGINT | 允许跨合同 |
| to_contract_room_id | BIGINT | |
| to_room_id | BIGINT | |
| transfer_at | DATETIME(3) | |
| transferred_by | BIGINT | STAFF user_id |

### 3.5 关键命令说明

| 命令 | 执行者 | 前置条件 | 主要动作 |
|---|---|---|---|
| assignTenantToRoom | STAFF | contract 处于 SIGN_BILL_PENDING 或 READY_FOR_CHECK_IN；effectiveOccupied < maxOccupancy | 创建 ASSIGNED assignment；IAM 创建/激活 tenant 自助入住能力 |
| cancelAssignment | STAFF | assignment.status = ASSIGNED | → CANCELLED |
| selfCheckIn | TENANT | assignment.status=ASSIGNED；contract.status=READY_FOR_CHECK_IN；CHECKED_IN 数 < maxOccupancy | 消费 assignment → CONSUMED；创建 Stay(CHECKED_IN)；metering 采集入住读数；accounting 创建个人押金账单；device 下发门锁凭证 |
| staffCheckIn | STAFF | 同上 | 同上，操作者为 STAFF |
| checkOut | TENANT/STAFF | stay.status=CHECKED_IN | Stay→CHECKED_OUT；metering 采集退宿读数；device 回收门锁；accounting 结算个人押金 |
| transferTenant | STAFF | fromStay=CHECKED_IN；押金 ACTIVE；目标合同 READY_FOR_CHECK_IN；目标房间容量允许 | 目标房间需有有效 assignment（新建或复用）；fromStay→TRANSFERRED；新 Stay(CHECKED_IN)；迁移押金资格；迁移租客子余额；门锁切换 |

> **换宿说明：** 换宿操作由 STAFF 发起，内部自动为目标房间生成一条新的 assignment 并立即消费（或要求预先有 assignment），具体看实现选择，推荐"换宿时自动创建目标 assignment 并消费"以简化流程。

---

## 4. Metering 模块

### 4.1 职责边界

**负责：** 设备读数收集、锚点管理、日结计算、产出房间日费用和分摊结果  
**不负责：** 账户扣减（委托 accounting）、账单生成

### 4.2 核心实体

#### MeterReading
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| device_binding_id | BIGINT | 关联设备绑定版本 |
| meter_type | VARCHAR(20) | WATER / ELECTRICITY / HOT_WATER |
| reading_value | DECIMAL(12,3) | 表盘累计读数 |
| reading_time | DATETIME(3) | |
| source | VARCHAR(20) | IOT / MANUAL |
| anchor_type | VARCHAR(20) | CHECK_IN / CHECK_OUT / PERIODIC，NULL 表示普通读数 |
| stay_id | BIGINT | 锚点关联的 stay（CHECK_IN/CHECK_OUT 时填写） |
| operator_id | BIGINT | 人工录入时填 |

#### MeterDeviceBinding（设备绑定版本）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| device_id | BIGINT | 引用 device-service |
| meter_type | VARCHAR(20) | |
| binding_start_at | DATETIME(3) | 绑定生效时间 |
| binding_end_at | DATETIME(3) | NULL 表示当前有效 |
| initial_reading | DECIMAL(12,3) | 新绑定时的起始读数（设备更换使用） |
| is_active | TINYINT(1) | 1=当前有效 |

#### SettlementAnchor
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| stay_id | BIGINT | CHECK_IN/CHECK_OUT 时关联 |
| anchor_type | VARCHAR(20) | CHECK_IN / DAILY_CLOSING / CHECK_OUT |
| reading_group_id | VARCHAR(64) | 一批读数的分组标识（同一锚点的水/电/热水读数共享一个 group） |
| anchor_time | DATETIME(3) | |

#### RoomDailyCharge（日结产出：房间维度）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT | |
| settlement_date | DATE | |
| water_amount | DECIMAL(12,2) | |
| electric_amount | DECIMAL(12,2) | |
| hot_water_amount | DECIMAL(12,2) | |
| total_amount | DECIMAL(12,2) | |
| status | VARCHAR(20) | PENDING / SETTLED / PARTIAL / FAILED |

#### TenantApportionment（日结产出：租客分摊维度）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_daily_charge_id | BIGINT | |
| room_id | BIGINT | |
| tenant_id | BIGINT | |
| stay_id | BIGINT | |
| settlement_date | DATE | |
| apportioned_amount | DECIMAL(12,2) | 本租客应分摊金额 |

### 4.3 日结计算逻辑

```
runDailySettlement(date):

1. 加载所有有效设备绑定（is_active=1）
2. 通过 OccupancyQueryPort 加载该 date 有 CHECKED_IN stay 的 room 列表
3. FOR EACH room:
   a. 取 date 的 DAILY_CLOSING anchor 读数（无则取当日最新读数）
   b. 取 date-1 的 DAILY_CLOSING anchor 读数（无则取 CHECK_IN anchor）
      若设备在 date 内发生更换，按分段计算（新绑定 initial_reading 作为分界点）
   c. usage = end - start
   d. 查询适用单价（MeterPriceConfig）
   e. total_amount = sum(usage × unit_price) across meter_types
   f. 计算企业覆盖部分 + 租客分摊部分：
      - enterprise_covered = min(enterprise_sub_balance, total_amount)
      - tenant_remainder = total_amount - enterprise_covered
      - 若有多个 CHECKED_IN stay，按人数均摊 tenant_remainder
   g. 生成 RoomDailyCharge + TenantApportionment 记录
4. 调用 accounting.deductRoomAccountForDailySettlement(plan)
```

### 4.4 设备更换规则

- 旧绑定：`binding_end_at = now()`，`is_active = 0`
- 新绑定：`binding_start_at = now()`，`initial_reading = 新表当前读数`，`is_active = 1`
- 历史读数、历史流水保持不变
- 房间账户与余额不受影响

---

## 5. Accounting 模块

### 5.1 职责边界

**负责：** 房间账户（含子余额）、所有类型账单台账、押金台账、日结扣减、结算退款  
**不负责：** 支付（委托 billing-service）、设备读数、居住关系

### 5.2 核心实体

#### RoomAccount
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_id | BIGINT UK | 一房间唯一一个账户 |
| current_contract_id | BIGINT | 当前绑定合同 |
| status | VARCHAR(20) | ACTIVE / FROZEN / CLOSED |

> 房间账户跟随 room，不随 contract 生命周期销毁；合同结束后归档（CLOSED），新合同入驻时重新激活（或新建）。

#### RoomAccountSubBalance
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_account_id | BIGINT | |
| owner_type | VARCHAR(20) | ENTERPRISE / TENANT |
| owner_id | BIGINT | enterprise_id 或 tenant_id |
| available_balance | DECIMAL(12,2) | 可用余额 |
| frozen_balance | DECIMAL(12,2) | 冻结余额 |

> **扣减顺序：** 企业子余额先行，不足部分由所有 CHECKED_IN 租客子余额均摊。

#### RoomAccountEntry（账户流水）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| room_account_id | BIGINT | |
| sub_balance_id | BIGINT | 关联子余额 |
| owner_type | VARCHAR(20) | ENTERPRISE / TENANT |
| owner_id | BIGINT | |
| entry_type | VARCHAR(30) | RECHARGE / DAILY_DEDUCT / TRANSFER_OUT / TRANSFER_IN / REFUND / FREEZE / UNFREEZE / ADJUST |
| amount | DECIMAL(12,2) | 正数入账，负数出账 |
| related_bill_id | BIGINT | 关联账单 ID |
| occurred_at | DATETIME(3) | |
| note | VARCHAR(200) | |

#### Bill（账单）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| bill_no | VARCHAR(32) UK | BILL-{type_prefix}-{yyyyMM}-{seq} |
| bill_type | VARCHAR(30) | 见下方类型表 |
| bill_owner_type | VARCHAR(20) | ENTERPRISE / TENANT |
| bill_owner_id | BIGINT | |
| contract_id | BIGINT | |
| room_id | BIGINT | |
| tenant_id | BIGINT | 个人相关账单时填 |
| total_amount | DECIMAL(12,2) | |
| bill_status | VARCHAR(20) | PENDING / PAID / CANCELLED / REFUNDED |
| billing_service_bill_id | BIGINT | billing-service 侧账单 ID |
| paid_at | DATETIME(3) | |
| created_at / updated_at | | |

**账单类型：**

| bill_type | 说明 |
|---|---|
| ENTERPRISE_SIGN_BILL | 企业签约账单（首月租金 + 企业押金） |
| PERSONAL_DEPOSIT_BILL | 租客个人押金账单（入住时创建） |
| RECHARGE_BILL | 房间水电账户充值账单 |
| SETTLEMENT_BILL | 退房/结算追缴账单（欠费收款） |
| REFUND_BILL | 退款账单（押金退还、余额退还） |

#### DepositLedger（押金台账）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| deposit_type | VARCHAR(20) | ENTERPRISE / PERSONAL |
| owner_type | VARCHAR(20) | ENTERPRISE / TENANT |
| owner_id | BIGINT | |
| contract_id | BIGINT | |
| current_stay_id | BIGINT | 个人押金当前关联的 stay（换宿时更新） |
| original_amount | DECIMAL(12,2) | 押金原始金额 |
| occupied_amount | DECIMAL(12,2) | 已被抵扣金额 |
| refundable_amount | DECIMAL(12,2) | 应退金额 |
| status | VARCHAR(20) | PENDING_PAYMENT / ACTIVE / REFUND_PENDING / CLOSED |
| related_bill_id | BIGINT | 对应押金账单 |

> **个人押金金额** = 全局配置值（system_config 表，key=`personal_deposit_amount`）

### 5.3 日结扣减规则

```
deductRoomAccountForDailySettlement(plan):
plan = { roomId, totalAmount, enterpriseCoveredAmount, tenantApportionments[] }

1. SELECT FOR UPDATE room_account WHERE room_id = ?
2. 扣企业子余额：
   actualEnterpriseDeduct = min(enterprise_sub_balance, plan.enterpriseCoveredAmount)
   写 RoomAccountEntry(DAILY_DEDUCT, ENTERPRISE)
3. remainder = plan.totalAmount - actualEnterpriseDeduct
4. 若 remainder > 0，按 tenantApportionments 依次扣租客子余额：
   FOR EACH apportionment:
     actualDeduct = min(tenant_sub_balance, apportionment.amount)
     shortfall += apportionment.amount - actualDeduct
     写 RoomAccountEntry(DAILY_DEDUCT, TENANT)
5. 若 shortfall > 0，写欠费记录（room_account_entry.entry_type=INSUFFICIENT）
```

### 5.4 换宿余额迁移规则

```
transferTenantRoomSubBalance(fromRoomAccountId, toRoomAccountId, tenantId):

1. 查 fromRoomAccount 中 tenant 的 available_balance（设为 migrationAmount）
2. 若 migrationAmount == 0，跳过
3. fromRoomAccount 写出账流水 (TRANSFER_OUT, tenantId, -migrationAmount)
4. toRoomAccount 中 tenant 子余额不存在则创建
5. toRoomAccount 写入账流水 (TRANSFER_IN, tenantId, +migrationAmount)
6. 不影响企业子余额
```

### 5.5 个人押金规则

- 入住时：accounting.createPersonalDepositBill(tenantId, stayId, amount)  
  → 创建 Bill(PERSONAL_DEPOSIT_BILL) + DepositLedger(PENDING_PAYMENT)
- billing-service 回调支付成功：DepositLedger → ACTIVE
- 换宿：transferPersonalDepositEligibility(fromStayId, toStayId)  
  → DepositLedger.current_stay_id 更新，不重新缴纳
- 退宿：settlePersonalDepositOnCheckout(stayId)  
  → 计算应退金额，DepositLedger → REFUND_PENDING → 生成 REFUND_BILL → CLOSED

---

## 6. Schedule 模块

### 6.1 职责

纯触发器，不含业务逻辑。

| 任务 | Cron | 触发目标 | 说明 |
|---|---|---|---|
| DAILY_METER_SETTLEMENT | `0 0 2 * * ?` | MeteringScheduleTrigger.runDailySettlement | 每日 02:00，处理昨日数据 |
| CONTRACT_EXPIRY_CHECK | `0 0 1 * * ?` | ContractScheduleTrigger.checkContractExpiry | 合同到期提醒 |

### 6.2 防重与幂等

- DB 唯一约束 `(task_type, scheduled_date)` 防止重复执行
- 下游触发器均有自身幂等保证（unique 约束）
- 手动触发支持 `force=true` 强制重跑

---

## 7. 跨模块时序

### 7.1 合同签约流程

```
STAFF → Contract: createContractDraft()
Contract → Customer: validate enterprise
Contract → Asset: validate rooms exist
Contract → STAFF: DRAFT

STAFF → Contract: confirmContract()
Contract → Asset: lockRooms(roomIds)
Contract → Accounting: createEnterpriseSignBill(contractId, firstRent+deposit)
Accounting → billing-service: createBill()
Accounting → Contract: signBillId
Contract → STAFF: SIGN_BILL_PENDING

[Enterprise pays sign bill via billing-service]
billing-service → Accounting: /internal/v1/accounting/bills/{id}/paid
Accounting → Contract: /internal/v1/contract/sign-bill-paid
Contract: status → READY_FOR_CHECK_IN
```

### 7.2 分配+入住流程

```
STAFF → Occupancy: assignTenantToRoom(contractRoomId, tenantId)
Occupancy → Contract: validateStatus in {SIGN_BILL_PENDING, READY_FOR_CHECK_IN}
Occupancy → Customer: validateTenantBelongsToEnterprise
Occupancy → Asset: getRoom (validate capacity: ASSIGNED+CHECKED_IN < maxOccupancy)
Occupancy → IAM: createTenantUser / enableSelfCheckInAbility
Occupancy: create ASSIGNED assignment
→ STAFF: assignmentId

TENANT → Occupancy: selfCheckIn(assignmentId)
Occupancy → Contract: validateStatus=READY_FOR_CHECK_IN
Occupancy → Asset: validate CHECKED_IN count < maxOccupancy
Occupancy: assignment → CONSUMED; create Stay(CHECKED_IN)
Occupancy → Metering: collectCheckInReadings(stayId, roomId)
Occupancy → Accounting: createPersonalDepositBill(tenantId, stayId)
Accounting → billing-service: createBill()
Occupancy → DoorLock: issueCredential(tenantId, roomId)
→ TENANT: stay created, deposit bill created
```

### 7.3 日结流程

```
Schedule(02:00) → Metering: runDailySettlement(yesterday)
Metering → Asset: loadActiveRoomDeviceBindings()
Metering → Occupancy: loadCheckedInStaysByDate(date)
Metering: compute usage per room per meterType
Metering: compute RoomDailyCharge + TenantApportionment
Metering → Accounting: deductRoomAccountForDailySettlement(deductionPlan)
Accounting: deduct enterprise sub-balance first
Accounting: deduct tenant sub-balances pro-rata
Accounting: record shortfall if insufficient
→ Metering: deductionResult
Metering: update RoomDailyCharge.status
```

### 7.4 退宿流程

```
TENANT → Occupancy: checkOut(stayId)
Occupancy: Stay → CHECKED_OUT
Occupancy → Metering: collectCheckOutReadings(stayId, roomId)
Occupancy → DoorLock: revokeCredential(tenantId, roomId)
Occupancy → Accounting: settlePersonalDepositOnCheckout(stayId)
Accounting: compute refundable amount
Accounting: DepositLedger → REFUND_PENDING
Accounting → billing-service: createRefundBill()
→ TENANT: checkout completed, refund initiated
```

### 7.5 换宿流程

```
STAFF → Occupancy: transferTenant(fromStayId, toContractRoomId)
Occupancy → Accounting: validate personalDeposit ACTIVE
Occupancy → Contract: validate target contract READY_FOR_CHECK_IN
Occupancy → Asset: validate target room capacity
Occupancy → Metering: collectCheckOutReadings(fromStay)
Occupancy: fromStay → TRANSFERRED
Occupancy: create toAssignment(ASSIGNED) → CONSUMED (内部直接消费)
Occupancy: create toStay(CHECKED_IN)
Occupancy → Metering: collectCheckInReadings(toStay)
Occupancy → Accounting: transferPersonalDepositEligibility(fromStayId, toStayId)
Occupancy → Accounting: transferTenantRoomSubBalance(fromRoom, toRoom, tenantId)
Occupancy → DoorLock: revoke old credential + issue new credential
→ STAFF: transfer completed
```

### 7.6 部分退房流程

```
STAFF → Contract: applyPartialReturnRooms(contractId, roomIds[])
Contract → Occupancy: validateNoCheckedInStays(roomIds)
Contract: ContractRoom → RETURNED
Contract → Accounting: settlePartialReturn(contractId, returnedRooms)
Accounting: compute settlement (outstanding utility + deposit adjustment)
Accounting → billing-service: createSettlementBill if needed
Contract: status → PARTIALLY_RETURNED (若仍有 ACTIVE 房间)
          OR → FULLY_RETURNED (若全部退完)
```

---

## 8. 已就绪模块 Interface 约定

### 8.1 AssetQueryPort
```java
package com.jugu.propertylease.main.asset.api;

public interface AssetQueryPort {
    RoomInfo getRoomInfo(Long roomId);
    List<RoomInfo> getRoomsByIds(List<Long> roomIds);
    int getCheckedInCount(Long roomId);          // 当前 CHECKED_IN 数量（occupancy 查询）
    int getMaxOccupancy(Long roomId);
    List<RoomDeviceBinding> getActiveBindings(Long roomId); // metering 用
    StoreInfo getStoreByRoom(Long roomId);
    boolean isRoomAvailableForLocking(Long roomId); // contract confirmContract 用
}
record RoomInfo(Long id, Long storeId, String status, int maxOccupancy) {}
record RoomDeviceBinding(Long roomId, Long deviceId, String meterType, BigDecimal initialReading) {}
```

### 8.2 AssetCommandPort
```java
public interface AssetCommandPort {
    void lockRoom(Long roomId, Long contractId);       // AVAILABLE → ALLOCATED
    void releaseRoom(Long roomId, Long contractId);    // ALLOCATED → AVAILABLE
}
```

### 8.3 CustomerQueryPort
```java
public interface CustomerQueryPort {
    EnterpriseInfo getEnterprise(Long enterpriseId);
    TenantInfo getTenant(Long tenantId);
    boolean isTenantOfEnterprise(Long tenantId, Long enterpriseId);
}
record TenantInfo(Long id, Long enterpriseId, String name, String mobile) {}
```

### 8.4 IamTenantPort（IAM 模块新增）
```java
public interface IamTenantPort {
    // 创建或激活 TENANT 用户，赋予自助入住权限
    Long createOrEnableTenantUser(Long tenantId, String mobile, String realName);
    // 退宿后软删除 IAM 用户
    void disableTenantUser(Long iamUserId);
}
```

### 8.5 DoorLockPort（device-service 抽象）
```java
public interface DoorLockPort {
    void issueCredential(Long tenantId, Long roomId, Long stayId);
    void revokeCredential(Long tenantId, Long roomId, Long stayId);
}
```
