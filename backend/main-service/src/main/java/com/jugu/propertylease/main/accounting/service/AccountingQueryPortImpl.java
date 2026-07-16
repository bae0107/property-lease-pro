package com.jugu.propertylease.main.accounting.service;

import com.jugu.propertylease.main.accounting.api.AccountingQueryPort;
import com.jugu.propertylease.main.accounting.api.model.DepositLedgerInfo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * AccountingQueryPort 实现（委托给 AccountingService）。
 */
@Service
public class AccountingQueryPortImpl implements AccountingQueryPort {

    private final AccountingService svc;

    public AccountingQueryPortImpl(AccountingService svc) {
        this.svc = svc;
    }

    @Override
    public BigDecimal getEnterpriseSubBalance(Long roomId, Long enterpriseId) {
        return svc.getEnterpriseSubBalance(roomId, enterpriseId);
    }

    @Override
    public DepositLedgerInfo getPersonalDepositLedger(Long tenantId, Long stayId) {
        return svc.getPersonalDepositLedger(tenantId, stayId);
    }
}
