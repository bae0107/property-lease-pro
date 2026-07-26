package com.jugu.propertylease.main.accounting.delegate;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.repo.BillRepository;
import com.jugu.propertylease.main.accounting.service.AccountingService;
import com.jugu.propertylease.main.jooq.tables.pojos.Bill;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 演示用模拟支付端点（仅 reale2e profile 激活，生产镜像无此 Bean）。
 *
 * <p>前端联调时 billing-service 是 stub 不会真回调，本端点直接调用
 * {@link AccountingService#handleBillPaid} 走真实支付完成链路
 * （账单 → PAID、押金台账激活、合同推进 READY_FOR_CHECK_IN 等，全部幂等）。
 * 路由在 reale2e profile 的 permit-paths 中放行（/dev/**）。
 */
@RestController
@Profile("reale2e")
public class DevMockPayController {

    private final BillRepository billRepository;
    private final AccountingService accountingService;

    public DevMockPayController(BillRepository billRepository,
                                AccountingService accountingService) {
        this.billRepository = billRepository;
        this.accountingService = accountingService;
    }

    @PostMapping("/dev/bills/{billId}/mock-pay")
    public Map<String, Object> mockPay(@PathVariable("billId") Long billId) {
        Bill bill = billRepository.findBillById(billId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "BILL_NOT_FOUND", "账单不存在：" + billId));

        if (!"PAID".equals(bill.getBillStatus())) {
            accountingService.handleBillPaid(bill.getBillingServiceBillId(),
                    bill.getTotalAmount(), OffsetDateTime.now());
        }

        return Map.of("billId", billId, "billStatus", "PAID");
    }
}
