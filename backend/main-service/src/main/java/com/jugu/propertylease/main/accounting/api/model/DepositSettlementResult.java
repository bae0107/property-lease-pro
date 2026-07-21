package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;

/**
 * 个人押金退宿结算结果。
 */
public record DepositSettlementResult(
        Long refundBillId,
        BigDecimal refundAmount
) {}
