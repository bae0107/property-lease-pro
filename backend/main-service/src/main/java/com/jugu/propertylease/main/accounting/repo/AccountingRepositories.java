package com.jugu.propertylease.main.accounting.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.Bill;
import com.jugu.propertylease.main.jooq.tables.pojos.DepositLedger;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccount;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccountSubBalance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

// ══════════════════════════════════════════════════════════════════════════════
// 房间账户
// ══════════════════════════════════════════════════════════════════════════════
interface RoomAccountRepository {
    /** 查找或创建房间账户（room_id 唯一，confirmContract 时激活）。*/
    Long upsertRoomAccount(Long roomId, Long contractId, OffsetDateTime now);

    Optional<RoomAccount> findByRoomId(Long roomId);

    Optional<RoomAccount> findById(Long id);

    void updateStatus(Long id, String status, OffsetDateTime now);

    void updateContractId(Long id, Long contractId, OffsetDateTime now);
}

// ══════════════════════════════════════════════════════════════════════════════
// 子余额
// ══════════════════════════════════════════════════════════════════════════════
interface RoomAccountSubBalanceRepository {
    /** 查找子余额，不存在则创建（幂等）。*/
    RoomAccountSubBalance findOrCreate(Long roomAccountId, String ownerType,
                                       Long ownerId, OffsetDateTime now);

    Optional<RoomAccountSubBalance> find(Long roomAccountId, String ownerType, Long ownerId);

    List<RoomAccountSubBalance> findAllByAccountId(Long roomAccountId);

    /** SELECT FOR UPDATE（扣款/充值前加锁）。*/
    Optional<RoomAccountSubBalance> findForUpdate(Long id);

    void updateBalance(Long id, BigDecimal availableBalance,
                       BigDecimal frozenBalance, OffsetDateTime now);
}

// ══════════════════════════════════════════════════════════════════════════════
// 账户流水
// ══════════════════════════════════════════════════════════════════════════════
interface RoomAccountEntryRepository {
    Long insert(Long roomAccountId, Long subBalanceId, String ownerType, Long ownerId,
                String entryType, BigDecimal amount, Long relatedBillId,
                OffsetDateTime occurredAt, String note);

    List<?> findByAccountId(Long roomAccountId, String ownerType, Long ownerId,
                             String entryType, LocalDate startDate, LocalDate endDate,
                             int offset, int limit);

    int countByAccountId(Long roomAccountId, String ownerType, Long ownerId,
                          String entryType, LocalDate startDate, LocalDate endDate);
}

// ══════════════════════════════════════════════════════════════════════════════
// 账单
// ══════════════════════════════════════════════════════════════════════════════
interface BillRepository {
    Long insert(String billNo, String billType, String billOwnerType, Long billOwnerId,
                Long contractId, Long roomId, Long tenantId, BigDecimal totalAmount,
                Long createdBy, OffsetDateTime now);

    Optional<Bill> findById(Long id);

    Optional<Bill> findByBillingServiceBillId(Long billingServiceBillId);

    void updateStatus(Long id, String status, Long billingServiceBillId,
                      OffsetDateTime paidAt, OffsetDateTime now);

    void cancel(Long id, OffsetDateTime now);

    List<Bill> findByOwner(String ownerType, Long ownerId, String status,
                           String billType, LocalDate start, LocalDate end,
                           int offset, int limit);

    int countByOwner(String ownerType, Long ownerId, String status,
                     String billType, LocalDate start, LocalDate end);
}

// ══════════════════════════════════════════════════════════════════════════════
// 押金台账
// ══════════════════════════════════════════════════════════════════════════════
interface DepositLedgerRepository {
    Long insert(String depositType, String ownerType, Long ownerId,
                Long contractId, Long currentStayId, BigDecimal originalAmount,
                String status, Long relatedBillId, OffsetDateTime now);

    Optional<DepositLedger> findByOwnerAndContract(String depositType,
                                                    Long ownerId, Long contractId);

    Optional<DepositLedger> findPersonalByStay(Long tenantId, Long stayId);

    void updateCurrentStay(Long id, Long newStayId, OffsetDateTime now);

    void updateStatus(Long id, String status, BigDecimal refundableAmount, OffsetDateTime now);

    List<DepositLedger> findByContractId(Long contractId, String depositType);

    List<DepositLedger> findAll(String depositType, Long ownerId, String status,
                                int offset, int limit);

    int countAll(String depositType, Long ownerId, String status);
}

// ══════════════════════════════════════════════════════════════════════════════
// 全局配置
// ══════════════════════════════════════════════════════════════════════════════
interface SystemConfigRepository {
    Optional<String> getValue(String key);
}
