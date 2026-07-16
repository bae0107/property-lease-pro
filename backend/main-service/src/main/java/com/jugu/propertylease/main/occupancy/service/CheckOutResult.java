package com.jugu.propertylease.main.occupancy.service;

import com.jugu.propertylease.main.jooq.tables.pojos.Stay;

import java.math.BigDecimal;

/**
 * {@link CheckOutService#checkOut} 返回结果。
 *
 * @param stay                  已更新为 CHECKED_OUT 的 stay 记录
 * @param personalDepositBillId 押金结算产生的退款账单 ID（无需退款时为 null）
 * @param refundAmount          实际退款金额（无需退款时为 null 或 0）
 */
public record CheckOutResult(
        Stay stay,
        Long personalDepositBillId,
        BigDecimal refundAmount
) {}
