package com.jugu.propertylease.main.accounting.outer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * BillingServicePort 的 stub 实现。
 *
 * <p>所有方法只打印 WARN 日志，不发起真实网络调用。
 * billing-service 支付回调（{@code POST /internal/v1/accounting/bills/paid}）
 * 在集成测试阶段需手动调用来模拟支付完成，推进业务流程。
 *
 * <p>stub 阶段返回合成的 billingServiceBillId（以启动时间戳为种子递增，避免重启冲突），
 * 集成测试时可从日志或 bill 表读取该 ID 手动触发 billPaidCallback。
 *
 * <p>替换方式：待 billing-service 提供 OpenAPI 规范后，
 * 新建 {@code HttpBillingServicePort implements BillingServicePort}，
 * 并通过 Spring Profile 或配置切换替换此 Bean，无需修改 Service 层。
 */
@Service
public class StubBillingServicePort implements BillingServicePort {

    private static final Logger log = LoggerFactory.getLogger(StubBillingServicePort.class);

    /** 合成 billing-service 账单 ID 序列（时间戳种子，避免与真实 ID 段及重启后冲突）。*/
    private static final AtomicLong STUB_BILL_ID_SEQ =
            new AtomicLong(System.currentTimeMillis());

    @Override
    public BillingCreateResult createBill(BillingCreateCommand cmd) {
        long stubBillId = STUB_BILL_ID_SEQ.incrementAndGet();
        log.warn("[STUB-BILLING] createBill: billNo={} amount={} type={} stubBillingServiceBillId={}",
                cmd.billNo(), cmd.amount(), cmd.billType(), stubBillId);
        return new BillingCreateResult(stubBillId,
                "https://stub.billing.local/pay?billNo=" + cmd.billNo());
    }

    @Override
    public void createRefund(BillingRefundCommand cmd) {
        log.warn("[STUB-BILLING] createRefund: billNo={} amount={} reason={}",
                cmd.billNo(), cmd.amount(), cmd.reason());
    }
}
