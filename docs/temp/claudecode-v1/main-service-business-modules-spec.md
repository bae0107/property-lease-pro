# Main-Service 业务模块 Spec

> 版本：v1.0 | 覆盖模块：account / meter / contract / settlement / schedule
> 依赖已完成模块：iam / asset（接口约定见附录）/ enterprise（接口约定见附录）

---

## 目录

1. [模块总览与依赖图](#1-模块总览与依赖图)
2. [Account Module — 预付费钱包账户](#2-account-module)
3. [Meter Module — 水电抄表与日结](#3-meter-module)
4. [Contract Module — 合同与入住（系统锚点）](#4-contract-module)
5. [Settlement Module — 退宿/退房结算](#5-settlement-module)
6. [Schedule Module — 周期任务调度](#6-schedule-module)
7. [附录 A：Asset 对外 Interface 约定](#7-附录-a-asset-interface)
8. [附录 B：Enterprise 对外 Interface 约定](#8-附录-b-enterprise-interface)
9. [附录 C：IAM 对外 Interface 约定（新增）](#9-附录-c-iam-interface)
10. [附录 D：跨模块时序说明](#10-附录-d-跨模块时序)

---

## 1. 模块总览与依赖图

### 1.1 模块职责一句话总结

| Module | 定位 | 核心职责 |
|---|---|---|
| `asset` | 已完成 | 房间/楼栋/门店/区域，管理房间生命周期状态 |
| `enterprise` | 已完成 | 企业信息、员工档案 |
| `iam` | 已完成 | 用户认证、授权、角色管理 |
| `account` | **本次** | 预付费水电钱包，充值/扣款/冻结/退款 |
| `meter` | **本次** | 水电抄表记录，日结用量，触发账户扣款 |
| `contract` | **本次** | 合同全生命周期 + 入住记录，编排各模块协作 |
| `settlement` | **本次** | 退宿/退房最终账目计算，生成结算账单 |
| `schedule` | **本次** | 周期任务统一管理，纯触发器，无业务逻辑 |
| `bff` | 低优先级 | Web 聚合查询，跨模块数据组合 |

### 1.2 依赖方向（单向，不可逆）

```
                    ┌──────────┐
                    │ schedule │  定时触发
                    └────┬─────┘
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
       meter          contract       account
          │              │              ▲
          │     ┌────────┼────────┐     │
          │     ▼        ▼        ▼     │
          │   asset  enterprise  iam    │
          │                            │
          └────────────────────────────┘
               (meter 日结后扣 account)
                         │
                    settlement
                         │
                    ┌────┴────┐
                    ▼         ▼
              account    Billing-Service
```

**关键约束：**
- 每个模块只暴露 `XxxCommandPort` / `XxxQueryPort` Interface，其他模块只依赖接口，不依赖实现
- contract 是唯一的"编排者"，向下调用 asset/enterprise/iam/meter/account
- meter 扣款直接调用 account，不经过 contract
- settlement 调用 meter/account/billing-service，调用完成后回调 contract

---

## 2. Account Module

### 2.1 职责

管理预付费水电钱包账户。账户分两类：

| 账户类型 | 归属 | 创建时机 | 销户时机 |
|---|---|---|---|
| `ROOM_SHARED` | 企业为某合同房间设立的公共水电账户 | 合同激活、房间分配时 | 该房间下所有租客退宿、结算完成后 |
| `TENANT` | 员工个人水电账户 | 员工办理入住时 | 该员工退宿、结算完成后 |

**扣款优先级**：同一房间日结时，先消耗 `ROOM_SHARED` 余额，不足部分由该房间活跃 `TENANT` 账户平摊。

### 2.2 数据模型

```xml
<!-- 004-create-account-tables.xml -->

<!-- 账户表 -->
<createTable tableName="account" remarks="预付费水电账户">
  <column name="id"            type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="account_no"    type="VARCHAR(32)"  remarks="业务账号 ACC-{type}-{yyyyMMdd}-{seq}"><constraints unique="true" nullable="false"/></column>
  <column name="account_type"  type="VARCHAR(20)"  remarks="ROOM_SHARED | TENANT"><constraints nullable="false"/></column>
  <column name="owner_type"    type="VARCHAR(20)"  remarks="CONTRACT_ROOM | TENANCY"><constraints nullable="false"/></column>
  <column name="owner_id"      type="BIGINT"       remarks="对应 contract_room.id 或 tenancy.id"><constraints nullable="false"/></column>
  <column name="balance"       type="DECIMAL(12,2)" defaultValueNumeric="0" remarks="可用余额"/>
  <column name="frozen_balance" type="DECIMAL(12,2)" defaultValueNumeric="0" remarks="冻结余额（结算中）"/>
  <column name="status"        type="VARCHAR(20)"  defaultValue="ACTIVE" remarks="ACTIVE | FROZEN | CLOSED"/>
  <column name="currency"      type="VARCHAR(3)"   defaultValue="CNY"/>
  <column name="created_by"    type="BIGINT"/>
  <column name="created_at"    type="DATETIME(3)"><constraints nullable="false"/></column>
  <column name="updated_at"    type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<addUniqueConstraint tableName="account" columnNames="owner_type,owner_id"
  constraintName="uq_account_owner" remarks="同一 owner 只能有一个账户"/>

<!-- 账户流水表 -->
<createTable tableName="account_transaction" remarks="账户流水明细">
  <column name="id"               type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="account_id"       type="BIGINT"><constraints nullable="false"/></column>
  <column name="transaction_type" type="VARCHAR(30)"
    remarks="TOP_UP|DEDUCT|FREEZE|UNFREEZE|REFUND|ADJUST"/>
  <column name="direction"        type="VARCHAR(10)" remarks="IN | OUT"/>
  <column name="amount"           type="DECIMAL(12,2)"><constraints nullable="false"/></column>
  <column name="balance_before"   type="DECIMAL(12,2)"/>
  <column name="balance_after"    type="DECIMAL(12,2)"/>
  <column name="reference_type"   type="VARCHAR(30)"
    remarks="METER_SETTLEMENT|TOP_UP_BILL|SETTLEMENT|MANUAL"/>
  <column name="reference_id"     type="BIGINT"/>
  <column name="note"             type="VARCHAR(200)"/>
  <column name="created_by"       type="BIGINT"/>
  <column name="created_at"       type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<createIndex tableName="account_transaction" indexName="idx_acct_txn_account_id">
  <column name="account_id"/>
  <column name="created_at"/>
</createIndex>
```

### 2.3 Internal Interface

```java
// com.jugu.propertylease.main.account.api.AccountCommandPort
public interface AccountCommandPort {

    /** 开户，由 contract 编排时调用 */
    AccountInfo openAccount(OpenAccountCommand cmd);

    /** 销户，由 settlement 完成后调用；返回剩余余额（用于退款） */
    CloseAccountResult closeAccount(Long accountId, String reason);

    /**
     * 扣款。余额不足时不抛异常，返回实际扣除金额和欠费金额。
     * 调用方（meter）根据 shortfall 决定是否标记欠费。
     */
    DeductResult deduct(DeductCommand cmd);

    /** 充值（由充值账单支付成功回调触发） */
    void topUp(TopUpCommand cmd);

    /** 冻结余额（结算开始时，防止重复扣款） */
    FreezeResult freeze(Long accountId, BigDecimal amount, String reason);

    /** 解冻（结算取消或异常回滚时） */
    void unfreeze(Long accountId, BigDecimal amount, String reason);

    /** 退款（结算完成，余额退还，调用 Billing Service 发起退款） */
    void refund(Long accountId, BigDecimal amount, String billingRefundReason);
}

// com.jugu.propertylease.main.account.api.AccountQueryPort
public interface AccountQueryPort {
    AccountInfo getAccountByOwner(String ownerType, Long ownerId);
    List<AccountInfo> getAccountsByOwners(String ownerType, List<Long> ownerIds);
    BigDecimal getAvailableBalance(Long accountId);
}

// ── Command / Result records ──────────────────────────────────────────────

record OpenAccountCommand(
    String accountType,   // ROOM_SHARED | TENANT
    String ownerType,     // CONTRACT_ROOM | TENANCY
    Long   ownerId,
    Long   createdBy
) {}

record DeductCommand(
    Long       accountId,
    BigDecimal amount,
    String     referenceType,
    Long       referenceId,
    String     note
) {}

record DeductResult(
    boolean    success,       // true=至少扣了一部分
    BigDecimal deducted,      // 实际扣除金额
    BigDecimal shortfall      // 欠费金额（0 表示足额）
) {}

record TopUpCommand(
    Long       accountId,
    BigDecimal amount,
    Long       billingBillId,  // 对应充值账单 ID
    Long       operatedBy
) {}

record FreezeResult(boolean success, BigDecimal frozen) {}

record CloseAccountResult(BigDecimal remainingBalance) {}

record AccountInfo(
    Long       id,
    String     accountNo,
    String     accountType,
    String     ownerType,
    Long       ownerId,
    BigDecimal balance,
    BigDecimal frozenBalance,
    String     status
) {}
```

### 2.4 External API

```yaml
# 权限码格式：account:{resource}:{action}

POST /api/account/accounts/query
  summary: 分页查询账户列表
  x-required-permission: account:read

GET /api/account/accounts/{id}
  summary: 查询账户详情（含余额、状态）
  x-required-permission: account:read

POST /api/account/accounts/{id}/transactions/query
  summary: 查询账户流水
  x-required-permission: account:read

POST /api/account/accounts/{id}/top-up
  summary: 发起充值（生成充值账单，跳转第三方支付）
  x-required-permission: account:top-up
  请求体: { amount: decimal, paymentMethod: string }
  响应:   { billId, paymentUrl }

# ── 内部接口 ──────────────────────────────────────────────────
POST /internal/v1/account/top-up-callback
  summary: 充值账单支付成功回调（Billing Service 调用）
  请求体: { billId, accountId, amount, paidAt }
```

### 2.5 业务规则

| # | 规则 |
|---|---|
| A-1 | 同一 `(ownerType, ownerId)` 只能有一个非 CLOSED 账户，重复开户抛 `ACCOUNT_ALREADY_EXISTS` |
| A-2 | `deduct` 时余额不足不报错，尽量扣（扣至 0），返回 `shortfall`；调用方处理欠费逻辑 |
| A-3 | `CLOSED` 账户拒绝一切操作，抛 `ACCOUNT_ALREADY_CLOSED` |
| A-4 | `FROZEN` 账户只允许 `unfreeze` 和 `closeAccount`，拒绝 `topUp`/`deduct` |
| A-5 | 充值流程：`topUp` 请求 → 调用 Billing Service 生成充值账单 → 第三方支付 → 回调 `/internal/v1/account/top-up-callback` → 执行 `TopUpCommand` 增加余额 |
| A-6 | 所有余额变动必须同步写 `account_transaction` 流水，保证可追溯 |

---

## 3. Meter Module

### 3.1 职责

- 记录每个房间入住底数（check-in）和退宿读数（check-out）
- 接收 Device Service IoT 推送或人工录入的周期读数
- 由 Schedule 每日触发：计算用量 → 按规则扣 Account

### 3.2 数据模型

```xml
<!-- 005-create-meter-tables.xml -->

<!-- 抄表记录表 -->
<createTable tableName="meter_reading" remarks="水电抄表记录">
  <column name="id"               type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="room_id"          type="BIGINT"><constraints nullable="false"/></column>
  <column name="meter_type"       type="VARCHAR(20)" remarks="WATER | ELECTRICITY"/>
  <column name="reading_value"    type="DECIMAL(12,3)" remarks="表盘累计读数"/>
  <column name="read_at"          type="DATETIME(3)"><constraints nullable="false"/></column>
  <column name="reading_source"   type="VARCHAR(20)" remarks="IOT | MANUAL"/>
  <column name="reading_category" type="VARCHAR(20)" remarks="CHECK_IN | CHECK_OUT | PERIODIC"/>
  <column name="tenancy_id"       type="BIGINT" remarks="CHECK_IN/CHECK_OUT 时关联入住记录"/>
  <column name="operator_id"      type="BIGINT" remarks="人工录入操作员"/>
  <column name="created_at"       type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<createIndex tableName="meter_reading" indexName="idx_meter_reading_room_type_date">
  <column name="room_id"/>
  <column name="meter_type"/>
  <column name="read_at"/>
</createIndex>

<!-- 日结记录表 -->
<createTable tableName="meter_daily_settlement" remarks="水电日结记录">
  <column name="id"                      type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="room_id"                 type="BIGINT"><constraints nullable="false"/></column>
  <column name="settlement_date"         type="DATE"><constraints nullable="false"/></column>
  <column name="meter_type"              type="VARCHAR(20)"/>
  <column name="start_reading"           type="DECIMAL(12,3)"/>
  <column name="end_reading"             type="DECIMAL(12,3)"/>
  <column name="usage"                   type="DECIMAL(12,3)"/>
  <column name="unit_price"              type="DECIMAL(10,4)"/>
  <column name="total_amount"            type="DECIMAL(12,2)"/>
  <column name="deducted_from_shared"    type="DECIMAL(12,2)" defaultValueNumeric="0"/>
  <column name="deducted_from_tenants"   type="DECIMAL(12,2)" defaultValueNumeric="0"/>
  <column name="shortfall"               type="DECIMAL(12,2)" defaultValueNumeric="0" remarks="欠费金额"/>
  <column name="status"                  type="VARCHAR(20)" remarks="PENDING|SETTLED|PARTIAL|FAILED"/>
  <column name="created_at"             type="DATETIME(3)"><constraints nullable="false"/></column>
  <column name="updated_at"             type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<addUniqueConstraint tableName="meter_daily_settlement"
  columnNames="room_id,settlement_date,meter_type"
  constraintName="uq_meter_daily_settlement_room_date_type"/>

<!-- 计费单价配置表 -->
<createTable tableName="meter_price_config" remarks="水电计费单价">
  <column name="id"             type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="store_id"       type="BIGINT" remarks="门店 ID，NULL 表示全局默认"/>
  <column name="meter_type"     type="VARCHAR(20)"><constraints nullable="false"/></column>
  <column name="unit_price"     type="DECIMAL(10,4)"><constraints nullable="false"/></column>
  <column name="effective_from" type="DATE"><constraints nullable="false"/></column>
  <column name="effective_to"   type="DATE" remarks="NULL 表示当前有效"/>
  <column name="created_by"     type="BIGINT"/>
  <column name="created_at"     type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>
```

### 3.3 Internal Interface

```java
// com.jugu.propertylease.main.meter.api.MeterCommandPort
public interface MeterCommandPort {

    /** 记录入住底数（contract 编排入住时调用，必须在创建 tenancy 前调用） */
    void recordCheckInReading(RecordReadingCommand cmd);

    /**
     * 记录退宿读数（contract 编排退宿时调用）。
     * 返回该租户入住期间完整用量摘要，供 settlement 使用。
     */
    TenancyUsageSummary recordCheckOutReading(RecordReadingCommand cmd);

    /** Device Service 推送 IoT 读数 / 人工录入周期读数（schedule 日结前也会调用） */
    void submitPeriodicReading(RecordReadingCommand cmd);
}

// com.jugu.propertylease.main.meter.api.MeterQueryPort
public interface MeterQueryPort {

    /** 获取某入住期间全量用量汇总，供 settlement 结算时使用 */
    TenancyUsageSummary getUsageSummaryForTenancy(Long tenancyId);

    /** 获取房间最新读数 */
    Optional<MeterReadingInfo> getLatestReading(Long roomId, String meterType);
}

// com.jugu.propertylease.main.meter.api.MeterScheduleTrigger
public interface MeterScheduleTrigger {
    /** 由 schedule 每日调用，执行全量房间日结 */
    DailySettlementResult triggerDailySettlement(LocalDate date);
}

// ── Command / Result records ──────────────────────────────────────────────

record RecordReadingCommand(
    Long       roomId,
    String     meterType,       // WATER | ELECTRICITY
    BigDecimal readingValue,    // 表盘累计读数
    String     source,          // IOT | MANUAL
    String     category,        // CHECK_IN | CHECK_OUT | PERIODIC
    Long       tenancyId,       // CHECK_IN/CHECK_OUT 时必填
    Long       operatorId       // 人工录入时填
) {}

record TenancyUsageSummary(
    Long       tenancyId,
    BigDecimal waterUsage,
    BigDecimal electricityUsage,
    BigDecimal totalAmount,       // 按当期单价计算
    BigDecimal alreadyDeducted,   // 日结已扣金额
    BigDecimal outstanding        // 未结清欠费
) {}

record DailySettlementResult(
    LocalDate date,
    int       totalRooms,
    int       settledRooms,
    int       partialRooms,    // 有欠费的房间数
    int       failedRooms,
    BigDecimal totalAmount,
    BigDecimal totalShortfall
) {}
```

### 3.4 External API

```yaml
POST /api/meter/readings
  summary: 人工录入抄表读数
  x-required-permission: meter:reading:write
  请求体: { roomId, meterType, readingValue, readAt, category }

POST /api/meter/readings/query
  summary: 分页查询抄表记录
  x-required-permission: meter:reading:read

POST /api/meter/settlements/query
  summary: 分页查询日结记录
  x-required-permission: meter:settlement:read

GET /api/meter/prices
  summary: 查询当前计费单价（含历史）
  x-required-permission: meter:price:read

POST /api/meter/prices
  summary: 设置计费单价（新增版本，不覆盖历史）
  x-required-permission: meter:price:write

# ── 内部接口 ──────────────────────────────────────────────────
POST /internal/v1/meter/readings/push
  summary: Device Service 推送 IoT 读数
  请求体: { roomId, meterType, readingValue, readAt, deviceId }
```

### 3.5 日结执行逻辑

```
triggerDailySettlement(date):

FOR EACH room_id IN [有活跃 tenancy 的房间]:
  FOR EACH meter_type IN [WATER, ELECTRICITY]:

    1. 取 date 的最新 PERIODIC 读数 end_reading
       取前一天（或 CHECK_IN 底数）start_reading
       usage = end_reading - start_reading

    2. 查询 meter_price_config（优先 store 级别，fallback 全局默认）
       total_amount = usage × unit_price

    3. 查询该房间的 ROOM_SHARED 账户
       shared_result = account.deduct(sharedAccountId, total_amount, ...)
       deducted_from_shared = shared_result.deducted
       remainder = shared_result.shortfall

    4. IF remainder > 0:
         获取该房间所有 CHECKED_IN 状态的 tenancy
         per_tenant = remainder / tenancy_count  （向上取整，最后一个人承担尾差）
         FOR EACH tenancy:
           tenant_result = account.deduct(tenantAccountId, per_tenant, ...)
           deducted_from_tenants += tenant_result.deducted
           shortfall += tenant_result.shortfall

    5. 写 meter_daily_settlement 记录
       status = shortfall > 0 ? PARTIAL : SETTLED
```

### 3.6 业务规则

| # | 规则 |
|---|---|
| M-1 | 同一房间同一日期同一 meter_type 的日结记录唯一（unique constraint），幂等重跑安全 |
| M-2 | CHECK_IN 底数必须在 tenancy 创建之前录入，作为用量计算起点 |
| M-3 | 单价查找：优先查 store_id 匹配且 effective_from <= today 的最新记录，fallback 全局（store_id IS NULL）|
| M-4 | IOT 推送的读数与人工录入走同一接口，source 字段区分，业务规则无差异 |
| M-5 | 日结读数缺失（IoT 断连、未人工录入）时，该房间标记 `FAILED`，不影响其他房间，告警通知 |

---

## 4. Contract Module

### 4.1 职责

整个系统的锚点，负责：
- 合同全生命周期管理（DRAFT → TERMINATED）
- 合同-房间关联管理（ContractRoom）
- 入住记录管理（Tenancy），编排入住/退宿的跨模块协作

### 4.2 核心概念

| 概念 | 说明 |
|---|---|
| `Contract` | 企业与我方签订的租赁合同，含多个房间，是一切业务的起点 |
| `ContractRoom` | 合同与房间的 M:N 桥接（一房间同时只能属于一份活跃合同） |
| `Tenancy` | 某员工在某合同房间下的入住记录，含入住/退宿时间、关联账户、IAM 用户 |

### 4.3 状态机

**Contract 状态机：**

```
DRAFT ──[确认签约]──→ SIGNED ──[押金支付完成]──→ READY_FOR_ALLOCATION
  │                     │                                │
  │[取消]               │[取消]                  [第一个租客入住]
  ▼                     ▼                                ▼
CANCELLED           CANCELLED                      IN_PROGRESS
                                                        │
                                              [发起退房 or 合同到期]
                                                        ▼
                                                   TERMINATING
                                                        │
                                         [所有 tenancy 退宿且结算完成]
                                                        ▼
                                                   TERMINATED
```

**Tenancy 状态机：**

```
PENDING ──[办理入住]──→ CHECKED_IN ──[发起退宿]──→ CHECKING_OUT ──[结算完成]──→ CHECKED_OUT
```

**ContractRoom 状态机：**

```
ACTIVE ──[房间下所有 tenancy CHECKED_OUT 且结算完成]──→ RELEASED
```

### 4.4 数据模型

```xml
<!-- 006-create-contract-tables.xml -->

<!-- 合同主表 -->
<createTable tableName="contract" remarks="租赁合同">
  <column name="id"              type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="contract_no"     type="VARCHAR(32)" remarks="合同编号，格式: CTR-{yyyyMM}-{seq}">
    <constraints unique="true" nullable="false"/>
  </column>
  <column name="enterprise_id"   type="BIGINT"><constraints nullable="false"/></column>
  <column name="status"          type="VARCHAR(30)" defaultValue="DRAFT"
    remarks="DRAFT|SIGNED|READY_FOR_ALLOCATION|IN_PROGRESS|TERMINATING|TERMINATED|CANCELLED"/>
  <column name="start_date"      type="DATE"><constraints nullable="false"/></column>
  <column name="end_date"        type="DATE"><constraints nullable="false"/></column>
  <column name="monthly_rent"    type="DECIMAL(12,2)"><constraints nullable="false"/></column>
  <column name="payment_cycle"   type="VARCHAR(20)" defaultValue="MONTHLY" remarks="MONTHLY|QUARTERLY"/>
  <column name="deposit_amount"  type="DECIMAL(12,2)"/>
  <column name="deposit_bill_id" type="BIGINT" remarks="Billing Service 押金账单 ID"/>
  <column name="terms_doc_url"   type="VARCHAR(500)" remarks="合同附件 URL"/>
  <column name="cancel_reason"   type="VARCHAR(200)"/>
  <column name="terminate_reason" type="VARCHAR(200)"/>
  <column name="created_by"      type="BIGINT"/>
  <column name="created_at"      type="DATETIME(3)"><constraints nullable="false"/></column>
  <column name="updated_at"      type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<!-- 合同-房间关联表 -->
<createTable tableName="contract_room" remarks="合同与房间关联">
  <column name="id"                type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="contract_id"       type="BIGINT"><constraints nullable="false"/></column>
  <column name="room_id"           type="BIGINT"><constraints nullable="false"/></column>
  <column name="status"            type="VARCHAR(20)" defaultValue="ACTIVE" remarks="ACTIVE | RELEASED"/>
  <column name="shared_account_id" type="BIGINT" remarks="ROOM_SHARED 水电账户 ID"/>
  <column name="allocated_at"      type="DATETIME(3)"/>
  <column name="released_at"       type="DATETIME(3)"/>
  <column name="created_at"        type="DATETIME(3)"><constraints nullable="false"/></column>
  <column name="updated_at"        type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<!-- 同一房间同时只能在一份活跃合同中 -->
<createIndex tableName="contract_room" indexName="idx_contract_room_room_active" unique="true">
  <!-- 通过应用层保证 status=ACTIVE 时 room_id 唯一，DB 层加普通索引 -->
  <column name="room_id"/>
  <column name="status"/>
</createIndex>

<!-- 入住记录表 -->
<createTable tableName="tenancy" remarks="员工入住记录">
  <column name="id"                   type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="contract_id"          type="BIGINT"><constraints nullable="false"/></column>
  <column name="contract_room_id"     type="BIGINT"><constraints nullable="false"/></column>
  <column name="room_id"              type="BIGINT" remarks="冗余，方便查询"/>
  <column name="employee_id"          type="BIGINT"><constraints nullable="false"/></column>
  <column name="iam_user_id"          type="BIGINT" remarks="对应 IAM TENANT 用户"/>
  <column name="tenant_account_id"    type="BIGINT" remarks="个人水电账户 ID"/>
  <column name="status"               type="VARCHAR(20)" defaultValue="PENDING"
    remarks="PENDING|CHECKED_IN|CHECKING_OUT|CHECKED_OUT"/>
  <column name="check_in_at"          type="DATETIME(3)"/>
  <column name="check_out_at"         type="DATETIME(3)"/>
  <column name="expected_check_out_at" type="DATE"/>
  <column name="created_by"           type="BIGINT"/>
  <column name="created_at"           type="DATETIME(3)"><constraints nullable="false"/></column>
  <column name="updated_at"           type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<createIndex tableName="tenancy" indexName="idx_tenancy_contract_id">
  <column name="contract_id"/>
</createIndex>
<createIndex tableName="tenancy" indexName="idx_tenancy_employee_status">
  <column name="employee_id"/>
  <column name="status"/>
</createIndex>

<!-- 合同操作日志 -->
<createTable tableName="contract_operation_log" remarks="合同关键操作记录">
  <column name="id"           type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="contract_id"  type="BIGINT"><constraints nullable="false"/></column>
  <column name="action"       type="VARCHAR(50)"
    remarks="CREATED|SIGNED|ACTIVATED|TENANT_CHECKED_IN|TENANT_CHECKOUT_INITIATED|TENANT_CHECKED_OUT|TERMINATING|TERMINATED|CANCELLED"/>
  <column name="target_id"    type="BIGINT" remarks="tenancy_id 等相关实体 ID（可选）"/>
  <column name="operator_id"  type="BIGINT"/>
  <column name="note"         type="VARCHAR(500)"/>
  <column name="created_at"   type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<!-- 租金账单跟踪表（合同维度，记录每期应生成的租金账单） -->
<createTable tableName="contract_rent_bill" remarks="合同租金账单记录">
  <column name="id"            type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="contract_id"   type="BIGINT"><constraints nullable="false"/></column>
  <column name="bill_id"       type="BIGINT" remarks="Billing Service 账单 ID"/>
  <column name="period_start"  type="DATE"/>
  <column name="period_end"    type="DATE"/>
  <column name="amount"        type="DECIMAL(12,2)"/>
  <column name="status"        type="VARCHAR(20)" remarks="PENDING|PAID|OVERDUE"/>
  <column name="created_at"    type="DATETIME(3)"><constraints nullable="false"/></column>
  <column name="updated_at"    type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>
```

### 4.5 Internal Interface

```java
// com.jugu.propertylease.main.contract.api.ContractQueryPort
public interface ContractQueryPort {

    ContractInfo getContract(Long contractId);

    /** schedule 触发合同到期检查时使用 */
    List<ContractInfo> findContractsDueBy(LocalDate date);

    /** schedule 触发租金账单生成时使用 */
    List<ContractInfo> findContractsNeedingRentBill(LocalDate date);

    List<ContractRoomInfo> getActiveRoomsByContract(Long contractId);

    /** meter 日结、settlement 查询时使用 */
    List<TenancyInfo> getActiveTenanciesByRoom(Long roomId);

    List<TenancyInfo> getActiveTenanciesByContract(Long contractId);
}

// com.jugu.propertylease.main.contract.api.ContractScheduleTrigger
public interface ContractScheduleTrigger {
    /** 检查并生成到期应出的租金账单 */
    void checkAndGenerateRentBills(LocalDate date);
    /** 检查合同到期，发送提醒或自动标记 TERMINATING */
    void checkContractExpiry(LocalDate date);
}

// ── Info records ──────────────────────────────────────────────────────────

record ContractInfo(
    Long   id, String contractNo, Long enterpriseId,
    String status, LocalDate startDate, LocalDate endDate,
    BigDecimal monthlyRent, String paymentCycle
) {}

record ContractRoomInfo(
    Long id, Long contractId, Long roomId,
    String status, Long sharedAccountId
) {}

record TenancyInfo(
    Long   id, Long contractId, Long contractRoomId, Long roomId,
    Long   employeeId, Long iamUserId, Long tenantAccountId,
    String status, OffsetDateTime checkInAt
) {}
```

### 4.6 External API

```yaml
# ── 合同管理 ──────────────────────────────────────────────────

POST /api/contract/contracts
  summary: 创建合同（初始状态 DRAFT）
  x-required-permission: contract:write

POST /api/contract/contracts/query
  summary: 分页查询合同列表（支持按企业、状态过滤）
  x-required-permission: contract:read

GET /api/contract/contracts/{id}
  summary: 合同详情（含房间列表、账单状态）
  x-required-permission: contract:read

PUT /api/contract/contracts/{id}/sign
  summary: 确认签约 DRAFT → SIGNED，生成押金账单
  x-required-permission: contract:write

PUT /api/contract/contracts/{id}/activate
  summary: 激活合同 SIGNED → READY_FOR_ALLOCATION
  note: 押金支付完成后可手动激活；也可通过 deposit-paid 回调自动激活
  x-required-permission: contract:write

PUT /api/contract/contracts/{id}/cancel
  summary: 取消合同（仅 DRAFT/SIGNED 可取消）
  x-required-permission: contract:write

PUT /api/contract/contracts/{id}/terminate
  summary: 发起退房 IN_PROGRESS → TERMINATING
  x-required-permission: contract:write

# ── 合同-房间管理 ──────────────────────────────────────────────

POST /api/contract/contracts/{id}/rooms
  summary: 为合同分配房间（可批量，rooms 传多个）
  x-required-permission: contract:room:write
  note: 合同处于 DRAFT/SIGNED/READY_FOR_ALLOCATION/IN_PROGRESS 时可添加

POST /api/contract/contracts/{id}/rooms/batch-remove
  summary: 从合同移除房间（仅该房间无活跃 tenancy 时可操作）
  x-required-permission: contract:room:write

# ── 入住管理 ──────────────────────────────────────────────────

POST /api/contract/tenancies
  summary: 办理员工入住（触发完整入住编排流程）
  x-required-permission: contract:tenancy:checkin
  请求体: { contractId, roomId, employeeId, expectedCheckOutDate,
            checkInWaterReading, checkInElectricityReading }

POST /api/contract/tenancies/query
  summary: 分页查询入住记录
  x-required-permission: contract:tenancy:read

GET /api/contract/tenancies/{id}
  summary: 入住记录详情
  x-required-permission: contract:tenancy:read

PUT /api/contract/tenancies/{id}/checkout
  summary: 发起退宿（CHECKED_IN → CHECKING_OUT，触发结算流程）
  x-required-permission: contract:tenancy:checkout
  请求体: { checkOutWaterReading, checkOutElectricityReading }

# ── 内部接口 ──────────────────────────────────────────────────

POST /internal/v1/contract/deposit-paid
  summary: 押金账单支付完成回调，触发合同自动激活
  请求体: { contractId, billId, paidAt }

POST /internal/v1/contract/tenancy-settlement-complete
  summary: Settlement 通知退宿结算已完成，更新 tenancy → CHECKED_OUT
  请求体: { tenancyId, settlementId }

POST /internal/v1/contract/rent-bill-paid
  summary: 租金账单支付完成回调
  请求体: { contractId, rentBillId, paidAt }
```

### 4.7 入住编排流程（checkIn）

```
输入：contractId, roomId, employeeId, meterReadings

前置校验：
  - contract.status == IN_PROGRESS 或 READY_FOR_ALLOCATION
  - contractRoom.status == ACTIVE
  - enterprise.isEmployeeOfEnterprise(employeeId, contract.enterpriseId)
  - 该员工无其他 CHECKED_IN/PENDING/CHECKING_OUT 状态的 tenancy（一人一房）
  - roomId 下无其他 CHECKED_IN 状态 tenancy 占满（按房型最大入住人数校验，可配置）

编排（@Transactional）：
  1. asset.assignTenant(roomId, tenancyId_placeholder)
     → 房间状态 PENDING_CHECKING → OCCUPIED
  2. iamPort.createTenantUser(employeeId, enterpriseId)
     → 返回 iamUserId
  3. meter.recordCheckInReading(roomId, WATER, reading)
  4. meter.recordCheckInReading(roomId, ELECTRICITY, reading)
  5. account.openAccount(TENANT, TENANCY, tenancy.id)
     → 返回 tenantAccountId
  6. 持久化 tenancy（status=CHECKED_IN，填入 iamUserId/tenantAccountId）
  7. 若 contract.status == READY_FOR_ALLOCATION → 更新为 IN_PROGRESS
  8. 写 contract_operation_log(TENANT_CHECKED_IN)

注意：步骤 1-5 涉及跨模块调用，若 IAM/meter/account 任一失败需整体回滚。
     asset 的状态变更通过 Interface 调用，asset 内部实现需保证幂等。
```

### 4.8 退宿编排流程（initiateCheckout）

```
输入：tenancyId, meterReadings

前置校验：
  - tenancy.status == CHECKED_IN

编排（@Transactional）：
  1. tenancy.status → CHECKING_OUT
  2. meter.recordCheckOutReading(roomId, WATER, reading, tenancyId)
  3. meter.recordCheckOutReading(roomId, ELECTRICITY, reading, tenancyId)
  4. settlement.initiateCheckout(tenancyId, contractId, roomId, employeeId)
     → 异步结算，完成后回调 /internal/v1/contract/tenancy-settlement-complete
  5. 写 contract_operation_log(TENANT_CHECKOUT_INITIATED)

// 回调处理：tenancySettlementComplete(tenancyId)
  1. tenancy.status → CHECKED_OUT
  2. asset.releaseTenant(roomId, tenancyId)  → 若房间无其他 CHECKED_IN tenancy → PENDING_CHECKOUT
  3. iamPort.deleteTenantUser(tenancy.iamUserId)
  4. 检查 contract_room：若所有 tenancy 均 CHECKED_OUT → contractRoom.status = RELEASED
                         → asset.releaseRoom(roomId)  → AVAILABLE
  5. 若 contract.status == TERMINATING 且所有 contractRoom RELEASED
     → settlement.initiateContractTermination(contractId)
```

### 4.9 业务规则

| # | 规则 |
|---|---|
| CO-1 | 同一房间同时只能属于一份 status != RELEASED 的 contract_room（应用层校验 + 唯一索引） |
| CO-2 | 合同激活时（SIGNED→READY_FOR_ALLOCATION）为每个 contract_room 创建 ROOM_SHARED 账户 |
| CO-3 | 一个员工同时只能有一条 status IN (PENDING, CHECKED_IN, CHECKING_OUT) 的 tenancy |
| CO-4 | TERMINATING 状态禁止新增 tenancy（新入住） |
| CO-5 | 合同到期（end_date < today）不自动终止，由 schedule 检查后发送提醒，管理员手动发起 terminate |
| CO-6 | 押金账单由 contract 调用 Billing Service 生成，回调后自动激活合同（或等待手动激活） |
| CO-7 | 租金账单由 schedule 定时检查生成，写入 contract_rent_bill 追踪；逾期仅标记，不自动终止合同 |

---

## 5. Settlement Module

### 5.1 职责

计算退宿（个人）或退房（企业整体）的最终账目，处理欠费追缴和余额退款，生成结算账单后回调 contract 完成闭环。

### 5.2 数据模型

```xml
<!-- 007-create-settlement-tables.xml -->

<!-- 结算单主表 -->
<createTable tableName="settlement" remarks="结算单">
  <column name="id"                type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="settlement_no"     type="VARCHAR(32)" remarks="结算编号 STL-{yyyyMM}-{seq}">
    <constraints unique="true" nullable="false"/>
  </column>
  <column name="settlement_type"   type="VARCHAR(30)" remarks="TENANT_CHECKOUT | CONTRACT_TERMINATION"/>
  <column name="reference_id"      type="BIGINT" remarks="tenancy_id 或 contract_id"/>
  <column name="status"            type="VARCHAR(20)"
    remarks="CALCULATING|PENDING_PAYMENT|COMPLETED|FAILED"/>
  <column name="water_outstanding"       type="DECIMAL(12,2)" defaultValueNumeric="0" remarks="水费欠费"/>
  <column name="electricity_outstanding" type="DECIMAL(12,2)" defaultValueNumeric="0" remarks="电费欠费"/>
  <column name="account_surplus"         type="DECIMAL(12,2)" defaultValueNumeric="0" remarks="账户剩余退款"/>
  <column name="deposit_deduction"       type="DECIMAL(12,2)" defaultValueNumeric="0" remarks="押金抵扣"/>
  <column name="deposit_refund"          type="DECIMAL(12,2)" defaultValueNumeric="0" remarks="押金应退"/>
  <column name="settlement_bill_id"      type="BIGINT" remarks="追缴账单 ID（欠费时生成）"/>
  <column name="completed_at"      type="DATETIME(3)"/>
  <column name="created_by"        type="BIGINT"/>
  <column name="created_at"        type="DATETIME(3)"><constraints nullable="false"/></column>
  <column name="updated_at"        type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<!-- 结算明细表 -->
<createTable tableName="settlement_item" remarks="结算明细">
  <column name="id"             type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="settlement_id"  type="BIGINT"><constraints nullable="false"/></column>
  <column name="item_type"      type="VARCHAR(30)"
    remarks="WATER_ELECTRIC_OUTSTANDING|ACCOUNT_SURPLUS|DEPOSIT_DEDUCTION|DEPOSIT_REFUND"/>
  <column name="description"    type="VARCHAR(200)"/>
  <column name="amount"         type="DECIMAL(12,2)"/>
  <column name="direction"      type="VARCHAR(10)" remarks="CHARGE | REFUND"/>
  <column name="created_at"     type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>
```

### 5.3 Internal Interface

```java
// com.jugu.propertylease.main.settlement.api.SettlementCommandPort
public interface SettlementCommandPort {

    /**
     * 发起个人退宿结算（由 contract 编排退宿时调用）。
     * 结算为异步流程，完成后回调 /internal/v1/contract/tenancy-settlement-complete。
     */
    void initiateCheckout(InitiateCheckoutCommand cmd);

    /**
     * 发起合同退房结算（所有 tenancy 均已 CHECKED_OUT 后由 contract 调用）。
     * 负责押金结算、ROOM_SHARED 账户余额处理。
     */
    void initiateContractTermination(Long contractId);
}

record InitiateCheckoutCommand(
    Long tenancyId,
    Long contractId,
    Long contractRoomId,
    Long roomId,
    Long employeeId,
    Long tenantAccountId
) {}
```

### 5.4 退宿结算逻辑

```
initiateCheckout(cmd):

1. 创建 settlement 记录（status=CALCULATING）

2. 查询 meter.getUsageSummaryForTenancy(tenancyId)
   → 获取 outstanding（日结遗留欠费）

3. 查询 account.getAvailableBalance(tenantAccountId)
   → 获取 surplus（账户剩余余额）

4. 计算结果：
   - 若 outstanding > 0：
       account.freeze(tenantAccountId, min(surplus, outstanding))
       net_charge = outstanding - frozen_from_account
       若 net_charge > 0：
         调用 Billing Service 生成追缴账单（settlement_bill_id 记录）
         settlement.status = PENDING_PAYMENT（等待支付回调）
       else:
         account.deduct(tenantAccountId, outstanding)
         net_refund = surplus - outstanding
         若 net_refund > 0: account.refund(tenantAccountId, net_refund)
         account.closeAccount(tenantAccountId)
         settlement.status = COMPLETED
         回调 contract.tenancySettlementComplete(tenancyId)

   - 若 outstanding == 0 且 surplus > 0：
       account.refund(tenantAccountId, surplus)
       account.closeAccount(tenantAccountId)
       settlement.status = COMPLETED
       回调 contract.tenancySettlementComplete(tenancyId)

   - 若 outstanding == 0 且 surplus == 0：
       account.closeAccount(tenantAccountId)
       settlement.status = COMPLETED
       回调 contract.tenancySettlementComplete(tenancyId)

// 支付回调处理：settlementBillPaid(settlementId)
   account.deduct(frozen_amount)
   account.closeAccount(tenantAccountId)
   settlement.status = COMPLETED
   回调 contract.tenancySettlementComplete(tenancyId)
```

### 5.5 合同退房结算逻辑

```
initiateContractTermination(contractId):

1. 汇总所有 ROOM_SHARED 账户余额（全部退还企业）
2. 押金计算：deposit_amount - 欠费抵扣（如有未付租金账单）= 应退押金
3. 调用 Billing Service 生成最终退款账单
4. 关闭所有 ROOM_SHARED 账户
5. 更新 contract.status = TERMINATED
```

### 5.6 External API

```yaml
POST /api/settlement/settlements/query
  summary: 分页查询结算记录
  x-required-permission: settlement:read

GET /api/settlement/settlements/{id}
  summary: 结算单详情（含明细）
  x-required-permission: settlement:read

PUT /api/settlement/settlements/{id}/confirm
  summary: 人工确认结算完成（异常兜底，仅 FAILED 状态可操作）
  x-required-permission: settlement:write

# ── 内部接口 ──────────────────────────────────────────────────

POST /internal/v1/settlement/bill-paid
  summary: 结算追缴账单支付成功回调
  请求体: { settlementId, billId, paidAt }
```

---

## 6. Schedule Module

### 6.1 职责

纯触发器，不含任何业务逻辑。统一管理所有周期任务的执行时间和记录，调用其他模块的 `ScheduleTrigger` Interface 完成实际工作。

### 6.2 任务清单

| taskType | Cron | 触发目标 | 说明 |
|---|---|---|---|
| `DAILY_METER_SETTLEMENT` | `0 0 2 * * ?` | `MeterScheduleTrigger.triggerDailySettlement` | 每日 02:00 执行昨日水电日结 |
| `RENT_BILL_CHECK` | `0 0 3 * * ?` | `ContractScheduleTrigger.checkAndGenerateRentBills` | 每日 03:00 检查是否需生成新一期租金账单 |
| `CONTRACT_EXPIRY_CHECK` | `0 0 1 * * ?` | `ContractScheduleTrigger.checkContractExpiry` | 每日 01:00 检查合同到期，发送提醒 |

### 6.3 数据模型

```xml
<!-- 008-create-schedule-tables.xml -->

<createTable tableName="schedule_task_log" remarks="定时任务执行记录">
  <column name="id"             type="BIGINT" autoIncrement="true"><constraints primaryKey="true"/></column>
  <column name="task_type"      type="VARCHAR(50)"><constraints nullable="false"/></column>
  <column name="scheduled_date" type="DATE" remarks="本次任务处理的业务日期（非执行时间）"/>
  <column name="started_at"     type="DATETIME(3)"/>
  <column name="finished_at"    type="DATETIME(3)"/>
  <column name="status"         type="VARCHAR(20)" remarks="RUNNING|SUCCESS|PARTIAL_FAILURE|FAILED"/>
  <column name="total_count"    type="INT" defaultValueNumeric="0"/>
  <column name="success_count"  type="INT" defaultValueNumeric="0"/>
  <column name="failure_count"  type="INT" defaultValueNumeric="0"/>
  <column name="error_summary"  type="VARCHAR(2000)"/>
  <column name="created_at"     type="DATETIME(3)"><constraints nullable="false"/></column>
</createTable>

<addUniqueConstraint tableName="schedule_task_log"
  columnNames="task_type,scheduled_date"
  constraintName="uq_schedule_task_log_type_date"
  remarks="同类型同日期只执行一次，防重复触发"/>
```

### 6.4 External API

```yaml
POST /api/schedule/tasks/{taskType}/trigger
  summary: 人工手动触发任务（运维/补跑）
  x-required-permission: schedule:trigger
  请求体: { targetDate: date }   # 可指定业务日期，默认昨日

POST /api/schedule/tasks/query
  summary: 查询任务执行历史
  x-required-permission: schedule:read
```

### 6.5 防重与幂等

| 机制 | 说明 |
|---|---|
| DB 唯一约束 | `(task_type, scheduled_date)` 唯一，同日重复触发直接跳过 |
| 任务幂等 | `triggerDailySettlement(date)` 在 `meter_daily_settlement` 中已有 unique(room_id, date, type)，重跑安全 |
| 手动触发保护 | 若当日任务已 SUCCESS，手动触发需传 `force=true` 参数才执行 |

---

## 7. 附录 A：Asset Interface 约定

> asset 模块已实现，以下为 contract 模块需要的 Interface，asset 侧按此适配。

```java
// com.jugu.propertylease.main.asset.api.AssetCommandPort
public interface AssetCommandPort {
    /** 合同分配房间时调用，房间状态 AVAILABLE → ALLOCATED */
    void allocateRoom(Long roomId, Long contractId);

    /** 合同激活时调用，房间状态 ALLOCATED → PENDING_CHECKING */
    void activateRoomForOccupancy(Long roomId, Long contractId);

    /** 员工入住时调用，房间状态 PENDING_CHECKING → OCCUPIED */
    void assignTenant(Long roomId, Long tenancyId);

    /** 员工退宿后调用，若房间无其他活跃 tenancy → PENDING_CHECKOUT */
    void releaseTenant(Long roomId, Long tenancyId);

    /** 房间最终释放，所有 tenancy 退宿后 → AVAILABLE */
    void releaseRoom(Long roomId, Long contractId);
}

// com.jugu.propertylease.main.asset.api.AssetQueryPort
public interface AssetQueryPort {
    RoomInfo getRoomInfo(Long roomId);
    List<RoomInfo> getRoomsByIds(List<Long> roomIds);
    boolean isRoomAvailableForContract(Long roomId);  // status == AVAILABLE
    StoreInfo getStoreByRoom(Long roomId);             // meter 查单价时需要 storeId
}

record RoomInfo(Long id, Long storeId, Long buildingId, String roomNo, String status, String roomType) {}
record StoreInfo(Long id, String name) {}
```

---

## 8. 附录 B：Enterprise Interface 约定

> enterprise 模块已实现，以下为 contract 模块需要的 Interface。

```java
// com.jugu.propertylease.main.enterprise.api.EnterpriseQueryPort
public interface EnterpriseQueryPort {
    EnterpriseInfo getEnterprise(Long enterpriseId);
    EmployeeInfo getEmployee(Long employeeId);
    /** 校验员工是否属于该企业（入住前置校验） */
    boolean isEmployeeOfEnterprise(Long employeeId, Long enterpriseId);
}

record EnterpriseInfo(Long id, String name, String status) {}
record EmployeeInfo(Long id, Long enterpriseId, String name, String mobile, String status) {}
```

---

## 9. 附录 C：IAM Interface 约定（新增）

> IAM 模块需新增以下 Port，供 contract 编排入住/退宿时调用。

```java
// com.jugu.propertylease.main.iam.api.IamTenantPort
public interface IamTenantPort {

    /**
     * 入住时创建 TENANT 类型 IAM 用户。
     * 若该 employee 已有 IAM 用户则复用（幂等），返回 iamUserId。
     */
    Long createTenantUser(CreateTenantUserCommand cmd);

    /**
     * 退宿后删除 TENANT 用户（软删除，对应 IAM 现有 deleteUser 逻辑）。
     */
    void deleteTenantUser(Long iamUserId);
}

record CreateTenantUserCommand(
    Long   employeeId,
    Long   enterpriseId,
    String realName,
    String mobile       // 用于登录小程序
) {}
```

---

## 10. 附录 D：跨模块时序说明

### D.1 合同签约 → 激活

```
管理员                  Contract              Billing-Service
  │                        │                        │
  ├─POST /contracts──────→ │ (status=DRAFT)         │
  │                        │                        │
  ├─PUT  /sign──────────→  │ status→SIGNED          │
  │                        ├─createDepositBill()───→ │
  │                        │ ←─{ billId }───────────┤
  │                        │ deposit_bill_id=billId  │
  │                        │                        │
  │    企业付款             │                        │
  │                        │ ←─POST /internal/v1/contract/deposit-paid
  │                        │  status→READY_FOR_ALLOCATION
  │                        ├─account.openAccount()   │  (为每个 contractRoom)
  │                        │  ROOM_SHARED × N        │
```

### D.2 员工入住完整时序

```
管理员       Contract      Asset     IAM      Meter    Account
  │             │            │        │          │        │
  ├─POST /tenancies────────→ │        │          │        │
  │             ├────allocateRoom()──→ │          │        │
  │             ├────createTenantUser()────────→ │        │
  │             ├────recordCheckInReading()──────────────→│  (WATER)
  │             ├────recordCheckInReading()──────────────→│  (ELEC)
  │             ├────openAccount(TENANT)─────────────────────→│
  │             │  persist tenancy(CHECKED_IN)             │
  │             │  contract→IN_PROGRESS (if needed)        │
  │ ←─200 OK──  │            │        │          │        │
```

### D.3 水电日结时序

```
Schedule     Meter          Account(ROOM_SHARED)    Account(TENANT×N)
  │            │                    │                      │
  ├─trigger──→ │                    │                      │
  │            │ for each room:     │                      │
  │            ├─deduct(total)─────→│                      │
  │            │ ←─{deducted, shortfall}                   │
  │            │ if shortfall > 0:  │                      │
  │            ├─deduct(per_tenant/N)──────────────────────→│
  │            │ ←─{deducted, shortfall}──────────────────  │
  │            │ write meter_daily_settlement               │
  │ ←─result── │                    │                      │
```

### D.4 员工退宿完整时序

```
管理员    Contract    Meter    Settlement    Account    Billing    IAM    Asset
  │          │          │          │            │          │        │       │
  ├─PUT /checkout──────→│          │            │          │        │       │
  │          ├─recordCheckOut()───→│            │          │        │       │
  │          ├─initiateCheckout()──────────────→│          │        │       │
  │          │          │    getUsageSummary()──→│          │        │       │
  │          │          │    getBalance()────────────────→  │        │       │
  │          │          │    if outstanding>0:              │        │       │
  │          │          │      freeze()──────────────────→  │        │       │
  │          │          │      createSettlementBill()───────────────→│       │
  │          │          │                           (用户支付)         │       │
  │          │          │ ←─POST /internal/v1/settlement/bill-paid   │       │
  │          │          │    deduct()────────────────────→  │        │       │
  │          │          │    closeAccount()───────────────→ │        │       │
  │          │          │    status=COMPLETED               │        │       │
  │          │ ←─POST /internal/v1/contract/tenancy-settlement-complete
  │          │  tenancy→CHECKED_OUT                         │        │       │
  │          ├─releaseTenant()──────────────────────────────────────────────→│
  │          ├─deleteTenantUser()────────────────────────────────────→│      │
  │          │  (若房间无其他tenancy → releaseRoom)                    │      │
  │ ←─200 OK │          │          │            │          │        │       │
```

---

*文档生成时间：2026-05 | 下一步：逐模块细化 OpenAPI yaml 和 Liquibase XML*
