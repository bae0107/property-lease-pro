package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;

/**
 * 押金台账信息（供 Port 查询返回）。
 */
public record DepositLedgerInfo(
        Long id,
        String status,
        BigDecimal originalAmount,
        BigDecimal occupiedAmount,
        BigDecimal refundableAmount
) {}
