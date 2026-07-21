# main-service 编译修复与 P0/P1 功能补齐实施记录

> 日期：2026-07-21
> 范围：仅 `backend/main-service`
> 设计依据：`docs/architecture/system-architecture-spec-v11.md`（总体）+ `docs/temp/main-service-dev-spec-v5.md`（模块细节）；**代码与文档冲突时以代码为准**。

## 1. 背景与流程

分支代码处于散乱状态，按以下顺序推进：

1. 通读 docs，识别最新文档，只聚焦 main-service；
2. 先修复 main-service 编译（6 轮编译迭代后 BUILD SUCCESS，532 个源文件）；
3. 评估代码 vs 文档差异，列出功能 gap；
4. 与用户确认本轮实施范围 = **P0 + P1**（P2 顺延）；
5. 实施并逐项验证；
6. 最终 `mvn -pl main-service -am compile -DskipTests -o` 通过（BUILD SUCCESS）。

## 2. 编译阶段的关键问题与修复

| 问题 | 修复 |
|---|---|
| accounting 6 个 repo 接口 package-private，`repo.jooq` 子包无法实现 | 拆为 6 个 public 接口文件（RoomAccountRepository 等） |
| jOOQ forcedType 只匹配 `*_AT`，`reading_time`/`anchor_time` 及 016 资产表旧风格 camelCase `*Time` 列映射成 LocalDateTime | forcedType 正则扩为 `(?i:PUBLIC\..*\..*(_AT\|_TIME))`，另加 `(?i:PUBLIC\.(ROOM_INFO\|BUILDING_INFO)\..*TIME)`，均 → OffsetDateTime |
| 016 资产表（building_info/room_info）缺失 | 新建 changelog：沿用旧 camelCase 列名、只建 2 张表、StoreId BIGINT（用户确认） |
| 单独 `-pl main-service` 命中 ~/.m2 过期上游 jar，出现幽灵构造器错误 | **必须 `-am`** 从源码重建上游 |
| 本机 `sed -i` 截断过 ScheduleTasksApiDelegateImpl.java | 改文件一律用编辑工具，禁用 sed -i |
| openapi-generator 生成模型为独立类（无 allOf 继承），分页字段内联 pageNo/pageSize/total(Long) | 各 Delegate 统一 `.pageNo()/.pageSize()/.total((long) total)`；**例外**：contract 模块的查询 Request 模型用内联 `page/size`（getPage/getSize） |
| `AuthUserContext.currentUserId()` 不存在 | 全部改为 `CurrentUser.getCurrentUserId()`（security-starter） |

## 3. P0 修复（阻塞主流程）

### P0-1 启用定时任务
- `MainServiceApplication` 加 `@EnableScheduling`。此前 `@Scheduled` 全部静默不执行。

### P0-2 日结企业分摊恒为 0（resolveEnterpriseId 恒 null）
- `MeteringService` 注入 `ContractQueryPort`，`resolveEnterpriseId` 改为取任一在住 Stay 的 contractId 反查 `enterpriseId()`；无在住返回 null（费用由租客分摊）。
- 配套修复 `AccountingRechargeApiDelegateImpl.initiateRecharge`：spec 规定 `room_account_sub_balance.owner_id = enterprise_id 或 tenant_id`，原代码 ENTERPRISE 充值错用 `currentContractId`。现改为 `payerId != null ? payerId : contractQueryPort.getContract(acc.getCurrentContractId()).enterpriseId()`。
- 顺带修复**启动级循环依赖**（此前应用无法启动）：
  `ContractLifecycleService → AccountingCommandPort(AccountingService) → ContractCallbackPort(ContractCallbackPortImpl) → ContractLifecycleService`。
  在 `ContractCallbackPortImpl` 构造器参数上加 `@Lazy` 打破。

### P0-3 settleFullReturn 空转
- `RoomAccountRepository` + jOOQ 实现新增 `findActiveByContractId(contractId)`（CURRENT_CONTRACT_ID 匹配且 STATUS=ACTIVE）；
- `AccountingService.findActiveAccountsByContract` 由原 placeholder（恒返回空 List）改为真实查询。

### P0-4 handleBillPaid 缺 SETTLEMENT_BILL 路由
- `AccountingService.handleBillPaid` 新增 `SETTLEMENT_BILL` 分支：标记欠费处理完成，`contractId != null` 时回调 `ContractCallbackPort.onSettlementCompleted`（markCompleted 内部幂等）。
- `StubBillingServicePort.createBill` 原返回 `billingServiceBillId=null`，导致 billPaidCallback 无法按 `billing_service_bill_id` 查到账单、集成测试无法模拟支付。现返回时间戳种子递增的合成 ID 并打 WARN 日志。

