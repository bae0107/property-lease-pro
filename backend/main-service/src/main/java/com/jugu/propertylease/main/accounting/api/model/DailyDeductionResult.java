package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;

/**
 * 日结扣减结果。
 *
 * @param enterpriseDeducted 企业子余额实际扣减金额
 * @param tenantsDeducted 租客子余额实际扣减金额
 * @param shortfall 欠费金额，0 表示全额扣款
 */
public record DailyDeductionResult(
        BigDecimal enterpriseDeducted,
        BigDecimal tenantsDeducted,
        BigDecimal shortfall
) {}
