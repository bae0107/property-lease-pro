# Contract 模块 Spec（修正版）

## 模块职责
- 维护企业合同
- 锁定合同房间
- 生成企业签约账单
- 维护合同生命周期
- 发起部分退房 / 全部退房 / 合同结算

## 不负责
- 办理入住
- 门锁密码发放
- 设备读数采集
- 房间账户扣减

## 核心实体
### Contract
- id
- enterpriseId
- contractNo
- signDate
- startDate
- endDate
- paymentMode
- contractStatus
- remark

### ContractRoom
- id
- contractId
- roomId
- signedRent
- leaseStartDate
- leaseEndDate
- status

### ContractChargeRule
- id
- contractId
- chargeType
- payerType
- ruleSnapshot

## 状态规则
- `DRAFT`：草稿，不锁房
- `SIGN_BILL_PENDING`：已确认并锁房，企业签约账单待支付
- `READY_FOR_CHECK_IN`：企业签约账单已支付，可入住
- `PARTIALLY_RETURNED`：部分房间已退房
- `FULLY_RETURNED`：全部房间已退房
- `SETTLING`：结算处理中
- `COMPLETED`：合同结束
- `CANCELLED`：合同取消（仅 DRAFT / SIGN_BILL_PENDING）

## 关键命令
### createContractDraft
输入：enterpriseId, rooms[], paymentMode, 起止日期等
输出：contractId, status=DRAFT

### confirmContract
前置：
- rooms 可锁定
- 企业存在
- 房源设备就绪
动作：
- 锁定房间
- 生成企业签约账单
- 状态 -> SIGN_BILL_PENDING

### cancelContract
前置：
- status in {DRAFT, SIGN_BILL_PENDING}
- 若 SIGN_BILL_PENDING，则签约账单未支付且无已入住 stay
动作：
- 若已锁房则释放房源
- 作废未支付账单
- 状态 -> CANCELLED

### activateContractAfterSignBillPaid
前置：
- 企业签约账单支付成功
动作：
- 状态 -> READY_FOR_CHECK_IN

### applyPartialReturnRooms / applyFullReturnRooms
前置：
- 目标房间当前无 CHECKED_IN stay
动作：
- 触发结算
- 更新合同房间状态

## DRAFT 房源冲突规则
- `DRAFT` 不锁定房源
- 多个 `DRAFT` 可引用同一房间
- 确认时进行最终冲突校验
