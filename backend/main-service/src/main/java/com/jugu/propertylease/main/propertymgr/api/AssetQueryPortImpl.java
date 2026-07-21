package com.jugu.propertylease.main.propertymgr.api;

import static com.jugu.propertylease.main.jooq.Tables.BUILDING_INFO;
import static com.jugu.propertylease.main.jooq.Tables.ROOM_INFO;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.propertymgr.api.model.AssetRoomInfo;
import com.jugu.propertylease.main.propertymgr.room.entity.RoomStatus;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link AssetQueryPort} 实现（Provisional Adapter）。
 *
 * <p>直接使用 jOOQ DSL 查询现有 propertymgr 表，不引入新 Repository，
 * 保持与 propertymgr 原有代码的松耦合（两套代码共用同一 DSLContext）。
 *
 * <p>⚠️ RoomStatus 映射说明：
 * <ul>
 *   <li>{@code EMPTY} → isRoomAvailableForContract = true（空闲可用）</li>
 *   <li>其余所有状态 → false（不可分配）</li>
 * </ul>
 * 待状态机正式对齐后，修改此类实现即可，接口与调用方代码无需变动。
 */
@Service
public class AssetQueryPortImpl implements AssetQueryPort {

    private final DSLContext dsl;

    public AssetQueryPortImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public AssetRoomInfo getRoomInfo(Long roomId) {
        return dsl.select(
                        ROOM_INFO.ROOMID,
                        ROOM_INFO.LEVEL,
                        ROOM_INFO.ROOMNUM,
                        ROOM_INFO.LIVINGNUM,
                        ROOM_INFO.ROOMSTATUS,
                        BUILDING_INFO.STOREID
                )
                .from(ROOM_INFO)
                .leftJoin(BUILDING_INFO).on(ROOM_INFO.BUILDINGID.eq(BUILDING_INFO.BUILDINGID))
                .where(ROOM_INFO.ROOMID.eq(roomId))
                .and(ROOM_INFO.ISDELETED.eq(1))    // 1 = 未删除（propertymgr 约定）
                .fetchOptional(r -> new AssetRoomInfo(
                        r.get(ROOM_INFO.ROOMID),
                        r.get(BUILDING_INFO.STOREID),
                        buildRoomNo(r.get(ROOM_INFO.LEVEL), r.get(ROOM_INFO.ROOMNUM)),
                        r.get(ROOM_INFO.LIVINGNUM) != null ? r.get(ROOM_INFO.LIVINGNUM) : 1,
                        r.get(ROOM_INFO.ROOMSTATUS)
                ))
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "房间不存在：" + roomId));
    }

    @Override
    public List<AssetRoomInfo> getRoomsByIds(List<Long> roomIds) {
        return dsl.select(
                        ROOM_INFO.ROOMID,
                        ROOM_INFO.LEVEL,
                        ROOM_INFO.ROOMNUM,
                        ROOM_INFO.LIVINGNUM,
                        ROOM_INFO.ROOMSTATUS,
                        BUILDING_INFO.STOREID
                )
                .from(ROOM_INFO)
                .leftJoin(BUILDING_INFO).on(ROOM_INFO.BUILDINGID.eq(BUILDING_INFO.BUILDINGID))
                .where(ROOM_INFO.ROOMID.in(roomIds))
                .and(ROOM_INFO.ISDELETED.eq(1))
                .fetch(r -> new AssetRoomInfo(
                        r.get(ROOM_INFO.ROOMID),
                        r.get(BUILDING_INFO.STOREID),
                        buildRoomNo(r.get(ROOM_INFO.LEVEL), r.get(ROOM_INFO.ROOMNUM)),
                        r.get(ROOM_INFO.LIVINGNUM) != null ? r.get(ROOM_INFO.LIVINGNUM) : 1,
                        r.get(ROOM_INFO.ROOMSTATUS)
                ));
    }

    @Override
    public int getMaxOccupancy(Long roomId) {
        Integer living = dsl.select(ROOM_INFO.LIVINGNUM)
                .from(ROOM_INFO)
                .where(ROOM_INFO.ROOMID.eq(roomId))
                .and(ROOM_INFO.ISDELETED.eq(1))
                .fetchOneInto(Integer.class);
        return (living != null && living > 0) ? living : 1;
    }

    @Override
    public Long getStoreIdByRoomId(Long roomId) {
        return dsl.select(BUILDING_INFO.STOREID)
                .from(ROOM_INFO)
                .join(BUILDING_INFO).on(ROOM_INFO.BUILDINGID.eq(BUILDING_INFO.BUILDINGID))
                .where(ROOM_INFO.ROOMID.eq(roomId))
                .and(ROOM_INFO.ISDELETED.eq(1))
                .fetchOne(BUILDING_INFO.STOREID);
    }

    @Override
    public boolean isRoomAvailableForContract(Long roomId) {
        String status = dsl.select(ROOM_INFO.ROOMSTATUS)
                .from(ROOM_INFO)
                .where(ROOM_INFO.ROOMID.eq(roomId))
                .and(ROOM_INFO.ISDELETED.eq(1))
                .fetchOneInto(String.class);
        // ⚠️ Provisional mapping: 仅 EMPTY 视为可用
        return RoomStatus.EMPTY.name().equals(status);
    }

    private String buildRoomNo(String level, String roomNum) {
        if (level == null && roomNum == null) return "未知";
        return (level != null ? level + "层" : "") + (roomNum != null ? roomNum + "室" : "");
    }
}
