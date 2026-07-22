package com.jugu.propertylease.main.accounting.delegate;

import com.jugu.propertylease.main.accounting.api.model.CreateBillResult;
import com.jugu.propertylease.main.accounting.service.AccountingQueryService;
import com.jugu.propertylease.main.accounting.service.AccountingService;
import com.jugu.propertylease.main.api.AccountingBillsApiDelegate;
import com.jugu.propertylease.main.api.AccountingDepositLedgersApiDelegate;
import com.jugu.propertylease.main.api.AccountingRechargeApiDelegate;
import com.jugu.propertylease.main.api.AccountingRoomAccountsApiDelegate;
import com.jugu.propertylease.main.api.AccountingSubBalancesApiDelegate;
import com.jugu.propertylease.main.api.model.BillPageResult;
import com.jugu.propertylease.main.api.model.BillQueryRequest;
import com.jugu.propertylease.main.api.model.DepositLedgerPageResult;
import com.jugu.propertylease.main.api.model.DepositLedgerQueryRequest;
import com.jugu.propertylease.main.api.model.EntryPageResult;
import com.jugu.propertylease.main.api.model.EntryQueryRequest;
import com.jugu.propertylease.main.api.model.RechargeRequest;
import com.jugu.propertylease.main.api.model.RechargeResult;
import com.jugu.propertylease.main.api.model.RoomAccountDetail;
import com.jugu.propertylease.main.api.model.RoomAccountEntry;
import com.jugu.propertylease.main.api.model.RoomAccountPageResult;
import com.jugu.propertylease.main.api.model.RoomAccountQueryRequest;
import com.jugu.propertylease.main.jooq.tables.pojos.Bill;
import com.jugu.propertylease.main.jooq.tables.pojos.DepositLedger;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccount;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccountSubBalance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

