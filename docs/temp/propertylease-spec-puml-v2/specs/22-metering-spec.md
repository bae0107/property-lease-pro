# Metering 模块 Spec（修正版）

## 模块职责
- 管理设备读数
- 管理入住/退宿锚点
- 执行每日日结
- 产出房间日费用与租客分摊结果

## 核心实体
### MeterReading
- id
- roomId
- deviceId
- meterType
- readingValue
- readingTime
- anchorType

### SettlementAnchor
- id
- roomId
- stayId (可空)
- anchorType = CHECK_IN / DAILY_CLOSING / CHECK_OUT
- readingGroupId
- anchorTime

### RoomDailyCharge
- id
- roomId
- settlementDate
- waterAmount
- electricAmount
- hotWaterAmount
- totalAmount

### TenantApportionment
- id
- roomId
- tenantId
- stayId
- settlementDate
- amount

## 设备更换规则
- 房间账户与余额跟随 room，不跟随 meter device
- 设备更换只是计量来源替换
- 需要维护设备绑定版本与新表起始读数
- 后续日结切换到新表分段
- 历史读数与历史流水保持不变

## 关键命令
### collectCheckInReadings
- 入住成功后调用
- 获取房间有效计量设备的最新读数
- 生成 CHECK_IN anchor

### collectCheckOutReadings
- 退宿或换宿转出时调用
- 生成 CHECK_OUT anchor

### runDailySettlement
- 每天定时执行
- 计算房间日费用
- 生成企业扣减额 + 租客分摊额
- 调 accounting 扣减房间账户

### replaceRoomMeterDevice
- 关闭旧绑定
- 创建新绑定版本
- 记录新设备初始读数
- 不影响房间账户
