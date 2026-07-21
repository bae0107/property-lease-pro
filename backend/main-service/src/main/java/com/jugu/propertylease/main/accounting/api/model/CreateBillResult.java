package com.jugu.propertylease.main.accounting.api.model;

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
