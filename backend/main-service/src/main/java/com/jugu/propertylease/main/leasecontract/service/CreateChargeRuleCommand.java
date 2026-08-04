package com.jugu.propertylease.main.leasecontract.service;

import java.math.BigDecimal;

/**
 * {@link ContractLifecycleService#createDraft} 入参：单条计费规则。
 *
 * <p>chargeType 传 "ENTERPRISE_DEPOSIT" 的记录会在 confirmContract 时被汇总为企业押金金额。
 */
public record CreateChargeRuleCommand(
        String chargeType,
        String payerType,
        BigDecimal amount,
        String ruleSnapshot
) {}
