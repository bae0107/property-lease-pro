package com.jugu.propertylease.main.accounting.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.Bill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 账单仓储。
 */
public interface BillRepository {
    Long insert(String billNo, String billType, String billOwnerType, Long billOwnerId,
                Long contractId, Long roomId, Long tenantId, BigDecimal totalAmount,
                Long createdBy, OffsetDateTime now);

    Optional<Bill> findBillById(Long id);

    Optional<Bill> findByBillingServiceBillId(Long billingServiceBillId);

    void updateStatus(Long id, String status, Long billingServiceBillId,
                      OffsetDateTime paidAt, OffsetDateTime now);

    void cancel(Long id, OffsetDateTime now);

    List<Bill> findByOwner(String ownerType, Long ownerId, String status,
                           String billType, LocalDate start, LocalDate end,
                           int offset, int limit);

    int countByOwner(String ownerType, Long ownerId, String status,
                     String billType, LocalDate start, LocalDate end);

    /** 幂等判重：同合同同类型在 [from, to) 内是否已有 PENDING/PAID 账单。*/
    boolean existsActiveBillForPeriod(Long contractId, String billType,
                                      OffsetDateTime from, OffsetDateTime to);
}
