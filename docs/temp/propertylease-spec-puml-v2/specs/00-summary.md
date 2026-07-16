# 总体修正版总结

## 关键修正规则
1. `DRAFT` 合同不锁房，可与其他 `DRAFT` 重复选房。
2. 房间锁定发生在 `confirmContract` 之后，合同进入 `SIGN_BILL_PENDING`。
3. `DRAFT` 与 `SIGN_BILL_PENDING` 均可取消。
4. `SIGN_BILL_PENDING` 可以分配人员，但不能办理入住。
5. `READY_FOR_CHECK_IN` 才允许办理入住。
6. 入住必须基于已存在的 `RoomAssignment`，未分配不得入住。
7. 分配只能由 STAFF 发起。
8. 分配成功后自动创建/激活具备“办理入住权限”的 tenant user。
9. 办理入住可由 TENANT 自助，也可由 STAFF 代办。
10. 分配人数不能超过房间容量上限。
11. 个人押金跟随入住产生，支付后获取换宿资格；换宿不重新缴纳个人押金。
12. 换宿允许跨合同。
13. 房间账户归属于房间，不归属于计量设备；表损坏更换后账户与余额继承。
14. 日结先扣企业充值余额，再扣租客分摊余额。
15. 租客换宿时，其在原房间账户中未消耗余额随人迁移到新房间账户。

## DRAFT 房源冲突规则
- `DRAFT` 仅为草稿态，不占用房间资源。
- 多个 `DRAFT` 可以引用同一房间。
- 冲突校验发生在 `confirmContract` 时。
- 先成功确认并锁房的合同获胜，后续合同确认时需重新选择房源。

## 模块关注范围
当前主要关注：
- contract
- occupancy
- metering
- accounting
- payment

asset / customer / device / basic billing CRUD 已认为就绪。