// ═════════════════════════════════════════════════════════════════════════════
// 房间账户 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class AccountingRoomAccountsApiDelegateImpl implements AccountingRoomAccountsApiDelegate {

    private final AccountingService svc;

    AccountingRoomAccountsApiDelegateImpl(AccountingService svc) {
        this.svc = svc;
    }

    @Override
    public RoomAccountPageResult queryRoomAccounts(RoomAccountQueryRequest req) {
        int page = req.getPageNo() != null ? req.getPageNo() : 1;
        int size = req.getPageSize() != null ? req.getPageSize() : 20;
        String status = req.getStatus() != null ? req.getStatus().getValue() : null;

        List<RoomAccount> accounts = svc.findRoomAccounts(req.getRoomId(),
                req.getContractId(), status, (page - 1) * size, size);
        int total = svc.countRoomAccounts(req.getRoomId(), req.getContractId(), status);

        return new RoomAccountPageResult()
                .items(accounts.stream().map(a ->
                        new com.jugu.propertylease.main.api.model.RoomAccount()
                                .id(a.getId())
                                .roomId(a.getRoomId())
                                .currentContractId(a.getCurrentContractId())
                                .status(com.jugu.propertylease.main.api.model.RoomAccount
                                        .StatusEnum.fromValue(a.getStatus()))
                ).toList())
                .total((long) total)
                .pageNo(page)
                .pageSize(size);
    }

    @Override
    public RoomAccountDetail getRoomAccount(Long id) {
        RoomAccount acc = svc.getRoomAccountById(id);
        return toDetail(acc, svc.getSubBalances(acc.getId()));
    }

    @Override
    public RoomAccountDetail getRoomAccountByRoom(Long roomId) {
        RoomAccount acc = svc.getRoomAccountByRoomId(roomId);
        return toDetail(acc, svc.getSubBalances(acc.getId()));
    }

    private RoomAccountDetail toDetail(RoomAccount acc, List<RoomAccountSubBalance> subs) {
        return new RoomAccountDetail()
                .id(acc.getId())
                .roomId(acc.getRoomId())
                .currentContractId(acc.getCurrentContractId())
                .status(RoomAccountDetail.StatusEnum.fromValue(acc.getStatus()))
                .subBalances(subs.stream().map(s ->
                        new com.jugu.propertylease.main.api.model.RoomAccountSubBalance()
                                .id(s.getId())
                                .ownerType(com.jugu.propertylease.main.api.model
                                        .RoomAccountSubBalance.OwnerTypeEnum.fromValue(s.getOwnerType()))
                                .ownerId(s.getOwnerId())
                                .availableBalance(s.getAvailableBalance().doubleValue())
                                .frozenBalance(s.getFrozenBalance().doubleValue())
                ).toList());
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 子余额流水 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class AccountingSubBalancesApiDelegateImpl implements AccountingSubBalancesApiDelegate {

    private final AccountingQueryService querySvc;

    AccountingSubBalancesApiDelegateImpl(AccountingQueryService querySvc) {
        this.querySvc = querySvc;
    }

    @Override
    public EntryPageResult queryRoomAccountEntries(Long id, EntryQueryRequest req) {
        int page = req.getPageNo() != null ? req.getPageNo() : 1;
        int size = req.getPageSize() != null ? req.getPageSize() : 20;
        String ownerType = req.getOwnerType() != null ? req.getOwnerType().getValue() : null;
        String entryType = req.getEntryType() != null ? req.getEntryType().getValue() : null;

        var entries = querySvc.getEntries(id, ownerType, req.getOwnerId(), entryType,
                req.getStartDate(), req.getEndDate(), (page - 1) * size, size);
        int total = querySvc.countEntries(id, ownerType, req.getOwnerId(), entryType,
                req.getStartDate(), req.getEndDate());

        return new EntryPageResult()
                .items(entries.stream().map(this::toEntryModel).toList())
                .total((long) total)
                .pageNo(page)
                .pageSize(size);
    }

    private RoomAccountEntry toEntryModel(Object raw) {
        var e = (com.jugu.propertylease.main.jooq.tables.pojos.RoomAccountEntry) raw;
        return new RoomAccountEntry()
                .id(e.getId())
                .ownerType(RoomAccountEntry.OwnerTypeEnum.fromValue(e.getOwnerType()))
                .ownerId(e.getOwnerId())
                .entryType(RoomAccountEntry.EntryTypeEnum.fromValue(e.getEntryType()))
                .amount(e.getAmount().doubleValue())
                .relatedBillId(e.getRelatedBillId())
                .occurredAt(e.getOccurredAt())
                .note(e.getNote());
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 充值 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class AccountingRechargeApiDelegateImpl implements AccountingRechargeApiDelegate {

    private final AccountingService svc;
    private final com.jugu.propertylease.main.contract.api.ContractQueryPort contractQueryPort;

    AccountingRechargeApiDelegateImpl(AccountingService svc,
                                      com.jugu.propertylease.main.contract.api.ContractQueryPort contractQueryPort) {
        this.svc = svc;
        this.contractQueryPort = contractQueryPort;
    }

    @Override
    public RechargeResult initiateRecharge(Long id, RechargeRequest req) {
        RoomAccount acc = svc.getRoomAccountById(id);
        String ownerType = req.getPayerType().getValue();
        // spec: room_account_sub_balance.owner_id = enterprise_id 或 tenant_id（不是 contractId）
        Long ownerId;
        if ("TENANT".equals(ownerType)) {
            ownerId = req.getPayerId();
        } else {
            ownerId = req.getPayerId() != null ? req.getPayerId()
                    : contractQueryPort.getContract(acc.getCurrentContractId()).enterpriseId();
        }
        CreateBillResult r = svc.initiateRecharge(
                acc.getRoomId(), id, BigDecimal.valueOf(req.getAmount()), ownerType, ownerId);
        return new RechargeResult()
                .billId(r.billId())
                .billingServiceBillId(r.billingServiceBillId())
                .paymentUrl(r.paymentUrl());
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 账单 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class AccountingBillsApiDelegateImpl implements AccountingBillsApiDelegate {

    private final AccountingQueryService querySvc;

    AccountingBillsApiDelegateImpl(AccountingQueryService querySvc) {
        this.querySvc = querySvc;
    }

    @Override
    public BillPageResult queryBills(BillQueryRequest req) {
        int page = req.getPageNo() != null ? req.getPageNo() : 1;
        int size = req.getPageSize() != null ? req.getPageSize() : 20;
        String ownerType = req.getBillOwnerType() != null ? req.getBillOwnerType().getValue() : null;
        String billType  = req.getBillType()      != null ? req.getBillType().getValue()      : null;
        String status    = req.getBillStatus()    != null ? req.getBillStatus().getValue()    : null;

        List<Bill> bills = querySvc.findBills(ownerType, req.getBillOwnerId(), status,
                billType, req.getStartDate(), req.getEndDate(), (page - 1) * size, size);
        int total = querySvc.countBills(ownerType, req.getBillOwnerId(), status,
                billType, req.getStartDate(), req.getEndDate());

        return new BillPageResult()
                .items(bills.stream().map(this::toModel).toList())
                .total((long) total)
                .pageNo(page)
                .pageSize(size);
    }

    @Override
    public com.jugu.propertylease.main.api.model.Bill getBill(Long id) {
        return toModel(querySvc.getBillById(id));
    }

    @Override
    public com.jugu.propertylease.main.api.model.Bill cancelBill(Long id) {
        querySvc.cancelBill(id);
        return toModel(querySvc.getBillById(id));
    }

    private com.jugu.propertylease.main.api.model.Bill toModel(Bill b) {
        return new com.jugu.propertylease.main.api.model.Bill()
                .id(b.getId())
                .billNo(b.getBillNo())
                .billType(com.jugu.propertylease.main.api.model.Bill.BillTypeEnum
                        .fromValue(b.getBillType()))
                .billOwnerType(com.jugu.propertylease.main.api.model.Bill.BillOwnerTypeEnum
                        .fromValue(b.getBillOwnerType()))
                .billOwnerId(b.getBillOwnerId())
                .contractId(b.getContractId())
                .roomId(b.getRoomId())
                .tenantId(b.getTenantId())
                .totalAmount(b.getTotalAmount().doubleValue())
                .billStatus(com.jugu.propertylease.main.api.model.Bill.BillStatusEnum
                        .fromValue(b.getBillStatus()))
                .billingServiceBillId(b.getBillingServiceBillId())
                .paidAt(b.getPaidAt())
                .createdAt(b.getCreatedAt());
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 押金台账 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class AccountingDepositLedgersApiDelegateImpl implements AccountingDepositLedgersApiDelegate {

    private final AccountingQueryService querySvc;

    AccountingDepositLedgersApiDelegateImpl(AccountingQueryService querySvc) {
        this.querySvc = querySvc;
    }

    @Override
    public DepositLedgerPageResult queryDepositLedgers(DepositLedgerQueryRequest req) {
        int page = req.getPageNo() != null ? req.getPageNo() : 1;
        int size = req.getPageSize() != null ? req.getPageSize() : 20;
        String type   = req.getDepositType() != null ? req.getDepositType().getValue() : null;
        String status = req.getStatus()      != null ? req.getStatus().getValue()      : null;

        List<DepositLedger> items = querySvc.findDepositLedgers(type, req.getOwnerId(),
                status, (page - 1) * size, size);
        int total = querySvc.countDepositLedgers(type, req.getOwnerId(), status);

        return new DepositLedgerPageResult()
                .items(items.stream().map(this::toModel).toList())
                .total((long) total)
                .pageNo(page)
                .pageSize(size);
    }

    @Override
    public com.jugu.propertylease.main.api.model.DepositLedger getDepositLedger(Long id) {
        return toModel(querySvc.getDepositLedgerById(id));
    }

    private com.jugu.propertylease.main.api.model.DepositLedger toModel(DepositLedger d) {
        return new com.jugu.propertylease.main.api.model.DepositLedger()
                .id(d.getId())
                .depositType(com.jugu.propertylease.main.api.model.DepositLedger.DepositTypeEnum
                        .fromValue(d.getDepositType()))
                .ownerType(com.jugu.propertylease.main.api.model.DepositLedger.OwnerTypeEnum
                        .fromValue(d.getOwnerType()))
                .ownerId(d.getOwnerId())
                .contractId(d.getContractId())
                .currentStayId(d.getCurrentStayId())
                .originalAmount(d.getOriginalAmount().doubleValue())
                .occupiedAmount(d.getOccupiedAmount() != null
                        ? d.getOccupiedAmount().doubleValue() : 0.0)
                .refundableAmount(d.getRefundableAmount() != null
                        ? d.getRefundableAmount().doubleValue() : null)
                .status(com.jugu.propertylease.main.api.model.DepositLedger.StatusEnum
                        .fromValue(d.getStatus()))
                .relatedBillId(d.getRelatedBillId());
    }
}
