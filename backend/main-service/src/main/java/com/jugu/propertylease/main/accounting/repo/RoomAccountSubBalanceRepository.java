package com.jugu.propertylease.main.accounting.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccountSubBalance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 子余额仓储。
 */
public interface RoomAccountSubBalanceRepository {
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