## 4. P1 修复（对齐 spec）

### P1-5 applyPartialReturn 状态机（spec 7.6 / 7.2）
- 允许从 `READY_FOR_CHECK_IN` **或** `PARTIALLY_RETURNED` 发起部分退房（原仅前者，与 spec "仍可入住未退房间/继续退" 矛盾）；
- 退完后检查剩余 ACTIVE 房间：有剩余 → `PARTIALLY_RETURNED`；**全部退完 → 自动触发整体结算**（用户确认的决策）：`FULLY_RETURNED → SETTLING → settleFullReturn（退企业押金）→ COMPLETED`（stub 同步，同 applyFullReturn 注释）。
  - 若不自动触发，企业押金只会在 settleFullReturn 退还，而 applyFullReturn 不接受 FULLY_RETURNED 入口 → 押金死锁。
- 新增 `STATUS_FULLY_RETURNED` 常量，更新类级状态机 javadoc。

### P1-6 settlePartialReturn 欠费追缴（spec 10.2）
- 欠费来源：日结 `deductForDailySettlement` 对扣不足部分写 `INSUFFICIENT` 负流水；
- 新增 `RoomAccountEntryRepository.sumInsufficientByOwner(roomAccountId)`（jOOQ group by owner，sum(-amount)），返回新 record `OwnerArrears(ownerType, ownerId, amount)`；
- `settleRoomReturn` 在退余额后、关户前，为每个欠费方生成 `SETTLEMENT_BILL`（billNo 前缀 STL，TENANT 时回写 tenant_id），并调用 `billingServicePort.createBill`。

### P1-7 押金台账详情全表扫描
- `DepositLedgerRepository` 新增 `findLedgerById(id)`（命名避开 `RoomAccountRepository.findById` 同实现类返回类型冲突——编译验证发现）；
- `AccountingQueryService.getDepositLedgerById` 由 `findAll + 内存 filter` 改为按主键查询。

## 5. 用户确认的决策记录

| 决策点 | 结论 |
|---|---|
| 016 资产表列名风格 | 沿用旧风格 camelCase |
| 016 建表范围 | 只建 building_info + room_info 2 张 |
| StoreId 类型 | BIGINT |
| 本轮实施范围 | P0 + P1（P2 顺延） |
| 部分退房退完全部房间 | 自动触发整体结算（ FULLY_RETURNED → SETTLING → COMPLETED ） |

## 6. 遗留 P2（下一轮）

1. **月租账单**：`ContractScheduleTrigger.checkAndGenerateRentBills` 未实现（MONTHLY_RENT 账单生成）；
2. `queryRoomAccounts` 分页仍是空页 stub（RoomAccountRepository 缺分页列表查询）；
3. `cancelBill` 缺状态校验（仅 PENDING 可取消）；
4. 换宿余额迁移（TRANSFER_OUT / TRANSFER_IN）链路未端到端验证；
5. billing-service / device-service 仍为 log-only stub，待真实 OpenAPI 规范后替换。

## 7. 单元测试（2026-07-21 补）

沿用项目既有风格（JUnit5 + Mockito + AssertJ 纯 mock 单测，不起 Spring 上下文），覆盖本轮风险最高的两处：

- `AccountingServiceSettleRoomReturnTest`（3 例）：欠费追缴生成 SETTLEMENT_BILL + 余额退还 + 关户；无欠费零余额只关户；无账户空转
- `ContractLifecycleServiceApplyPartialReturnTest`（4 例）：部分退剩余→PARTIALLY_RETURNED 且不触发整体结算；退完全部→FULLY_RETURNED→SETTLING→COMPLETED 顺序验证；非法状态拒绝；房间有在住租客拒绝

验证：`mvn -pl main-service -am test -Dtest='...' -o` → Tests run: 7, Failures: 0, Errors: 0。

## 8. 构建命令（本机）

```bash
export JAVA_HOME="/c/Users/A/.jdks/corretto-20.0.2.1"
export PATH="$JAVA_HOME/bin:/c/Users/A/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin:$PATH"
cd /d/workdir/github/property-lease-pro/backend
mvn -pl main-service -am compile -DskipTests -o   # -am 必须，离线 -o 可用
```
