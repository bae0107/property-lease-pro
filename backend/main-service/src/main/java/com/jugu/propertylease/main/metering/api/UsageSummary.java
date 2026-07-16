package com.jugu.propertylease.main.metering.api;

import java.math.BigDecimal;

/**
 * 退宿时返回的用量摘要（入住期间累计）。
 */
public record UsageSummary(
        Long stayId,
        BigDecimal waterUsage,
        BigDecimal electricityUsage,
        BigDecimal hotWaterUsage,
        BigDecimal totalAmount,       // 按当期单价计算的应付总额
        BigDecimal alreadyDeducted,   // 日结期间已成功扣款总额
        BigDecimal outstanding        // 欠费（尚未扣款部分）
) {}
