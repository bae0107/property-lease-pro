# Accounting 模块 Spec（修正版）

## 模块职责
- 管理房间账户
- 管理企业/租客子余额
- 管理各类账单
- 管理企业押金与个人押金台账
- 管理日结扣减、充值入账、结算退款

## 核心实体
### RoomAccount
- roomAccountId
- roomId
- currentContractId
- status

### RoomAccountSubBalance
- subBalanceId
- roomAccountId
- ownerType = ENTERPRISE / TENANT
- ownerId
- availableBalance
- frozenBalance

### RoomAccountEntry
- entryId
- roomAccountId
- ownerType
- ownerId
- entryType
- amount
- relatedBillId
- occurredAt

### Bill
- billId
- billType
- billOwnerType
- billOwnerId
- contractId
- roomId
- tenantId
- billStatus
- totalAmount

### DepositLedger
- depositLedgerId
- depositType = ENTERPRISE / PERSONAL
- ownerType
- ownerId
- currentStayId (personal deposit current stay reference)
- originalAmount
- occupiedAmount
- refundableAmount
- status = PENDING_PAYMENT / ACTIVE / REFUND_PENDING / CLOSED

## 核心账单类型
- ENTERPRISE_SIGN_BILL
- PERSONAL_DEPOSIT_BILL
- RECHARGE_BILL
- SETTLEMENT_BILL
- REFUND_BILL

## 关键扣减规则
### 房间账户日结扣减顺序
1. 先扣企业子余额
2. 不足部分再按 tenant apportionment 扣租客子余额
3. 不足则登记欠费 / 待补缴

## 换宿余额迁移规则
### 原则
租客在原房间账户中的未消耗余额，**随人迁移到新房间账户**

### 处理方式
- 原房间账户写出账流水
- 新房间账户写入账流水
- 调整对应 tenant 子余额
- 不影响企业子余额

## 个人押金规则
- 入住时创建个人押金账单
- 支付成功后 personal deposit ledger -> ACTIVE
- 换宿不重新缴纳押金
- 仅在最终退宿时结算退还

## 企业押金规则
- 合同确认后纳入企业签约账单
- 合同退房 / 合同结束时结合其他费用统一结算
- 可生成退款账单

## 关键命令
- createEnterpriseSignBill
- createPersonalDepositBill
- createRoomRechargeBill
- confirmRechargePaid
- deductRoomAccountForDailySettlement
- activatePersonalDepositAfterPaid
- transferPersonalDepositEligibility
- transferTenantRoomSubBalance
- settlePersonalDepositOnCheckout
- settleContractReturn
