package com.jugu.propertylease.main.accounting.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccount;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 房间账户仓储。
 */
public interface RoomAccountRepository {
    /** 查找或创建房间账户（room_id 唯一，confirmContract 时激活）。*/
    Long upsertRoomAccount(Long roomId, Long contractId, OffsetDateTime now);

    Optional<RoomAccount> findByRoomId(Long roomId);

    Optional<RoomAccount> findById(Long id);

    /** 按当前合同查找所有 ACTIVE 房间账户（settleFullReturn 用）。*/
    List<RoomAccount> findActiveByContractId(Long contractId);

    void updateStatus(Long id, String status, OffsetDateTime now);

    void updateContractId(Long id, Long contractId, OffsetDateTime now);
}
