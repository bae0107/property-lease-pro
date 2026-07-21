package com.jugu.propertylease.main.accounting.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 账户流水仓储。
 */
public interface RoomAccountEntryRepository {
    Long insert(Long roomAccountId, Long subBalanceId, String ownerType, Long ownerId,
                String entryType, BigDecimal amount, Long relatedBillId,
                OffsetDateTime occurredAt, String note);

    List<?> findByAccountId(Long roomAccountId, String ownerType, Long ownerId,
                             String entryType, LocalDate startDate, LocalDate endDate,
                             int offset, int limit);

    int countByAccountId(Long roomAccountId, String ownerType, Long ownerId,
                          String entryType, LocalDate startDate, LocalDate endDate);

    /** 按欠费方汇总 INSUFFICIENT 流水总额（正数=欠费金额），退房结算生成 SETTLEMENT_BILL 用。*/
    List<OwnerArrears> sumInsufficientByOwner(Long roomAccountId);
}
