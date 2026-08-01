# 入住门锁密码 / 换宿授权 / 换表结算 闭环 Spec v1

日期：2026-07-29
状态：待确认

## 1. 背景与范围

三个此前未闭环的业务点，本轮全部在 **main-service 仓库内**闭环（device-service 不在本仓库，
门锁/电表硬件交互继续走 Port + stub，契约就绪后平替 Http 实现，业务代码不变）：

| # | 事项 | 闭环定义 |
|---|------|---------|
| 1 | 入住门锁密码 | 办理入住时生成门锁密码并与该入住（stay）绑定，加密落库；租客本人通过小程序端 API 查询明文 |
| 2 | 换宿授权 | 换宿时旧房密码作废、新房生成新密码（复用 #1 同一套机制）；退宿时密码作废 |
| 3 | 换表结算 | 换表/解绑时以旧表最终读数对旧绑定做一次期末结算（现行电价+分摊+扣款链路），房间账户结构不变；新表从初始读数继续每日结算 |

**非目标**（明确不做）：
- 密码主动通知（短信/推送/小程序消息）——租客自行查询即可
- 换房（多人/合同级房间调换）——只有个人换宿
- 管理台查看密码明文 / 密码重置入口
- device-service 真实下发/回收（stub 仅记日志）
- 密码有效期/错误次数等安全策略

## 2. 已确认决策

1. 密码**本地生成闭环**：8 位数字（SecureRandom），AES-GCM 加密落库，查询时解密；
   `DoorLockPort` 保持"下发/回收"语义，stub 记日志，未来 HttpDoorLockPort 把密码同步到门锁硬件。
2. 换表/解绑时旧表**最终读数必填**，且必须 ≥ 该绑定已有最新读数（含绑定底数），否则 400。

## 3. 门锁密码设计

### 3.1 数据模型（changelog 019）

新表 `door_credential`（沿用 015 occupancy 表 snake_case 约定）：

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK auto-inc | |
| stay_id | BIGINT NOT NULL | stay.id，索引 |
| room_id | BIGINT NOT NULL | room_info.RoomId |
| tenant_id | BIGINT NOT NULL | customer.employee.id |
| iam_user_id | BIGINT NOT NULL | stay.iam_user_id（冗余，查询免 join） |
| password_enc | VARCHAR(256) NOT NULL | AES-GCM（Base64: iv‖cipher‖tag） |
| status | VARCHAR(16) NOT NULL | ACTIVE / REVOKED |
| created_at / revoked_at | DATETIME(3) | revoked_at 可空 |

约束：`stay_id` 普通索引；**同一 stay 最多一条 ACTIVE**（应用层保证：生成前把该 stay 旧记录置 REVOKED）。

### 3.2 密码生成与加密

- 生成：8 位数字，`SecureRandom`，不排除前导零（作为字符串处理）。
- 加密：AES-256-GCM，密钥来自配置 `occupancy.door-credential.aes-key`（Base64 32 字节）；
  reale2e/local profile 内置开发默认值，生产由部署注入。加解密工具类放 common 模块。
- 明文只出现在两个瞬间：生成后交给 `DoorLockPort.issueCredential(...)` 入参、租客查询解密返回。
  不落日志、不进管理台响应。

### 3.3 DoorLockPort 语义调整

```java
/** 入住/换宿：下发新密码到门锁（stub 仅记日志，真硬件由 Http 实现同步）。*/
void issueCredential(Long tenantId, Long roomId, Long stayId, String plainPassword);

/** 退宿/换宿：注销门锁密码。*/
void revokeCredential(Long tenantId, Long roomId, Long stayId);
```

密码生成/落库在 occupancy Service 层完成，Port 只负责"通知门锁"。
CheckInService / CheckOutService / TransferService 现有调用点同步适配。

### 3.4 业务流

- **入住**：CheckInService 在 stay 创建、`iamTenantPort.createOrEnableTenantUser` 之后：
  生成密码 → 写 `door_credential`(ACTIVE) → `issueCredential`。
- **退宿**：该 stay 的 ACTIVE 记录置 REVOKED → `revokeCredential`。
- **换宿**：旧 stay 记录 REVOKED + `revokeCredential`（旧房）；新 stay 生成新密码 ACTIVE +
  `issueCredential`（新房）。即 TransferService 现有两处调用点之间补齐落库逻辑。

### 3.5 租客查询 API（小程序）

