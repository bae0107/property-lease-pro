# Payment 模块 Spec（修正版）

## 模块职责
- 创建支付单
- 对接第三方支付
- 接收支付回调
- 通知 accounting / contract / occupancy 状态推进

## 核心实体
### PaymentOrder
- paymentOrderId
- billId
- payerId
- channel
- amount
- status

### PaymentCallback
- callbackId
- paymentOrderId
- callbackPayload
- callbackStatus
- callbackTime

## 关键命令
### createPaymentOrder
输入：
- billId
- payerId
- channel

输出：
- paymentOrderId
- outTradeNo
- payUrl / payParams

### handlePaymentCallback
输入：
- paymentOrderId
- callbackPayload

动作：
- 验签
- 更新支付状态
- 发布 PaymentSucceeded / PaymentFailed 事件

## 典型联动
- 企业签约账单支付成功 -> Contract READY_FOR_CHECK_IN
- 个人押金支付成功 -> personal deposit ACTIVE
- 充值账单支付成功 -> 房间账户子余额增加
