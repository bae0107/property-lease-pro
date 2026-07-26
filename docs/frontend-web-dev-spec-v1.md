# frontend/web 管理台开发 spec v1

> 日期：2026-07-25
> 范围：`frontend/web`（新建）+ `backend/main-service` 与 `backend/gateway` 的最小补齐
> 目标：基于 main-service 现有能力，提供一个**可用即可**（不追求美观）的 Web 管理台，把后端能力映射成完整业务操作流，覆盖：Admin 登录 → 建 STAFF → 房屋管理 → 企业/员工录入 → 合同全生命周期（含续租、部分/全部退房）→ 员工分配/入职/退宿/换宿 → 账单/押金/充值/抄表/日结。

## 1. 用户确认的决策

| 决策点 | 结论 |
|---|---|
| 登录链路 | **真实链路**：gateway(8080) + main-service 切 `security.mode=service`，前端走 `/api/main-service/**`，iam_admin 真实登录拿 User JWT |
| 房屋管理 | **后端补 propertymgr 外部 API**（楼栋/房间 列表+创建），前端真实操作 |
| 续租 | **后端补续租端点** `POST /contract/contracts/{id}/renew` |
| 前端技术栈 | **原生 HTML + JS**，无框架无构建，hash 路由 SPA-lite |

## 2. 总体架构与请求链路

```
浏览器 (localhost:3000, python -m http.server 静态托管 frontend/web)
   │  Authorization: Bearer <User JWT>
   ▼
gateway (8080, security.mode=gateway)
   │  校验 User JWT → 铸 Service JWT（携带 userId+permissions）
   │  路由 /api/main-service/** → localhost:8081，StripPrefix
   ▼
main-service (8081, security.mode=service)
   │  ServletServiceJwtFilter 校验 Service JWT → @PreAuthorize 权限拦截
   ▼
业务模块
```

- 登录请求（无 token）：`POST /api/main-service/auth/login/password`，gateway permit-path 已放行；
- 前端从 User JWT payload（base64 解码）取 `sub`（用户名）、`userId`、`permissions`，用于顶栏显示与菜单/按钮显隐；**不需要新增 /me 接口**；
- main-service 新增 profile（见 G4），gateway 用现有 `local` profile 即可（CORS 已允许 localhost:3000）。

## 3. Gap 分析与后端补齐清单

### G1 propertymgr 外部 API（房屋管理）
现状：只有 `building_info`/`room_info` 两张表（016 changelog），无 yaml、无 Delegate，E2E 靠 H2 SQL 预制。

补齐：
- 新建 `openapi/propertymgr-external.yaml`，并挂入 pom 的 openapi-generator：
  - `GET/POST /propertymgr/buildings`、`POST /propertymgr/buildings/query`（列表分页）
  - `GET/POST /propertymgr/rooms`、`POST /propertymgr/rooms/query`（可按 buildingId/状态过滤）
- 权限：`propertymgr:building:read/write`、`propertymgr:room:read/write`（PermissionManifest 同步后授权给 STAFF 角色）；
- 实现：新建 Delegate，复用现有 `AssetQueryPort`/`AssetCommandPort`（propertymgr/api 下已有），列名沿用表内 camelCase；
- 字段最小集：楼栋（BuildingId 业务编号、StoreId、BuildingName）；房间（BuildingId、Level、RoomNum、LivingNum）。RoomStatus 由后端默认 `EMPTY`。

### G2 合同续租
现状：contract 无任何 renew 端点（dev-spec-v5 也没有）。

