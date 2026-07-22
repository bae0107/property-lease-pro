package com.jugu.propertylease.main.accounting.service;

import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.AccountingQueryPort;
import com.jugu.propertylease.main.accounting.api.model.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * AccountingCommandPort 实现（委托给 AccountingService）。
 */
@Service
public class AccountingCommandPortImpl implements AccountingCommandPort {

    private final AccountingService svc;

    public AccountingCommandPortImpl(AccountingService svc) {
        this.svc = svc;
    }

    @Override
    public CreateBillResult createEnterpriseSignBill(EnterpriseSignBillCommand cmd) {
        return svc.createEnterpriseSignBill(cmd);
    }

    @Override
    public void activateRoomSharedAccount(Long roomId, Long contractId) {
        svc.activateRoomSharedAccount(roomId, contractId);
    }

    @Override
    public CreateBillResult createPersonalDepositBill(PersonalDepositBillCommand cmd) {
        return svc.createPersonalDepositBill(cmd);
    }

    @Override
    public void transferPersonalDepositEligibility(Long tenantId,
                                                    Long fromStayId, Long toStayId) {
        svc.transferPersonalDepositEligibility(tenantId, fromStayId, toStayId);
    }

    @Override
    public TransferSubBalanceResult transferTenantSubBalance(Long tenantId,
                                                              Long fromRoomId,
                                                              Long toRoomId) {
        return svc.transferTenantSubBalance(tenantId, fromRoomId, toRoomId);
    }

    @Override
    public DepositSettlementResult settlePersonalDepositOnCheckout(Long tenantId, Long stayId) {
        return svc.settlePersonalDepositOnCheckout(tenantId, stayId);
    }

    @Override
    public void settlePartialReturn(PartialReturnCommand cmd) {
        svc.settlePartialReturn(cmd);
    }

    @Override
    public void settleFullReturn(Long contractId) {
        svc.settleFullReturn(contractId);
    }

    @Override
    public CreateBillResult createRentBill(RentBillCommand cmd) {
        return svc.createRentBill(cmd);
    }

    @Override
    public DailyDeductionResult deductForDailySettlement(DailyDeductionCommand cmd) {
        return svc.deductForDailySettlement(cmd);
    }
}
