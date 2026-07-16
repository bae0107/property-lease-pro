package com.jugu.propertylease.main.propertymgr.api;

import static com.jugu.propertylease.main.jooq.Tables.ROOM_INFO;

import com.jugu.propertylease.main.propertymgr.room.entity.RoomStatus;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * {@link AssetCommandPort} 实现（Provisional Adapter）。
 *
 * <p>⚠️ 当前状态映射：
 * <ul>
 *   <li>lockRoom → RoomStatus.WAIT_CHECK_IN</li>
 *   <li>releaseRoom → RoomStatus.EMPTY</li>
 * </ul>
 * 待 RoomStatus 状态机对齐后修订此实现，接口与调用方代码无需变动。
 */
@Service
public class AssetCommandPortImpl implements AssetCommandPort {

    private final DSLContext dsl;

    public AssetCommandPortImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void lockRoom(Long roomId, Long contractId) {
        // ⚠️ Provisional: EMPTY → WAIT_CHECK_IN
        dsl.update(ROOM_INFO)
                .set(ROOM_INFO.ROOMSTATUS, RoomStatus.WAIT_CHECK_IN.name())
                .set(ROOM_INFO.UPDATETIME, OffsetDateTime.now())
                .where(ROOM_INFO.ROOMID.eq(roomId))
                .and(ROOM_INFO.ISDELETED.eq(1))
                .execute();
    }

    @Override
    @Transactional
    public void releaseRoom(Long roomId, Long contractId) {
        // ⚠️ Provisional: → EMPTY
        dsl.update(ROOM_INFO)
                .set(ROOM_INFO.ROOMSTATUS, RoomStatus.EMPTY.name())
                .set(ROOM_INFO.UPDATETIME, OffsetDateTime.now())
                .where(ROOM_INFO.ROOMID.eq(roomId))
                .and(ROOM_INFO.ISDELETED.eq(1))
                .execute();
    }
}
