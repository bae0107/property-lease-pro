package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;

/**
 * 日结扣减结果。
 */
public record DailyDeductionResult(
        BigDecimal enterpriseDeducted,
        BigDecimal tenantsDeducted,
        BigDecimal shortfall          // 0 = 全额扣款，> 0 = 欠费
) {}