- `GET /occupancy/my/door-password`，TENANT 用户 JWT（`/auth/wechat/miniprogram/login` 签发）。
- 鉴权：无权限码，**按 JWT userType=TENANT 放行**（delegate 校验 principal 类型，非 TENANT 403）。
- 解析链：JWT userId → `door_credential.iam_user_id` + status=ACTIVE（可多条，历史 stay 已 REVOKED）
  → 逐条解密返回：
  ```json
  { "items": [ { "stayId": 1, "roomId": 33, "password": "12345678", "checkInAt": "..." } ] }
  ```
- 无 ACTIVE 记录 → `items: []`（200，不报错）。
- occupancy-external.yaml 增加该端点（Tag: occupancy-my，不带 x-required-permission，
  文档注明 TENANT-only）。

## 4. 换表结算设计

### 4.1 API 变更（metering-external.yaml）

- `POST /metering/bindings`（bindDevice，权限 metering:binding:write 不变）：
  请求增加可选 `oldFinalReading`。**当该房间+表类型已存在 ACTIVE 绑定时（即换表）必填**，否则 400；
  首次绑表不允许传（400）。
- `POST /metering/bindings/unbind`：请求体增加必填 `finalReading`。
- 校验：`finalReading ≥ 该绑定最新读数（无读数则 ≥ 绑定底数）`，否则 400 `METER_FINAL_READING_INVALID`。

### 4.2 期末结算逻辑（MeteringService）

换表/解绑共用私有方法 `settleBindingFinal(binding, finalReading)`：

1. 起始读数 = 该房间+表类型最新读数，无则绑定底数（与日结 `settleOneRoom` 同源，保证不重复不漏）；
2. 用量 = finalReading − 起始读数（校验后必然 ≥ 0）；
3. 金额 = 用量 × 当日有效电价（`findEffectivePrice`，无单价按 0 并照常出结算记录）；
4. 分摊与扣款：**完全复用日结链路**——企业子余额优先、余量在住租客均摊（尾差末位承担），
   调 `accountingCommandPort.deductForDailySettlement`；
5. 落账：`room_daily_charge` 写一条 date=当天 的记录（SETTLED/PARTIAL 语义同日结），
   分摊明细写 `tenant_apportionment`；`meter_reading` 写一条 anchorType=`REPLACE_FINAL` 的锚点读数
   （归属旧 binding），标记结算边界；
6. 然后才关闭旧绑定（换表场景再开新绑定，initialReading 为新表底数）。

幂等与防重：
- 日结只查 ACTIVE 绑定，旧绑定关闭后不会再被日结，不会重复扣；
- 同一绑定重复换表/解绑请求：绑定已非 ACTIVE → 按现有 404/400 处理，不会二次结算；
- `room_daily_charge` 有 `uq_rdc_room_date` 唯一约束：当日日结已完成后换表/解绑直接拒绝
  （409 `METER_REPLACE_AFTER_DAILY_SETTLEMENT`，提示次日再操作），避免同日两条 charge 或重复扣款；
- 期末结算复用日结链路时，被换表类型以 `finalReading` 为截止读数，**其余表类型按日结规则
  一并结清**，因此当晚日结幂等跳过该房间后不会漏结；
- 金额=0 也照常写 charge 记录（用量 0，状态 SETTLED），保证审计完整。

房间账户**不做任何结构变更**：只发生扣款流水，子账户数量与归属保持不变。

### 4.3 对管理台前端的影响

- 绑表弹窗：若该房间+表类型已有绑定（换表），增加"旧表最终读数"必填输入；
- 解绑操作：弹窗要求输入"最终读数"；
- 抄表与日结页：日结记录来源展示（日结/换表结算）——可后置，本轮仅后端。

## 5. 权限与配置

| 项 | 值 |
|----|----|
| 换表/解绑 | 沿用 `metering:binding:write` |
| 租客查密码 | TENANT userType 放行，无权限码 |
| 新配置 | `occupancy.door-credential.aes-key`（Base64 32B），reale2e/local 给开发默认值 |

## 6. 数据变更清单

- changelog `019-create-door-credential.xml`：新表 `door_credential` + 索引
- `meter_reading.anchor_type` 新增取值 `REPLACE_FINAL`（字符串列，无需 DDL，仅约定）

## 7. 验证

1. UT：
   - 加解密往返、错误密钥解密失败；
   - 换表结算：用量/金额/分摊正确、finalReading 小于已有读数 400、账户结构不变；
   - 入住/换宿/退宿后 credential 状态流转正确。
2. reale2e 冒烟：
   - 入住 → TENANT 登录（小程序接口）→ 查到 8 位数字密码；退宿 → 查不到；
   - 换宿 → 旧密码失效、新密码可查且不同；
   - 录入读数 → 换表（带 finalReading）→ charge 金额=（final−上次读数）×电价、
     账户扣款流水生成、次日日结从 newInitial 起算不重复。