补齐（设计从简，与 create+confirm 同款流程）：
- `POST /contract/contracts/{id}/renew`，请求体：`{startDate, endDate, paymentMode, rooms:[{roomId, signedRent, leaseStart, leaseEnd}], chargeRules:[...], remark?}`（即复用 CreateContractRequest 结构，enterpriseId 从原合同带出不许改）；
- 入口状态：`READY_FOR_CHECK_IN` / `PARTIALLY_RETURNED`（在租合同才允许续租）；
- 行为：生成**新合同**（DRAFT，contractNo 新签发，`renewedFromContractId` 记录来源——如需新列则加 changelog，或直接放 remark JSON，实施时定），之后走 confirm → 签约账单 → 支付 → READY_FOR_CHECK_IN 的既有链路；
- 校验：新合同起租日 ≥ 原合同结束日（允许等于），房间不得与原合同 ACTIVE 房间冲突（沿用创建合同时的占用校验）；
- 权限：复用 `contract:contract:write`。

### G3 模拟支付（演示用）
现状：支付回调是 `/internal/v1/accounting/bills/paid`，service 模式下需要 Service JWT，前端没有；billing-service 是 stub 不会真回调。

补齐：
- main-service 新增 **`@Profile("reale2e")` 限定** 的 `DevMockPayController`：`POST /dev/bills/{billId}/mock-pay`，内部直接调 `AccountingService.handleBillPaid`（按 billId 反查 billingServiceBillId）；
- 仅 reale2e profile 激活，生产镜像无此 Bean；权限放行（permit-paths 加 `/dev/**`，仅该 profile 生效）；
- 前端账单列表对 PENDING 账单显示"模拟支付"按钮。

### G4 main-service 真实认证 profile
现状：`application-local.yml` 是 `security.mode=mock`（固定 user-id=1），无法验证真实登录与权限。

补齐：
- 新增 `application-reale2e.yml`：与 local 相同的 H2 数据源，`security.mode=service`，`security.jwt.user/service.secret` 与 gateway 的 `application-local.yml` **完全一致**（local-user-jwt-secret-for-development-only / local-service-jwt-secret-for-development-only），`permit-paths: /auth/**, /v3/api-docs/**, /dev/**`；
- 启动：`spring-boot:run -Dspring-boot.run.profiles=local,reale2e`（local 提供 H2/iam bootstrap，reale2e 覆盖安全模式）；
- mock 权限配置在 reale2e 下不生效，天然验证真实权限链。

### G5 gateway 可用性验证（风险项）
gateway 模块从未在本机编译启动过。实施第一步先 `mvn -pl gateway -am compile` + 启动 + 代理转发冒烟（登录 → 带 token 调 `/api/main-service/iam/users/query`）。如有编译问题按 main-service 同款方式修复。

### G6 STAFF 角色与权限（无后端改动）
- 权限清单由 PermissionManifestBootstrap 启动时自动同步（含 G1 新增的 propertymgr 权限）；
- 演示流程中由 iam_admin 在**角色管理页**创建 STAFF 角色并勾选全部业务权限，再创建 STAFF 用户并绑定角色——全程走现有 IAM API，正好覆盖"Admin 创建 STAFF"剧本；
- 数据权限（data-scope）：新建 STAFF 角色时 `requiredDataScopeDimension` 选无要求，避免额外配置。

## 4. 前端设计

### 4.1 技术形态
- 纯静态：`frontend/web/index.html` + `css/app.css` + `js/`（无 npm、无构建）；
- 本地托管：`python -m http.server 3000`（或任意静态服务器），与 gateway CORS 配置匹配；
- hash 路由：`#/login`、`#/dashboard`、`#/iam/users`…… 单 index.html + 每页面一个 JS 模块；
- 无 UI 库，手写极简 CSS（flex 布局 + 表格 + 表单 + 模态框），目标是"能操作、看得清"。

### 4.2 通用能力（js/common）
- `api.js`：fetch 封装——自动带 `Authorization: Bearer`、统一处理 401（跳登录）/403（提示无权限）/业务错误码（toast `message`）、traceId 展示；
- `auth.js`：token 存 localStorage；JWT payload base64 解码取 `sub/userId/permissions`；`hasPerm(code)`；
- `ui.js`：表格渲染、分页条、表单对话框、toast；
- 菜单按 permissions 动态显隐（无权限的菜单不显示）。

