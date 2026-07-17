package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;

/**
 * 单个租客分摊明细。
 */
public record TenantDeductItem(
        Long tenantId,
        Long stayId,
        BigDecimal amount
) {}
