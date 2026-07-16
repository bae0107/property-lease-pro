package com.jugu.propertylease.main.accounting.delegate;

import com.jugu.propertylease.main.accounting.api.model.CreateBillResult;
import com.jugu.propertylease.main.accounting.service.AccountingService;
import com.jugu.propertylease.main.api.AccountingBillsApiDelegate;
import com.jugu.propertylease.main.api.AccountingDepositLedgersApiDelegate;
import com.jugu.propertylease.main.api.AccountingRoomAccountsApiDelegate;
import com.jugu.propertylease.main.api.model.*;
import com.jugu.propertylease.main.jooq.tables.pojos.Bill;
import com.jugu.propertylease.main.jooq.tables.pojos.DepositLedger;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccount;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccountSubBalance;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
        // 分页查询由 repo 支撑，此处简化：通过 roomId 或 contractId 过滤
        // 完整分页实现依赖 repo.findAllByFilter()，当前骨架先返回空
        return new RoomAccountPageResult().items(List.of()).total(0);
    }

    @Override
    public RoomAccountDetail getRoomAccount(Long id) {
        RoomAccount acc = svc.getRoomAccountById(id);
        List<RoomAccountSubBalance> subs = svc.getSubBalances(id);
        return toDetail(acc, subs);
    }

    @Override
    public RoomAccountDetail getRoomAccountByRoom(Long roomId) {
        RoomAccount acc = svc.getRoomAccountByRoomId(roomId);
        List<RoomAccountSubBalance> subs = svc.getSubBalances(acc.getId());
        return toDetail(acc, subs);
    }

    @Override
    public EntryPageResult queryRoomAccountEntries(Long id, EntryQueryRequest req) {
        int page = req.getPage() != null ? req.getPage() : 1;
        int size = req.getSize() != null ? req.getSize() : 20;
        String ownerType = req.getOwnerType() != null ? req.getOwnerType().getValue() : null;
        String entryType = req.getEntryType() != null ? req.getEntryType().getValue() : null;

        var entries = svc.getEntries(id, ownerType, req.getOwnerId(), entryType,
                req.getStartDate(), req.getEndDate(), (page - 1) * size, size);
        int total = svc.countEntries(id, ownerType, req.getOwnerId(), entryType,
                req.getStartDate(), req.getEndDate());
        return new EntryPageResult().items(entries.stream().map(this::toEntryModel).toList())
                .total(total).page(page).size(size);
    }

    @Override
    public RechargeResult initiateRecharge(Long id, RechargeRequest req) {
        RoomAccount acc = svc.getRoomAccountById(id);
        String ownerType = req.getPayerType().getValue();
        Long ownerId = "TENANT".equals(ownerType) && req.getPayerId() != null
                ? req.getPayerId() : acc.getCurrentContractId();
        CreateBillResult r = svc.initiateRecharge(
                acc.getRoomId(), id, req.getAmount(), ownerType, ownerId);
        return new RechargeResult().billId(r.billId())
                .billingServiceBillId(r.billingServiceBillId())
                .paymentUrl(r.paymentUrl());
    }

    private RoomAccountDetail toDetail(
            RoomAccount acc, List<RoomAccountSubBalance> subs) {
        return new RoomAccountDetail()
                .id(acc.getId())
                .roomId(acc.getRoomId())
                .currentContractId(acc.getCurrentContractId())
                .status(com.jugu.propertylease.main.api.model.RoomAccount.StatusEnum
                        .fromValue(acc.getStatus()))
                .subBalances(subs.stream().map(s -> new RoomAccountSubBalance_()
                        .id(s.getId())
                        .ownerType(RoomAccountSubBalance_.OwnerTypeEnum.fromValue(s.getOwnerType()))
                        .ownerId(s.getOwnerId())
                        .availableBalance(s.getAvailableBalance().doubleValue())
                        .frozenBalance(s.getFrozenBalance().doubleValue())
                ).toList());
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
// 账单 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class AccountingBillsApiDelegateImpl implements AccountingBillsApiDelegate {

    private final AccountingService svc;

    AccountingBillsApiDelegateImpl(AccountingService svc) {
        this.svc = svc;
    }

    @Override
    public BillPageResult queryBills(BillQueryRequest req) {
        int page = req.getPage() != null ? req.getPage() : 1;
        int size = req.getSize() != null ? req.getSize() : 20;
        String ownerType = req.getBillOwnerType() != null ? req.getBillOwnerType().getValue() : null;
        String billType  = req.getBillType()      != null ? req.getBillType().getValue()      : null;
        String status    = req.getBillStatus()     != null ? req.getBillStatus().getValue()    : null;

        List<Bill> bills = svc.findBills(ownerType, req.getBillOwnerId(), status,
                billType, req.getStartDate(), req.getEndDate(), (page - 1) * size, size);
        int total = svc.countBills(ownerType, req.getBillOwnerId(), status,
                billType, req.getStartDate(), req.getEndDate());

        return new BillPageResult().items(bills.stream().map(this::toModel).toList())
                .total(total).page(page).size(size);
    }

    @Override
    public com.jugu.propertylease.main.api.model.Bill getBill(Long id) {
        return toModel(svc.getBillById(id));
    }

    @Override
    public com.jugu.propertylease.main.api.model.Bill cancelBill(Long id) {
        svc.cancelBill(id);
        return toModel(svc.getBillById(id));
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
                .paidAt(b.getPaidAt());
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 押金台账 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class AccountingDepositLedgersApiDelegateImpl implements AccountingDepositLedgersApiDelegate {

    private final AccountingService svc;

    AccountingDepositLedgersApiDelegateImpl(AccountingService svc) {
        this.svc = svc;
    }

    @Override
    public DepositLedgerPageResult queryDepositLedgers(DepositLedgerQueryRequest req) {
        int page = req.getPage() != null ? req.getPage() : 1;
        int size = req.getSize() != null ? req.getSize() : 20;
        String type   = req.getDepositType() != null ? req.getDepositType().getValue() : null;
        String status = req.getStatus()      != null ? req.getStatus().getValue()      : null;

        List<DepositLedger> items = svc.findDepositLedgers(type, req.getOwnerId(),
                status, (page - 1) * size, size);
        int total = svc.countDepositLedgers(type, req.getOwnerId(), status);

        return new DepositLedgerPageResult()
                .items(items.stream().map(this::toModel).toList())
                .total(total).page(page).size(size);
    }

    @Override
    public com.jugu.propertylease.main.api.model.DepositLedger getDepositLedger(Long id) {
        return toModel(svc.getDepositLedgerById(id));
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
