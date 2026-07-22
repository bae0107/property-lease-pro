package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;

/**
 * 月租账单生成命令（contract 模块每月 1 号调度触发）。
 *
 * @param contractId   合同 ID
 * @param enterpriseId 付款企业 ID
 * @param amount       账单金额（ACTIVE 房间 signed_rent 求和，QUARTERLY 已乘 3）
 * @param period       账期 yyyy-MM（仅用于账单备注/描述，幂等按 contractId+billType+创建月份判重）
 */
public record RentBillCommand(
        Long contractId,
        Long enterpriseId,
        BigDecimal amount,
        String period
) {}
