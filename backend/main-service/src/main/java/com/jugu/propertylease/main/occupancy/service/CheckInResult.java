package com.jugu.propertylease.main.occupancy.service;

import com.jugu.propertylease.main.jooq.tables.pojos.Stay;

/**
 * {@link CheckInService#checkIn} 返回结果。
 *
 * @param stay                  新建的 stay 记录（已回写 iamUserId）
 * @param personalDepositBillId 本次入住创建的个人押金账单 ID
 * @param personalDepositStatus 押金台账状态（创建后固定为 PENDING_PAYMENT）
 */
public record CheckInResult(
        Stay stay,
        Long personalDepositBillId,
        String personalDepositStatus
) {}
