package com.jugu.propertylease.main.accounting.outer;

import java.math.BigDecimal;

/**
 * billing-service 客户端契约（当前为 stub 实现）。
 *
 * <p>accounting 模块通过此接口与 billing-service 交互，
 * 实现完全解耦——待 billing-service 提供 OpenAPI 规范后，
 * 仅需替换此接口的实现类，调用方代码（Service 层）不变。
 *
 * <p>账单创建成功后，billing-service 会异步回调
 * {@code POST /internal/v1/accounting/bills/paid}，
 * 不依赖此接口的返回值来触发后续流程。
 */
public interface BillingServicePort {

    /**
     * 在 billing-service 中创建账单，生成支付单。
     *
     * @return 创建结果（含 billing-service 侧账单 ID 和支付 URL）
     */
    BillingCreateResult createBill(BillingCreateCommand cmd);

    /**
     * 在 billing-service 中发起退款。
     */
    void createRefund(BillingRefundCommand cmd);

    // ── Model records ────────────────────────────────────────────────────

    record BillingCreateCommand(
            String billNo,
            BigDecimal amount,
            String billType,
            String description
    ) {}

    record BillingCreateResult(
            Long billingServiceBillId,
            String paymentUrl
    ) {}

    record BillingRefundCommand(
            String billNo,
            BigDecimal amount,
            String reason
    ) {}
}
