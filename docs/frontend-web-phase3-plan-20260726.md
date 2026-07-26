# frontend/web Phase 3 执行计划 — 业务页面

> 日期：2026-07-26
> 依据：`frontend-web-dev-spec-v1.md` §4.3 页面清单、§6 Phase 3 范围
> 前置：Phase 1（后端 gap + reale2e 链路）、Phase 2（骨架 + 登录 + IAM）均已完成

## 范围

按 spec §4.3 实现 14 个业务页面（房屋 2 / 客户 2 / 合同 2 / 入住 2 / 抄表 2 / 账务 3 / 运维 1），全部走 gateway 真实链路，菜单与按钮按权限显隐。

## API 核对结论（已从 openapi yaml 逐字确认）

关键易错点：

1. **合同查询分页字段是 `page`/`size`**（ContractQueryRequest / ContractRoomQueryRequest），**不是** PageRequest 的 `pageNo`/`pageSize`；其它模块（customer/occupancy/metering/accounting/schedule/propertymgr/iam）均为 `pageNo`/`pageSize`。
2. confirm / cancel / full-return / check-out / cancelAssignment / unbind / cancelBill 均为 **PUT 无 body**；partial-return 为 **POST** `{contractRoomIds[]}`；renew 为 **POST** RenewContractRequest。
3. `GET /metering/rooms/{roomId}/bindings` 返回 `{items[]}`（非分页）；`GET /metering/prices` 用 **query 参数** storeId/meterType，返回 `{items[]}`。
4. 抄表读数 `readingTime` 为 date-time，前端用 `datetime-local` 输入转 ISO（`new Date(v).toISOString()`）。
5. 员工查询 `enterpriseId` **必填** —— 员工页须先选企业。
6. 入住分配 `contractRoomId` 是**合同房间 id**（非 roomId）；创建分配对话框流程：输合同号 → 加载合同详情（拿 enterpriseId + rooms）→ 选合同房间 + 该企业员工。
7. 充值返回 `{billId, billingServiceBillId, paymentUrl}`，充值账单同样走 mock-pay 闭环。
8. mock-pay：`POST /dev/bills/{billId}/mock-pay`（reale2e 限定，无权限码，permit-path 放行 `/dev/**`）。

## 文件清单

| 动作 | 文件 |
|---|---|
| 修改 | `js/app.js` —— 注册 14 条路由（分组菜单）；`currentPath()` 剥离 query string |
| 修改 | `js/common/ui.js` —— 加 `hashQuery()`（解析 `#/path?k=v`）、`fmtTime()`；statusTag 补业务状态映射 |
| 修改 | `js/pages/dashboard.js` —— 功能入口补业务模块 |
| 新建 | `js/pages/property-buildings.js` —— 楼栋列表（名称模糊）/ 创建 |
| 新建 | `js/pages/property-rooms.js` —— 房间列表（楼栋/状态过滤）/ 创建（选楼栋） |
| 新建 | `js/pages/customer-enterprises.js` —— 企业列表 / 创建 / 编辑 |
| 新建 | `js/pages/customer-employees.js` —— 先选企业 → 员工列表 / 创建 / 编辑 |
| 新建 | `js/pages/contract-list.js` —— 合同查询（企业/状态，page+size）/ 创建（企业+动态房间行+动态收费规则行） |
| 新建 | `js/pages/contract-detail.js` —— 详情（房间+收费规则面板）；按状态出操作：DRAFT→确认/作废；READY_FOR_CHECK_IN/PARTIALLY_RETURNED→部分退房(勾 ACTIVE 房间)/整体退房/续租(预填房间) |
| 新建 | `js/pages/occupancy-assignments.js` —— 分配查询 / 创建（合同→房间+员工两级联动）/ 取消 |
| 新建 | `js/pages/occupancy-stays.js` —— 在住查询 / 办理入住（输 assignmentId）/ 退宿 / 换宿（输 toContractRoomId） |
| 新建 | `js/pages/metering-bindings.js` —— 输 roomId 查绑定 / 绑表 / 解绑；电价列表 + 新增版本 |
| 新建 | `js/pages/metering-readings.js` —— 抄表录入 / 读数查询 / 日结费用查询 + 分摊明细弹窗 |
| 新建 | `js/pages/accounting-bills.js` —— 账单查询（类型/状态/合同/房间）/ 详情 / **模拟支付**(PENDING) / 作废 |
| 新建 | `js/pages/accounting-accounts.js` —— 房间账户查询 / 子余额详情 / 流水查询 / 充值 |
| 新建 | `js/pages/accounting-deposits.js` —— 押金台账查询 / 详情 |
| 新建 | `js/pages/schedule-tasks.js` —— 手动触发（taskType+targetDate+force）/ 日志查询 |

菜单分组：房屋管理 / 客户管理 / 合同管理 / 入住管理 / 抄表 / 账务 / 运维（接在现有"系统管理"之前）。

## 验证

1. `node --check` 全部新文件语法通过；
2. 静态服务器(3000) + gateway(8080) + main-service(reale2e, 8081) 三进程在线；
3. python 脚本按各页面请求形态对 14 个页面的核心接口逐一打一遍（读接口 200、写接口用预制数据或新建临时数据验证）；
4. staff01（OPS_STAFF 角色，业务全量权限）登录后菜单应出现全部业务分组；无 token 访问任意业务页跳登录；
5. 浏览器点击测试留给用户（本机无 headless 浏览器），报告中明确说明。

## 非目标

- 不做 spec §5 已排除项（视觉打磨、响应式等）；
- 合同创建的收费规则 chargeType/payerType 用下拉预置常用值（ENTERPRISE_DEPOSIT/RENT × ENTERPRISE/TENANT），允许手输；
- 演示剧本全流程走查属于 Phase 4。
