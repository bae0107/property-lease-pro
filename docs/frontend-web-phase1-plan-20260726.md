# frontend/web Phase 1 plan：后端 gap 补齐

> 日期：2026-07-26
> 对应 spec：`docs/frontend-web-dev-spec-v1.md`
> 状态：**已执行完毕**（执行结果与偏差见 spec §7 Phase 1 完成记录；
> commits：be78827 gateway 修复 / 2798e66 reale2e+manifest / 6f0602e propertymgr+续租+mock-pay）

## Context

frontend/web 管理台 spec（已确认）要求真实登录链路：浏览器 → gateway(8080) → main-service(8081, `security.mode=service`)。Phase 1 补齐 5 个后端 gap，让 Phase 2/3 的前端有完整可用的 API。

探查结论（已验证）：
- gateway 在 root pom `<modules>` 中，依赖 `main-client`（需 `-am` 构建），`RouteConfig` fail-fast 要求三个服务 url（local yml 已配齐，billing/device 不用真启动）；
- main-service 基座 `application.yml` 默认 `security.mode: service`，local profile 覆盖为 mock；
- 权限检查纯 JWT claim（`@PreAuthorize hasPermission`），登录时按角色 join 权限表签发；
- **风险**：构建生成的 `permissions-manifest.json` 中 `ROLE_IAM_ADMIN` 权限数为 **0**（可能是 stale 构建产物）——真实模式下 iam_admin 可能无任何权限，必须先验证；
- propertymgr 模块只有 `AssetQueryPort`/`AssetCommandPort`（无 insert/list），impl 直接用 `DSLContext` 操作 `BUILDING_INFO`/`ROOM_INFO`（jOOQ 表已生成）；
- `ContractLifecycleService.createDraft(CreateDraftCommand)` 内含房间占用校验（`assetQueryPort.isRoomAvailableForContract` + `repo.existsActiveContractRoom`），续租同合同房间需排除源合同；
- `AccountingService.handleBillPaid(billingServiceBillId, amount, paidAt)` 幂等，`BillRepository.findBillById` 可按内部 id 查。

## 实施步骤

### Step 1 — G5 gateway 编译验证（风险前置）
```bash
cd backend && mvn -pl gateway -am install -DskipTests -o
```
- 若编译失败按 main-service 经验修复；
- 编译过后先跑一次 gateway 单测（`mvn -pl gateway test -o`），失败则评估修复或记录跳过原因。

### Step 2 — G4 reale2e profile + 登录链路冒烟
新建 `main-service/src/main/resources/application-reale2e.yml`（自包含，不与 local 叠加，避免 mock 配置残留）：
- 复制 local 的 H2 数据源（`PROPERTY_LEASE_MAIN_V2`）、jooq h2 dialect、redis 排除、cache simple、`iam.bootstrap...admin123`；
- `security.mode: service`；jwt user/service secret 与 `gateway/application-local.yml` **逐字一致**；
- `security.permit-paths`：`/auth/**`、swagger、`/v3/api-docs/**`、`/dev/**`（注意 profile 会整体替换基座的 list，需写全）；
- **不要** mock-user 段。

冒烟：
1. `mvn -pl main-service spring-boot:run -o -Dspring-boot.run.profiles=reale2e`；
2. `mvn -pl gateway spring-boot:run -o`（local profile 默认）；**注意 TaskStop 只杀 mvn wrapper，需 taskkill 残留 java.exe**；
3. `POST localhost:8080/api/main-service/auth/login/password` `{username:"iam_admin",password:"admin123"}` → 期望 200 + token；
4. 带 token `POST /api/main-service/iam/users/query` → 期望 200；
5. 若 403：解码 token 检查 permissions 为空 → 重建后看 `target/classes/iam/permissions-manifest.json` 的 `ROLE_IAM_ADMIN`；确认为空则在 `iam-external.yaml` 全部带 `x-required-permission` 的操作上补 `x-builtin-roles: IAM_ADMIN`（遵循 updateRole 已有先例，标量/逗号分隔均支持），重建再验。

### Step 3 — G1 propertymgr 外部 API
1. 新建 `main-service/src/main/resources/openapi/propertymgr-external.yaml`：
   - `POST /propertymgr/buildings`（创建：buildingId 业务编号 VARCHAR(30) 主键由请求方给、storeId、buildingName）；
   - `POST /propertymgr/buildings/query`（分页，PageRequest 继承，filter：buildingName 模糊）；
   - `POST /propertymgr/rooms`（创建：buildingId、level、roomNum、livingNum；RoomStatus 后端写死 `EMPTY`）；
   - `POST /propertymgr/rooms/query`（分页，filter：buildingId、roomStatus）；
   - 权限码：`propertymgr:building:read/write`、`propertymgr:room:read/write`；错误码/分页模型参照 customer-external.yaml；
2. `main-service/pom.xml`：复制 `customer-external` execution 块改 id/inputSpec/output 为 propertymgr；build-helper `add-generated-sources` 加一行 source 目录；
3. 新建 `propertymgr/service/PropertymgrService.java`（沿用 AssetQueryPortImpl 的 DSLContext 直用模式）：insertBuilding（查重 409）、insertRoom（校验楼栋存在 404、同楼栋 roomNum 查重 409）、findBuildings/countBuildings、findRooms/countRooms；**注意 `IsDeleted=1` 表示未删除的历史约定**；
4. 新建 `propertymgr/delegate/PropertymgrBuildingsApiDelegateImpl.java` + `PropertymgrRoomsApiDelegateImpl.java`（参照 customer delegate 模式）；
5. 编译（`mvn -pl main-service -am compile -DskipTests -o`）→ 重启 reale2e → curl 冒烟：建楼栋 → 建房间 → query 两表；manifest 应自动新增 4 个权限码。

