package com.jugu.propertylease.main.accounting.delegate;

import com.jugu.propertylease.main.accounting.service.AccountingService;
import com.jugu.propertylease.main.internal.api.InternalAccountingApiDelegate;
import com.jugu.propertylease.main.internal.api.model.BillPaidCallbackRequest;
import org.springframework.stereotype.Service;

/**
 * billing-service 回调入口 Delegate。
 *
 * <p>路由逻辑：accounting 模块根据 bill.bill_type 分发处理，
 * 详见 AccountingService.handleBillPaid()。
 */
@Service
public class InternalAccountingApiDelegateImpl implements InternalAccountingApiDelegate {

    private final AccountingService svc;

    public InternalAccountingApiDelegateImpl(AccountingService svc) {
        this.svc = svc;
    }

    @Override
    public void billPaidCallback(BillPaidCallbackRequest request) {
        svc.handleBillPaid(
                request.getBillingServiceBillId(),
                java.math.BigDecimal.valueOf(request.getActualAmount()),
                request.getPaidAt()
        );
    }
}
