package com.jugu.propertylease.main.accounting.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.*;
import static org.jooq.impl.DSL.trueCondition;

import com.jugu.propertylease.main.accounting.repo.*;
import com.jugu.propertylease.main.jooq.tables.pojos.*;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 所有 accounting 仓储的 jOOQ 实现，聚合在单个 @Repository Bean 中，
 * 由 AccountingService 通过各接口方法调用。
 */
@Repository
public class JooqAccountingRepository
        implements RoomAccountRepository,
                   RoomAccountSubBalanceRepository,
                   RoomAccountEntryRepository,
                   BillRepository,
                   DepositLedgerRepository,
                   SystemConfigRepository {

    private final DSLContext dsl;

    public JooqAccountingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ── RoomAccount ─────────────────────────────────────────────────────────

    @Override
    public Long upsertRoomAccount(Long roomId, Long contractId, OffsetDateTime now) {
        // 已存在则更新 contractId，不存在则插入
        return dsl.selectFrom(ROOM_ACCOUNT)
                .where(ROOM_ACCOUNT.ROOM_ID.eq(roomId))
                .fetchOptionalInto(RoomAccount.class)
                .map(existing -> {
                    dsl.update(ROOM_ACCOUNT)
                            .set(ROOM_ACCOUNT.CURRENT_CONTRACT_ID, contractId)
                            .set(ROOM_ACCOUNT.STATUS, "ACTIVE")
                            .set(ROOM_ACCOUNT.UPDATED_AT, now)
                            .where(ROOM_ACCOUNT.ID.eq(existing.getId()))
                            .execute();
                    return existing.getId();
                })
                .orElseGet(() -> dsl.insertInto(ROOM_ACCOUNT)
                        .set(ROOM_ACCOUNT.ROOM_ID, roomId)
                        .set(ROOM_ACCOUNT.CURRENT_CONTRACT_ID, contractId)
                        .set(ROOM_ACCOUNT.STATUS, "ACTIVE")
                        .set(ROOM_ACCOUNT.CREATED_AT, now)
                        .set(ROOM_ACCOUNT.UPDATED_AT, now)
                        .returning(ROOM_ACCOUNT.ID)
                        .fetchOne(ROOM_ACCOUNT.ID));
    }

    @Override
    public Optional<RoomAccount> findByRoomId(Long roomId) {
        return Optional.ofNullable(dsl.selectFrom(ROOM_ACCOUNT)
                .where(ROOM_ACCOUNT.ROOM_ID.eq(roomId))
                .fetchOneInto(RoomAccount.class));
    }

    @Override
    public Optional<RoomAccount> findById(Long id) {
        return Optional.ofNullable(dsl.selectFrom(ROOM_ACCOUNT)
                .where(ROOM_ACCOUNT.ID.eq(id))
                .fetchOneInto(RoomAccount.class));
    }

    @Override
    public List<RoomAccount> findActiveByContractId(Long contractId) {
        return dsl.selectFrom(ROOM_ACCOUNT)
                .where(ROOM_ACCOUNT.CURRENT_CONTRACT_ID.eq(contractId))
                .and(ROOM_ACCOUNT.STATUS.eq("ACTIVE"))
                .fetchInto(RoomAccount.class);
    }

    @Override
    public void updateStatus(Long id, String status, OffsetDateTime now) {
        dsl.update(ROOM_ACCOUNT)
                .set(ROOM_ACCOUNT.STATUS, status)
                .set(ROOM_ACCOUNT.UPDATED_AT, now)
                .where(ROOM_ACCOUNT.ID.eq(id))
                .execute();
    }

    @Override
    public void updateContractId(Long id, Long contractId, OffsetDateTime now) {
        dsl.update(ROOM_ACCOUNT)
                .set(ROOM_ACCOUNT.CURRENT_CONTRACT_ID, contractId)
                .set(ROOM_ACCOUNT.UPDATED_AT, now)
                .where(ROOM_ACCOUNT.ID.eq(id))
                .execute();
    }

    // ── RoomAccountSubBalance ───────────────────────────────────────────────

    @Override
    public RoomAccountSubBalance findOrCreate(Long roomAccountId, String ownerType,
                                               Long ownerId, OffsetDateTime now) {
        return find(roomAccountId, ownerType, ownerId)
                .orElseGet(() -> {
                    Long id = dsl.insertInto(ROOM_ACCOUNT_SUB_BALANCE)
                            .set(ROOM_ACCOUNT_SUB_BALANCE.ROOM_ACCOUNT_ID, roomAccountId)
                            .set(ROOM_ACCOUNT_SUB_BALANCE.OWNER_TYPE, ownerType)
                            .set(ROOM_ACCOUNT_SUB_BALANCE.OWNER_ID, ownerId)
                            .set(ROOM_ACCOUNT_SUB_BALANCE.AVAILABLE_BALANCE, BigDecimal.ZERO)
                            .set(ROOM_ACCOUNT_SUB_BALANCE.FROZEN_BALANCE, BigDecimal.ZERO)
                            .set(ROOM_ACCOUNT_SUB_BALANCE.CREATED_AT, now)
                            .set(ROOM_ACCOUNT_SUB_BALANCE.UPDATED_AT, now)
                            .onDuplicateKeyIgnore()
                            .returning(ROOM_ACCOUNT_SUB_BALANCE.ID)
                            .fetchOne(ROOM_ACCOUNT_SUB_BALANCE.ID);
                    return find(roomAccountId, ownerType, ownerId).orElseThrow();
                });
    }

    @Override
    public Optional<RoomAccountSubBalance> find(Long roomAccountId, String ownerType, Long ownerId) {
        return Optional.ofNullable(
                dsl.selectFrom(ROOM_ACCOUNT_SUB_BALANCE)
                        .where(ROOM_ACCOUNT_SUB_BALANCE.ROOM_ACCOUNT_ID.eq(roomAccountId))
                        .and(ROOM_ACCOUNT_SUB_BALANCE.OWNER_TYPE.eq(ownerType))
                        .and(ROOM_ACCOUNT_SUB_BALANCE.OWNER_ID.eq(ownerId))
                        .fetchOneInto(RoomAccountSubBalance.class));
    }

    @Override
    public List<RoomAccountSubBalance> findAllByAccountId(Long roomAccountId) {
        return dsl.selectFrom(ROOM_ACCOUNT_SUB_BALANCE)
                .where(ROOM_ACCOUNT_SUB_BALANCE.ROOM_ACCOUNT_ID.eq(roomAccountId))
                .fetchInto(RoomAccountSubBalance.class);
    }

    @Override
    public Optional<RoomAccountSubBalance> findForUpdate(Long id) {
        return Optional.ofNullable(
                dsl.selectFrom(ROOM_ACCOUNT_SUB_BALANCE)
                        .where(ROOM_ACCOUNT_SUB_BALANCE.ID.eq(id))
                        .forUpdate()
                        .fetchOneInto(RoomAccountSubBalance.class));
    }

    @Override
    public void updateBalance(Long id, BigDecimal availableBalance,
                              BigDecimal frozenBalance, OffsetDateTime now) {
        dsl.update(ROOM_ACCOUNT_SUB_BALANCE)
                .set(ROOM_ACCOUNT_SUB_BALANCE.AVAILABLE_BALANCE, availableBalance)
                .set(ROOM_ACCOUNT_SUB_BALANCE.FROZEN_BALANCE, frozenBalance)
                .set(ROOM_ACCOUNT_SUB_BALANCE.UPDATED_AT, now)
                .where(ROOM_ACCOUNT_SUB_BALANCE.ID.eq(id))
                .execute();
    }

    // ── RoomAccountEntry ────────────────────────────────────────────────────

    @Override
    public Long insert(Long roomAccountId, Long subBalanceId, String ownerType, Long ownerId,
                       String entryType, BigDecimal amount, Long relatedBillId,
                       OffsetDateTime occurredAt, String note) {
        return dsl.insertInto(ROOM_ACCOUNT_ENTRY)
                .set(ROOM_ACCOUNT_ENTRY.ROOM_ACCOUNT_ID, roomAccountId)
                .set(ROOM_ACCOUNT_ENTRY.SUB_BALANCE_ID, subBalanceId)
                .set(ROOM_ACCOUNT_ENTRY.OWNER_TYPE, ownerType)
                .set(ROOM_ACCOUNT_ENTRY.OWNER_ID, ownerId)
                .set(ROOM_ACCOUNT_ENTRY.ENTRY_TYPE, entryType)
                .set(ROOM_ACCOUNT_ENTRY.AMOUNT, amount)
                .set(ROOM_ACCOUNT_ENTRY.RELATED_BILL_ID, relatedBillId)
                .set(ROOM_ACCOUNT_ENTRY.OCCURRED_AT, occurredAt)
                .set(ROOM_ACCOUNT_ENTRY.NOTE, note)
                .returning(ROOM_ACCOUNT_ENTRY.ID)
                .fetchOne(ROOM_ACCOUNT_ENTRY.ID);
    }

    @Override
    public List<?> findByAccountId(Long accountId, String ownerType, Long ownerId,
                                    String entryType, LocalDate startDate, LocalDate endDate,
                                    int offset, int limit) {
        Condition cond = ROOM_ACCOUNT_ENTRY.ROOM_ACCOUNT_ID.eq(accountId);
        if (ownerType != null) cond = cond.and(ROOM_ACCOUNT_ENTRY.OWNER_TYPE.eq(ownerType));
        if (ownerId   != null) cond = cond.and(ROOM_ACCOUNT_ENTRY.OWNER_ID.eq(ownerId));
        if (entryType != null) cond = cond.and(ROOM_ACCOUNT_ENTRY.ENTRY_TYPE.eq(entryType));
        if (startDate != null) cond = cond.and(
                ROOM_ACCOUNT_ENTRY.OCCURRED_AT.ge(startDate.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)));
        if (endDate   != null) cond = cond.and(
                ROOM_ACCOUNT_ENTRY.OCCURRED_AT.lt(endDate.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)));

        return dsl.selectFrom(ROOM_ACCOUNT_ENTRY)
                .where(cond)
                .orderBy(ROOM_ACCOUNT_ENTRY.OCCURRED_AT.desc())
                .limit(limit).offset(offset)
                .fetchInto(RoomAccountEntry.class);
    }

    @Override
    public int countByAccountId(Long accountId, String ownerType, Long ownerId,
                                 String entryType, LocalDate startDate, LocalDate endDate) {
        Condition cond = ROOM_ACCOUNT_ENTRY.ROOM_ACCOUNT_ID.eq(accountId);
        if (ownerType != null) cond = cond.and(ROOM_ACCOUNT_ENTRY.OWNER_TYPE.eq(ownerType));
        if (ownerId   != null) cond = cond.and(ROOM_ACCOUNT_ENTRY.OWNER_ID.eq(ownerId));
        if (entryType != null) cond = cond.and(ROOM_ACCOUNT_ENTRY.ENTRY_TYPE.eq(entryType));
        return dsl.fetchCount(ROOM_ACCOUNT_ENTRY, cond);
    }

    @Override
    public List<OwnerArrears> sumInsufficientByOwner(Long roomAccountId) {
        var arrears = DSL.sum(ROOM_ACCOUNT_ENTRY.AMOUNT.neg()).as("arrears");
        return dsl.select(ROOM_ACCOUNT_ENTRY.OWNER_TYPE, ROOM_ACCOUNT_ENTRY.OWNER_ID, arrears)
                .from(ROOM_ACCOUNT_ENTRY)
                .where(ROOM_ACCOUNT_ENTRY.ROOM_ACCOUNT_ID.eq(roomAccountId))
                .and(ROOM_ACCOUNT_ENTRY.ENTRY_TYPE.eq("INSUFFICIENT"))
                .groupBy(ROOM_ACCOUNT_ENTRY.OWNER_TYPE, ROOM_ACCOUNT_ENTRY.OWNER_ID)
                .fetch(r -> new OwnerArrears(
                        r.get(ROOM_ACCOUNT_ENTRY.OWNER_TYPE),
                        r.get(ROOM_ACCOUNT_ENTRY.OWNER_ID),
                        r.get(arrears)));
    }

    // ── Bill ────────────────────────────────────────────────────────────────

    @Override
    public Long insert(String billNo, String billType, String billOwnerType, Long billOwnerId,
                       Long contractId, Long roomId, Long tenantId, BigDecimal totalAmount,
                       Long createdBy, OffsetDateTime now) {
        return dsl.insertInto(BILL)
                .set(BILL.BILL_NO, billNo)
                .set(BILL.BILL_TYPE, billType)
                .set(BILL.BILL_OWNER_TYPE, billOwnerType)
                .set(BILL.BILL_OWNER_ID, billOwnerId)
                .set(BILL.CONTRACT_ID, contractId)
                .set(BILL.ROOM_ID, roomId)
                .set(BILL.TENANT_ID, tenantId)
                .set(BILL.TOTAL_AMOUNT, totalAmount)
                .set(BILL.BILL_STATUS, "PENDING")
                .set(BILL.CREATED_BY, createdBy)
                .set(BILL.CREATED_AT, now)
                .set(BILL.UPDATED_AT, now)
                .returning(BILL.ID)
                .fetchOne(BILL.ID);
    }

    @Override
    public Optional<Bill> findBillById(Long id) {
        return Optional.ofNullable(dsl.selectFrom(BILL)
                .where(BILL.ID.eq(id))
                .fetchOneInto(Bill.class));
    }

    @Override
    public Optional<Bill> findByBillingServiceBillId(Long billingServiceBillId) {
        return Optional.ofNullable(dsl.selectFrom(BILL)
                .where(BILL.BILLING_SERVICE_BILL_ID.eq(billingServiceBillId))
                .fetchOneInto(Bill.class));
    }

    @Override
    public void updateStatus(Long id, String status, Long billingServiceBillId,
                             OffsetDateTime paidAt, OffsetDateTime now) {
        dsl.update(BILL)
                .set(BILL.BILL_STATUS, status)
                .set(BILL.BILLING_SERVICE_BILL_ID, billingServiceBillId)
                .set(BILL.PAID_AT, paidAt)
                .set(BILL.UPDATED_AT, now)
                .where(BILL.ID.eq(id))
                .execute();
    }

    @Override
    public void cancel(Long id, OffsetDateTime now) {
        dsl.update(BILL)
                .set(BILL.BILL_STATUS, "CANCELLED")
                .set(BILL.UPDATED_AT, now)
                .where(BILL.ID.eq(id))
                .and(BILL.BILL_STATUS.eq("PENDING"))
                .execute();
    }

    @Override
    public List<Bill> findByOwner(String ownerType, Long ownerId, String status,
                                   String billType, LocalDate start, LocalDate end,
                                   int offset, int limit) {
        Condition cond = trueCondition();
        if (ownerType != null) cond = cond.and(BILL.BILL_OWNER_TYPE.eq(ownerType));
        if (ownerId   != null) cond = cond.and(BILL.BILL_OWNER_ID.eq(ownerId));
        if (status    != null) cond = cond.and(BILL.BILL_STATUS.eq(status));
        if (billType  != null) cond = cond.and(BILL.BILL_TYPE.eq(billType));
        if (start     != null) cond = cond.and(
                BILL.CREATED_AT.ge(start.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)));
        if (end       != null) cond = cond.and(
                BILL.CREATED_AT.lt(end.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)));
        return dsl.selectFrom(BILL).where(cond)
                .orderBy(BILL.CREATED_AT.desc())
                .limit(limit).offset(offset)
                .fetchInto(Bill.class);
    }

    @Override
    public int countByOwner(String ownerType, Long ownerId, String status,
                             String billType, LocalDate start, LocalDate end) {
        Condition cond = trueCondition();
        if (ownerType != null) cond = cond.and(BILL.BILL_OWNER_TYPE.eq(ownerType));
        if (ownerId   != null) cond = cond.and(BILL.BILL_OWNER_ID.eq(ownerId));
        if (status    != null) cond = cond.and(BILL.BILL_STATUS.eq(status));
        if (billType  != null) cond = cond.and(BILL.BILL_TYPE.eq(billType));
        return dsl.fetchCount(BILL, cond);
    }

    // ── DepositLedger ───────────────────────────────────────────────────────

    @Override
    public Long insert(String depositType, String ownerType, Long ownerId,
                       Long contractId, Long currentStayId, BigDecimal originalAmount,
                       String status, Long relatedBillId, OffsetDateTime now) {
        return dsl.insertInto(DEPOSIT_LEDGER)
                .set(DEPOSIT_LEDGER.DEPOSIT_TYPE, depositType)
                .set(DEPOSIT_LEDGER.OWNER_TYPE, ownerType)
                .set(DEPOSIT_LEDGER.OWNER_ID, ownerId)
                .set(DEPOSIT_LEDGER.CONTRACT_ID, contractId)
                .set(DEPOSIT_LEDGER.CURRENT_STAY_ID, currentStayId)
                .set(DEPOSIT_LEDGER.ORIGINAL_AMOUNT, originalAmount)
                .set(DEPOSIT_LEDGER.OCCUPIED_AMOUNT, BigDecimal.ZERO)
                .set(DEPOSIT_LEDGER.STATUS, status)
                .set(DEPOSIT_LEDGER.RELATED_BILL_ID, relatedBillId)
                .set(DEPOSIT_LEDGER.CREATED_AT, now)
                .set(DEPOSIT_LEDGER.UPDATED_AT, now)
                .returning(DEPOSIT_LEDGER.ID)
                .fetchOne(DEPOSIT_LEDGER.ID);
    }

    @Override
    public Optional<DepositLedger> findByOwnerAndContract(String depositType,
                                                           Long ownerId, Long contractId) {
        return Optional.ofNullable(dsl.selectFrom(DEPOSIT_LEDGER)
                .where(DEPOSIT_LEDGER.DEPOSIT_TYPE.eq(depositType))
                .and(DEPOSIT_LEDGER.OWNER_ID.eq(ownerId))
                .and(DEPOSIT_LEDGER.CONTRACT_ID.eq(contractId))
                .fetchOneInto(DepositLedger.class));
    }

    @Override
    public Optional<DepositLedger> findPersonalByStay(Long tenantId, Long stayId) {
        return Optional.ofNullable(dsl.selectFrom(DEPOSIT_LEDGER)
                .where(DEPOSIT_LEDGER.DEPOSIT_TYPE.eq("PERSONAL"))
                .and(DEPOSIT_LEDGER.OWNER_ID.eq(tenantId))
                .and(DEPOSIT_LEDGER.CURRENT_STAY_ID.eq(stayId))
                .fetchOneInto(DepositLedger.class));
    }

    @Override
    public void updateCurrentStay(Long id, Long newStayId, OffsetDateTime now) {
        dsl.update(DEPOSIT_LEDGER)
                .set(DEPOSIT_LEDGER.CURRENT_STAY_ID, newStayId)
                .set(DEPOSIT_LEDGER.UPDATED_AT, now)
                .where(DEPOSIT_LEDGER.ID.eq(id))
                .execute();
    }

    @Override
    public void updateStatus(Long id, String status,
                             BigDecimal refundableAmount, OffsetDateTime now) {
        dsl.update(DEPOSIT_LEDGER)
                .set(DEPOSIT_LEDGER.STATUS, status)
                .set(DEPOSIT_LEDGER.REFUNDABLE_AMOUNT, refundableAmount)
                .set(DEPOSIT_LEDGER.UPDATED_AT, now)
                .where(DEPOSIT_LEDGER.ID.eq(id))
                .execute();
    }

    @Override
    public List<DepositLedger> findByContractId(Long contractId, String depositType) {
        Condition cond = DEPOSIT_LEDGER.CONTRACT_ID.eq(contractId);
        if (depositType != null) cond = cond.and(DEPOSIT_LEDGER.DEPOSIT_TYPE.eq(depositType));
        return dsl.selectFrom(DEPOSIT_LEDGER).where(cond).fetchInto(DepositLedger.class);
    }

    @Override
    public Optional<DepositLedger> findLedgerById(Long id) {
        return Optional.ofNullable(dsl.selectFrom(DEPOSIT_LEDGER)
                .where(DEPOSIT_LEDGER.ID.eq(id))
                .fetchOneInto(DepositLedger.class));
    }

    @Override
    public List<DepositLedger> findAll(String depositType, Long ownerId, String status,
                                       int offset, int limit) {
        Condition cond = trueCondition();
        if (depositType != null) cond = cond.and(DEPOSIT_LEDGER.DEPOSIT_TYPE.eq(depositType));
        if (ownerId     != null) cond = cond.and(DEPOSIT_LEDGER.OWNER_ID.eq(ownerId));
        if (status      != null) cond = cond.and(DEPOSIT_LEDGER.STATUS.eq(status));
        return dsl.selectFrom(DEPOSIT_LEDGER).where(cond)
                .orderBy(DEPOSIT_LEDGER.CREATED_AT.desc())
                .limit(limit).offset(offset)
                .fetchInto(DepositLedger.class);
    }

    @Override
    public int countAll(String depositType, Long ownerId, String status) {
        Condition cond = trueCondition();
        if (depositType != null) cond = cond.and(DEPOSIT_LEDGER.DEPOSIT_TYPE.eq(depositType));
        if (ownerId     != null) cond = cond.and(DEPOSIT_LEDGER.OWNER_ID.eq(ownerId));
        if (status      != null) cond = cond.and(DEPOSIT_LEDGER.STATUS.eq(status));
        return dsl.fetchCount(DEPOSIT_LEDGER, cond);
    }

    // ── SystemConfig ────────────────────────────────────────────────────────

    @Override
    public Optional<String> getValue(String key) {
        return Optional.ofNullable(
                dsl.select(SYSTEM_CONFIG.CONFIG_VALUE)
                        .from(SYSTEM_CONFIG)
                        .where(SYSTEM_CONFIG.CONFIG_KEY.eq(key))
                        .fetchOneInto(String.class));
    }
}