### Step 4 — G2 合同续租
1. `contract-external.yaml` 加 `POST /contract/contracts/{id}/renew`：
   - 请求 `RenewContractRequest`：startDate*/endDate*/paymentMode*/rooms*/chargeRules/remark（即 CreateContractRequest 去掉 enterpriseId）；
   - 响应 200 = ContractDetail；404 合同不存在；409 状态不允许/房间冲突；
   - `x-required-permission: contract:contract:write`；
2. 新 changelog `017-add-contract-renewed-from.xml`：contract 表加 `renewed_from_contract_id BIGINT NULL`；确认 master changelog 是通配 include 还是需手工登记；
3. `ContractLifecycleService`：
   - `renewContract(Long sourceId, RenewCommand cmd)`：源合同状态须 ∈ {READY_FOR_CHECK_IN, PARTIALLY_RETURNED}；cmd.startDate ≥ 源 endDate（允许等于）；enterpriseId 强制取源合同；
   - 房间占用校验：与 createDraft 同款，但**豁免源合同自身 ACTIVE 房间**（续租同房场景）——把 createDraft 的校验抽成私有重载 `validateRoomsAvailable(rooms, excludeContractId)`，createDraft 传 null；
   - 插入复用 repo.insertContract/insertContractRoom/insertChargeRule，写入 renewed_from_contract_id；
4. `ContractContractsApiDelegateImpl` 加 renew 委托；
5. UT `ContractLifecycleServiceRenewContractTest`（沿用现有 Mockito 风格）：非法状态拒绝、起租日早于源结束日拒绝、同房续租放行、他合同占用房拒绝、成功生成 DRAFT 且 enterpriseId 继承。

### Step 5 — G3 mock-pay（reale2e 限定）
新建 `accounting/delegate/DevMockPayController.java`：
- `@RestController @Profile("reale2e")`，`POST /dev/bills/{billId}/mock-pay`；
- 注入 `BillRepository` + `AccountingService`：`findBillById` → 404；已 PAID 直接 200（幂等）；否则 `handleBillPaid(bill.getBillingServiceBillId(), bill.getTotalAmount(), OffsetDateTime.now())`；
- 返回 `{billId, billStatus:"PAID"}`；
- 冒烟小闭环：reale2e 下走 建企业→合同→confirm→bills/query 拿 billId→mock-pay→合同 READY_FOR_CHECK_IN（顺带验证 gateway 全链路）。

### Step 6 — 收尾
- 全量编译 + 单测：`mvn -pl main-service -am test -o`；
- 提交（gateway 修复 / reale2e+manifest / propertymgr / renew / mock-pay 可分 2-3 个 commit）；
- 更新 `docs/frontend-web-dev-spec-v1.md`（标注 Phase 1 完成项与任何偏离）。

## 关键文件

| 动作 | 文件 |
|---|---|
| 新建 | `main-service/src/main/resources/application-reale2e.yml` |
| 新建 | `main-service/src/main/resources/openapi/propertymgr-external.yaml` |
| 修改 | `main-service/pom.xml`（openapi execution + build-helper source） |
| 新建 | `propertymgr/service/PropertymgrService.java`、`propertymgr/delegate/Propertymgr*ApiDelegateImpl.java` |
| 修改 | `contract-external.yaml`、`ContractLifecycleService.java`、`ContractContractsApiDelegateImpl.java` |
| 新建 | `db/changelog/changes/017-add-contract-renewed-from.xml`、`accounting/delegate/DevMockPayController.java` |
| 条件修改 | `iam-external.yaml`（仅当 ROLE_IAM_ADMIN 权限确认为空时补 `x-builtin-roles`） |

## 验证

1. gateway + main-service(reale2e) 双进程启动；
2. curl 链路：login 拿 token → iam/users/query 200 → 建楼栋/房间 200 → 建企业/合同/confirm → mock-pay → 合同 READY_FOR_CHECK_IN → renew 生成新 DRAFT 合同；
3. `mvn -pl main-service -am test -o` 全绿（含新增 renew UT）；
4. 不带 token 调业务接口 401、token 权限不足 403（权限链真实生效）。

## 执行偏差记录（2026-07-26 补）

- Step 2 冒烟发现计划外 bug：`SecurityHeaderCleanFilter` 为 GlobalFilter，把认证过滤器刚铸入的 `X-Service-Token` 剥掉（WebFilter 链整体先于 GlobalFilter 链执行）→ 改为 WebFilter（commit be78827）；
- Step 4 偏差：占用豁免未抽共享 `validateRoomsAvailable`，直接在 renewContract 内用 `findRoomByContractAndRoom` 判断源合同自身 ACTIVE 占用后跳过（createDraft 零改动）；
- Step 5 偏差：项目未开 `-parameters`，`@PathVariable` 必须显式写名字（`@PathVariable("billId")`）。