### 4.3 页面清单与 API 映射

| 页面 | 功能 | 主要 API |
|---|---|---|
| 登录 | 账号密码登录、登出 | `/auth/login/password`、`/auth/logout`、`/auth/refresh` |
| 工作台 | 各模块入口 + 当前用户信息 | — |
| 系统-用户 | 列表/创建 STAFF/分配角色/启停/重置密码 | `/iam/users*`、`/iam/users/{id}/roles` |
| 系统-角色 | 列表/创建/分配权限/删除 | `/iam/roles*`、`/iam/roles/{id}/permissions`、`/iam/permissions/query` |
| 房屋-楼栋 | 列表/创建 | G1 `/propertymgr/buildings*` |
| 房屋-房间 | 列表/创建（选楼栋） | G1 `/propertymgr/rooms*` |
| 客户-企业 | 列表/创建/详情 | `/customer/enterprises*` |
| 客户-员工 | 列表/创建（选企业） | `/customer/employees*` |
| 合同-列表 | 查询/创建（企业+房间+收费规则）/详情 | `/contract/contracts*` |
| 合同-详情 | 确认/作废/续租/部分退房/全部退房、房间与账单面板 | confirm/cancel/renew(G2)/partial-return/full-return、`/contract/rooms/query` |
| 入住-分配 | 创建分配（合同房间+员工）/取消分配 | `/occupancy/assignments*` |
| 入住-在住 | 办理入住/退宿/换宿、在住查询 | check-in、check-out、transfer、`/occupancy/stays*` |
| 抄表-绑定 | 房间绑表/解绑/电价维护 | `/metering/rooms/{id}/bindings*`、`/metering/prices*` |
| 抄表-读数 | 抄表录入/读数查询/日结费用查询 | `/metering/readings*`、`/metering/daily-charges/query` |
| 账务-账单 | 查询/详情/**模拟支付(G3)**/作废 | `/accounting/bills*`、`/dev/bills/{id}/mock-pay` |
| 账务-房间账户 | 账户查询/子余额/流水/充值 | `/accounting/room-accounts*`（含 entries、recharge） |
| 账务-押金台账 | 台账查询/详情 | `/accounting/deposit-ledgers*` |
| 运维-定时任务 | 手动触发/日志查询 | `/schedule/tasks/{type}/trigger`、`/schedule/tasks/query` |

### 4.4 演示剧本（验收路径）
1. iam_admin/admin123 登录；
2. 角色管理：创建 `运营STAFF` 角色，勾选全部业务权限（iam 只读 + propertymgr/customer/contract/occupancy/metering/accounting/schedule 全量）；
3. 用户管理：创建 STAFF 用户 `staff01`，绑定该角色；**登出，换 staff01 登录**（菜单只剩被授权项）；
4. 房屋管理：建楼栋 B101 → 建房间 301/302；
5. 客户管理：建企业 → 建员工；
6. 合同管理：建合同（选 2 房间 + 企业押金规则）→ 确认 → 账单页出现签约账单（含押金+首月租）→ 模拟支付 → 合同 READY_FOR_CHECK_IN；
7. 入住管理：分配员工到房间1 → 办理入住 → 自动生成个人押金账单（账单页模拟支付）→ 房间账户已激活（房间账户页可见）；
8. 抄表：房间1 绑电表（底数100）→ 建电价 → 充值 100 → 账单模拟支付 → 抄表 110→130 → 运维页手动触发日结 → 账户流水出现 DAILY_DEDUCT -36；
9. 换宿：在住列表操作换宿到房间2 → 余额 64 迁移可见；
10. 退宿 → 押金退款单 REFUND 500 出现；
11. 合同详情：部分退房（房间1）→ PARTIALLY_RETURNED → 再退房间2 → 自动整体结算 COMPLETED，企业押金/租客余额退款单生成；
12. 续租：对已完成/在租合同演示续租入口（G2，生成新 DRAFT 合同）；
13. 定时任务页：展示日结/租金账单/到期检查的日志。

