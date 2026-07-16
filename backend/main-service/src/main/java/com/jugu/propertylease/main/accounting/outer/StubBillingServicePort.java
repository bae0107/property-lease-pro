package com.jugu.propertylease.main.accounting.outer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * BillingServicePort 的 stub 实现。
 *
 * <p>所有方法只打印 WARN 日志，不发起真实网络调用。
 * billing-service 支付回调（{@code POST /internal/v1/accounting/bills/paid}）
 * 在集成测试阶段需手动调用来模拟支付完成，推进业务流程。
 *
 * <p>替换方式：待 billing-service 提供 OpenAPI 规范后，
 * 新建 {@code HttpBillingServicePort implements BillingServicePort}，
 * 并通过 Spring Profile 或配置切换替换此 Bean，无需修改 Service 层。
 */
@Service
public class StubBillingServicePort implements BillingServicePort {

    private static final Logger log = LoggerFactory.getLogger(StubBillingServicePort.class);

    @Override
    public BillingCreateResult createBill(BillingCreateCommand cmd) {
        log.warn("[STUB-BILLING] createBill: billNo={} amount={} type={}",
                cmd.billNo(), cmd.amount(), cmd.billType());
        // 返回占位数据；billingServiceBillId=null 表示账单未对接真实支付
        return new BillingCreateResult(null,
                "https://stub.billing.local/pay?billNo=" + cmd.billNo());
    }

    @Override
    public void createRefund(BillingRefundCommand cmd) {
        log.warn("[STUB-BILLING] createRefund: billNo={} amount={} reason={}",
                cmd.billNo(), cmd.amount(), cmd.reason());
    }
}
