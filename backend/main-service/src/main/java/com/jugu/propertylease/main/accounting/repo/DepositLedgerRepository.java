package com.jugu.propertylease.main.accounting.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.DepositLedger;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 押金台账仓储。
 */
public interface DepositLedgerRepository {
    Long insert(String depositType, String ownerType, Long ownerId,
                Long contractId, Long currentStayId, BigDecimal originalAmount,
                String status, Long relatedBillId, OffsetDateTime now);

    Optional<DepositLedger> findByOwnerAndContract(String depositType,
                                                    Long ownerId, Long contractId);

    /** 命名避开 RoomAccountRepository.findById（同实现类，返回类型冲突）。*/
    Optional<DepositLedger> findLedgerById(Long id);

    Optional<DepositLedger> findPersonalByStay(Long tenantId, Long stayId);

    void updateCurrentStay(Long id, Long newStayId, OffsetDateTime now);

    void updateStatus(Long id, String status, BigDecimal refundableAmount, OffsetDateTime now);

    List<DepositLedger> findByContractId(Long contractId, String depositType);

    List<DepositLedger> findAll(String depositType, Long ownerId, String status,
                                int offset, int limit);

    int countAll(String depositType, Long ownerId, String status);
}
