# 入住门锁密码 / 换宿授权 / 换表结算 闭环 — 执行 Plan

日期：2026-07-30
Spec：`docs/checkin-doorpwd-meterreplace-spec-v1.md`（已确认：本地生成闭环、最终读数必填）

## 探查结论（已验证）

- jOOQ forcedType 已有 `(?i:PUBLIC\..*\..*(_AT|_TIME))` 规则，`door_credential.created_at/revoked_at`
  自动映射 OffsetDateTime，无需改 starter pom；
- occupancy 表（015）snake_case、无 IsDeleted、状态字段表达生命周期 —— door_credential 沿用；
- `stay.iam_user_id` 已存在（checkIn 时回写），door_credential 冗余该列，租客查询免 join；
- 换表入口即 `MeteringService.bindDevice`（存在 ACTIVE 旧绑定时自动关旧开新）与 `unbindDevice`；
- 日结 `settleOneRoom` 的分摊扣款链路可抽取复用：起始读数=最新读数或绑定底数、
  企业优先+租客均摊（尾差末位）、`accountingCommandPort.deductForDailySettlement`；
- TENANT 登录走 `/auth/wechat/miniprogram/login`，JWT 内含 userType（实现时在 delegate 校验）。

## 实施步骤

### Step 1 — changelog 019 + 编译
`019-create-door-credential.xml`（已建）。`mvn -pl main-service -am compile -DskipTests -o`
确认 jOOQ 生成 DoorCredential 表类。

### Step 2 — AES-GCM 工具 + 配置（P2）
- common 模块新建 `crypto/AesGcmCipher`：`encrypt(plain)→Base64(iv‖cipher‖tag)` / `decrypt`；
  key 为 32 字节 Base64；
- main-service 配置 `occupancy.door-credential.aes-key`（base/local/reale2e 给开发默认值，
  yml 注释注明生产必须注入覆盖）。

### Step 3 — 门锁凭证流转（P3）
- `DoorLockPort.issueCredential` 增加 `String plainPassword` 入参；stub 日志打掩码（不打明文）；
- occupancy 新建 `DoorCredentialService`（DSLContext 直用，沿用 PropertymgrService 模式）：
  `issueForStay(stay)`（置旧 REVOKED → 生成 8 位数字 → 加密落库 ACTIVE → port.issueCredential）、
  `revokeForStay(stayId)`（ACTIVE→REVOKED + port.revokeCredential）、
  `findActivePasswordsByIamUser(iamUserId)`（解密返回）；
- 适配 CheckInService（issue）、CheckOutService（revoke）、TransferService（旧 revoke+新 issue）；
  现有三处 port 调用点替换为 DoorCredentialService 调用。

### Step 4 — 租客查询 API（P4）
- occupancy-external.yaml：`GET /occupancy/my/door-password`，Tag occupancy-my，
  无 x-required-permission，文档注明 TENANT-only；响应 `DoorPasswordResult{items:[{stayId,roomId,password,checkInAt}]}`；
- delegate：从 SecurityContext 取 principal，校验 userType=TENANT（非 TENANT → 403）；
  按 iamUserId 查 ACTIVE 记录解密返回。

### Step 5 — 换表/解绑期末结算（P5）
- metering-external.yaml：`BindDeviceRequest` 加 `oldFinalReading`（可选；换表时业务必填）、
  `UnbindDeviceRequest` 加 `finalReading`（必填）；错误码 `METER_FINAL_READING_INVALID`；
- MeteringService：
  - `settleBindingFinal(binding, finalReading)` 私有方法：起始读数=最新读数或底数 →
    用量×当日电价 → 复用日结分摊扣款 → 写 room_daily_charge（date=今天）+ tenant_apportionment +
    meter_reading(anchorType=REPLACE_FINAL，挂旧 binding)；
  - bindDevice：存在旧绑定 → oldFinalReading 必填校验 + settleBindingFinal 后关旧开新；
    无旧绑定传了 oldFinalReading → 400；
  - unbindDevice：finalReading 校验（≥最新读数/底数，否则 400）+ settleBindingFinal 后关闭；
  - 校验失败/金额为 0 的边界：金额 0 照常写 SETTLED 记录。

### Step 6 — UT + 冒烟（P6）
- UT：AesGcmCipher 往返/错钥失败；DoorCredentialService 状态流转（mock port/repo 层用 H2 或 Mockito
  视现有测试风格）；settleBindingFinal 金额/分摊/校验 400；
- `mvn -pl main-service -am install -o` 全量 + 单测；
- reale2e 冒烟：入住→（模拟 TENANT JWT）查密码 8 位数字；退宿→空；换宿→新密码且不同；
  换表带 finalReading→charge 金额正确、账户流水生成、次日日结不重复。

### Step 7 — 提交（P7）
backend 单 commit（含 docs spec+plan）；前端换表弹窗输入项后置（spec 已定本轮仅后端）。

## 风险

| 风险 | 缓解 |
|------|------|
| TENANT JWT 的 principal 结构未验证 | Step 4 实现时先读 AuthService 签发逻辑确认 claim 名 |
| 换表结算与日结同日并发重复扣 | 结算起始读数=最新读数（日结总是结到最新），REPLACE_FINAL 锚点防重；冒烟覆盖 |
| aes-key 缺失导致启动失败 | 默认值内置开发 profile；生产配置缺失时启动即报错（fail-fast 优于运行期 500） |
