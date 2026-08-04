package com.jugu.propertylease.main.leasecontract.api;

import com.jugu.propertylease.main.leasecontract.service.ContractLifecycleService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * {@link ContractCallbackPort} 实现。委托 {@link ContractLifecycleService} 的内部状态流转方法，
 * 两处回调都做了幂等处理（见 markReadyForCheckIn / markCompleted 内部的状态短路判断）。
 */
@Service
public class ContractCallbackPortImpl implements ContractCallbackPort {

    private final ContractLifecycleService lifecycleService;

    // @Lazy 打破构造器循环依赖：ContractLifecycleService → AccountingCommandPort(AccountingService)
    //   → ContractCallbackPort(本类) → ContractLifecycleService
    public ContractCallbackPortImpl(@Lazy ContractLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Override
    public void onSignBillPaid(Long contractId, Long billId) {
        // billId 目前只用于日志/审计场景，状态流转本身只依赖 contractId。
        lifecycleService.markReadyForCheckIn(contractId);
    }

    @Override
    public void onSettlementCompleted(Long contractId) {
        lifecycleService.markCompleted(contractId);
    }
}
