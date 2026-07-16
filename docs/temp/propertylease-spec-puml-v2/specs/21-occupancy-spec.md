# Occupancy 模块 Spec（修正版）

## 模块职责
- 分配租客到合同房间
- 办理入住
- 办理退宿
- 办理换宿
- 管理实际居住关系 stay

## 核心建模结论
### 分配 != 入住
- 分配是 STAFF 发起的预安排动作
- 入住是基于 assignment 消费产生的实际居住关系

### Check-in 的正确归属
- check-in 不属于 contract
- check-in 不属于 asset
- check-in 属于 occupancy
- 它的本质是：**消费 assignment，创建 stay**

## 核心实体
### RoomAssignment
- assignmentId
- contractId
- contractRoomId
- roomId
- tenantId
- status = ASSIGNED / CONSUMED / CANCELLED
- assignedAt
- assignedBy

### Stay
- stayId
- sourceAssignmentId
- contractId
- contractRoomId
- roomId
- tenantId
- stayStatus = CHECKED_IN / TRANSFERRED / CHECKED_OUT
- checkInAt
- checkOutAt

### TransferRecord
- transferRecordId
- tenantId
- fromStayId
- fromContractId
- fromRoomId
- toContractId
- toContractRoomId
- toRoomId
- transferAt

## 容量规则
### 有效占位人数
`effectiveOccupied = ASSIGNED assignments + CHECKED_IN stays`

规则：
`effectiveOccupied <= Room.maxOccupancy`

含义：
- assignment 阶段就占一个名额
- check-in 成功后 assignment 被消费，stay 接替该名额
- 未分配者不允许直接入住

## 用户与权限规则
- assignTenantToRoom：只能 STAFF 执行
- assignment 创建后：自动创建/激活 tenant user 的自助入住能力
- selfCheckIn：只能由 assignment 对应 tenant 执行
- staffCheckIn：有权限 STAFF 可代办

## 关键命令
### assignTenantToRoom
前置：
- contract.status in {SIGN_BILL_PENDING, READY_FOR_CHECK_IN}
- tenant 属于企业
- effectiveOccupied < maxOccupancy
动作：
- 创建 ASSIGNED assignment
- 自动创建/激活 tenant user

### selfCheckIn / staffCheckIn
前置：
- assignment.status = ASSIGNED
- contract.status = READY_FOR_CHECK_IN
- room checked-in count < maxOccupancy
动作：
- 创建 stay
- assignment -> CONSUMED
- 触发 meter anchor / personal deposit / door lock

### checkOutTenant
动作：
- Stay -> CHECKED_OUT
- 采集退宿读数
- 回收门锁
- 触发个人押金结算

### transferTenant
前置：
- fromStay.status = CHECKED_IN
- personal deposit ledger = ACTIVE
- target contract.status = READY_FOR_CHECK_IN
- target room capacity allows
动作：
- 原 stay -> TRANSFERRED
- 创建新 stay
- 迁移押金资格
- 迁移租客房间账户余额
- 门锁切换