## 5. 非目标
- 不做任何视觉打磨、响应式、浏览器兼容矩阵；现代 Chrome 可用即可；
- 不做微信小程序端（frontend/wxml）；
- 不接真实 billing-service / device-service（支付走 G3 模拟）；
- 不做微信登录、数据权限细粒度配置演示（页面存在即可）。

## 6. 实施阶段预告（确认后拆 plan）
- **Phase 1（后端 gap）**：G5 gateway 编译启动验证 → G1 propertymgr API → G2 续租 → G3 mock-pay → G4 reale2e profile → 链路冒烟（登录→带权限调用）；
- **Phase 2（前端骨架）**：静态骨架 + 登录 + api/auth/ui 公共层 + IAM 三页（验证权限驱动菜单）；
- **Phase 3（业务页面）**：房屋/客户/合同/入住/抄表/账务/运维 页面；
- **Phase 4（剧本联调）**：按 §4.4 全剧本走一遍，修问题，补文档。

## 7. Phase 1 完成记录（2026-07-26）

全部 5 个 gap 已落地并通过 curl 端到端冒烟（gateway:8080 → main-service:8081 reale2e）：

- **G5 gateway**：编译 + 30 单测一次通过。冒烟发现真实 bug：`SecurityHeaderCleanFilter` 原为 GlobalFilter，而 WebFilter 链（含认证过滤器 ReactiveUserJwtFilter）整体先于 GlobalFilter 链执行，导致它把刚铸入的 `X-Service-Token` 剥掉，下游恒 403 IAM_TOKEN_MISSING。已改为 WebFilter（order -200，先于认证），全链路 200/401/403 行为正确。
- **G4 reale2e profile**：`application-reale2e.yml` 自包含（不叠加 local），登录 iam_admin/admin123 拿真实 User JWT。配套修复：`iam-external.yaml` 全部 24 个操作补 `x-builtin-roles: IAM_ADMIN`；`iam-manifest-maven-plugin` 须从源码 install（~/.m2 旧 jar 输出字段名 `permissionCodes`，bootstrap 读的是 `permissions`）。
- **G1 propertymgr API**：`propertymgr-external.yaml` + `PropertymgrService` + 2 个 Delegate。楼栋查重 409、房间楼栋不存在 404、同房号 409、RoomStatus 写死 EMPTY。冒烟：建 B101 + 房间 33/34 成功。
- **G2 续租**：`POST /contract/contracts/{id}/renew` + changelog 017（`renewed_from_contract_id` 列，Contract 模型同步暴露）。与计划的偏差：占用豁免未抽共享方法，直接在 renewContract 内用 `findRoomByContractAndRoom` 判断"源合同自身 ACTIVE 占用"后跳过校验（createDraft 路径零改动）。UT 5 例全绿。冒烟：合同 33（READY_FOR_CHECK_IN）→ 续租生成 DRAFT 合同 65，企业沿用、来源记录正确；起租日早于原结束日 409。
- **G3 mock-pay**：`DevMockPayController`（`@Profile("reale2e")`）。注意项目未开 `-parameters`，`@PathVariable` 必须显式写名字。冒烟：bill 97 mock-pay → PAID → 合同 READY_FOR_CHECK_IN；重复调用幂等 200；不存在账单 404。

预制演示数据（H2 PROPERTY_LEASE_MAIN_V2）：角色 `OPS_STAFF`(id=37，iam 只读+业务全量权限)、用户 `staff01/staff123`(id=66)、楼栋 B101、房间 33/34、企业 33、合同 33（READY_FOR_CHECK_IN）、续租合同 65（DRAFT）。
