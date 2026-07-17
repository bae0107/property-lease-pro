package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;

/**
 * 创建企业签约账单命令。
 *
 * @param contractId 合同 ID
 * @param enterpriseId 企业 ID
 * @param totalAmount 首月租金 + 企业押金合计
 * @param depositAmount 其中押金部分（用于创建 DepositLedger）
 */
public record EnterpriseSignBillCommand(
        Long contractId,
        Long enterpriseId,
        BigDecimal totalAmount,
        BigDecimal depositAmount
) {}
