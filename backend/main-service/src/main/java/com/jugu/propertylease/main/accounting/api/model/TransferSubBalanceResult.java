package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;

/**
 * 换宿子余额迁移结果。
 */
public record TransferSubBalanceResult(BigDecimal migratedAmount) {}
