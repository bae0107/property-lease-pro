package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// ── Commands ──────────────────────────────────────────────────────────────

/**
 * 创建企业签约账单命令。
 *
 * @param contractId    合同 ID
 * @param enterpriseId  企业 ID
 * @param totalAmount   首月租金 + 企业押金合计
 * @param depositAmount 其中押金部分（用于创建 DepositLedger）
 */
public record EnterpriseSignBillCommand(
        Long contractId,
        Long enterpriseId,
        BigDecimal totalAmount,
        BigDecimal depositAmount
) {}

/**
 * 创建个人押金账单命令。
 */
public record PersonalDepositBillCommand(
        Long tenantId,
        Long stayId,
        Long contractId,
        Long roomId
) {}

/**
 * 部分退房结算命令。
 */
public record PartialReturnCommand(
        Long contractId,
        List<Long> returnedRoomIds
) {}

/**
 * 日结扣减命令。
 *
 * @param roomId                 房间 ID
 * @param settlementDate         日结业务日期
 * @param totalAmount            当日应扣总额
 * @param enterpriseCoverAmount  企业子余额承担部分（metering 预先计算）
 * @param tenantApportionments   各租客分摊明细
 */
public record DailyDeductionCommand(
        Long roomId,
        LocalDate settlementDate,
        BigDecimal totalAmount,
        BigDecimal enterpriseCoverAmount,
        List<TenantDeductItem> tenantApportionments
) {}

/**
 * 单个租客分摊明细。
 */
public record TenantDeductItem(
        Long tenantId,
        Long stayId,
        BigDecimal amount
) {}

// ── Results ───────────────────────────────────────────────────────────────

/**
 * 创建账单结果。
 *
 * @param billId              accounting 模块内 bill.id
 * @param billingServiceBillId billing-service 侧账单 ID（stub 阶段为 null）
 * @param paymentUrl          第三方支付跳转 URL（stub 阶段为占位 URL）
 */
public record CreateBillResult(
        Long billId,
        Long billingServiceBillId,
        String paymentUrl
) {}

/**
 * 换宿子余额迁移结果。
 */
public record TransferSubBalanceResult(BigDecimal migratedAmount) {}

/**
 * 个人押金退宿结算结果。
 */
public record DepositSettlementResult(
        Long refundBillId,
        BigDecimal refundAmount
) {}

/**
 * 日结扣减结果。
 */
public record DailyDeductionResult(
        BigDecimal enterpriseDeducted,
        BigDecimal tenantsDeducted,
        BigDecimal shortfall          // 0 = 全额扣款，> 0 = 欠费
) {}

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
