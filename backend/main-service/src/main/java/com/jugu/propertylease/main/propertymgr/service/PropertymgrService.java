package com.jugu.propertylease.main.propertymgr.service;

import static com.jugu.propertylease.main.jooq.Tables.BUILDING_INFO;
import static com.jugu.propertylease.main.jooq.Tables.ROOM_INFO;

import com.jugu.propertylease.common.exception.BusinessException;
import org.jooq.DSLContext;
import org.jooq.Condition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * propertymgr 楼栋/房间外部 API 的业务逻辑。
 *
 * <p>沿用 {@code AssetQueryPortImpl} 的模式：直接使用 DSLContext 操作
 * building_info / room_info（jOOQ 表已生成），IsDeleted=1 表示未删除。
 */
@Service
public class PropertymgrService {

    private final DSLContext dsl;

    public PropertymgrService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ══════════════════════ 楼栋 ══════════════════════

    public record BuildingInfo(String buildingId, Long storeId, String buildingName) {}

    public BuildingInfo createBuilding(String buildingId, Long storeId, String buildingName) {
        boolean exists = dsl.fetchExists(BUILDING_INFO,
                BUILDING_INFO.BUILDINGID.eq(buildingId)
                        .and(BUILDING_INFO.ISDELETED.eq(1)));
        if (exists) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "BUILDING_DUPLICATED", "楼栋编号已存在：" + buildingId);
        }
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(BUILDING_INFO)
                .set(BUILDING_INFO.BUILDINGID, buildingId)
                .set(BUILDING_INFO.STOREID, storeId)
                .set(BUILDING_INFO.BUILDINGNAME, buildingName)
                .set(BUILDING_INFO.ISDELETED, 1)
                .set(BUILDING_INFO.CREATETIME, now)
                .set(BUILDING_INFO.UPDATETIME, now)
                .execute();
        return new BuildingInfo(buildingId, storeId, buildingName);
    }

    public record PagedResult<T>(List<T> items, int total) {}

    public PagedResult<BuildingInfo> queryBuildings(String buildingName, int page, int size) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(BUILDING_INFO.ISDELETED.eq(1));
        if (buildingName != null && !buildingName.isBlank()) {
            conditions.add(BUILDING_INFO.BUILDINGNAME.like("%" + buildingName + "%"));
        }

        int total = dsl.selectCount().from(BUILDING_INFO)
                .where(conditions)
                .fetchOneInto(Integer.class);

        List<BuildingInfo> items = dsl
                .select(BUILDING_INFO.BUILDINGID, BUILDING_INFO.STOREID, BUILDING_INFO.BUILDINGNAME)
                .from(BUILDING_INFO)
                .where(conditions)
                .orderBy(BUILDING_INFO.BUILDINGID)
                .limit((page - 1) * size, size)
                .fetch(r -> new BuildingInfo(
                        r.get(BUILDING_INFO.BUILDINGID),
                        r.get(BUILDING_INFO.STOREID),
                        r.get(BUILDING_INFO.BUILDINGNAME)));

        return new PagedResult<>(items, total);
    }

    // ══════════════════════ 房间 ══════════════════════

    public record RoomInfo(Long roomId, String buildingId, String level,
                           String roomNum, Integer livingNum, String roomStatus) {}

    public RoomInfo createRoom(String buildingId, String level, String roomNum, Integer livingNum) {
        boolean buildingExists = dsl.fetchExists(BUILDING_INFO,
                BUILDING_INFO.BUILDINGID.eq(buildingId)
                        .and(BUILDING_INFO.ISDELETED.eq(1)));
        if (!buildingExists) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "BUILDING_NOT_FOUND", "楼栋不存在：" + buildingId);
        }
        if (roomNum != null && !roomNum.isBlank()) {
            boolean roomExists = dsl.fetchExists(ROOM_INFO,
                    ROOM_INFO.BUILDINGID.eq(buildingId)
                            .and(ROOM_INFO.ROOMNUM.eq(roomNum))
                            .and(ROOM_INFO.ISDELETED.eq(1)));
            if (roomExists) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "ROOM_DUPLICATED", "同楼栋下房号已存在：" + roomNum);
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        Long roomId = dsl.insertInto(ROOM_INFO)
                .set(ROOM_INFO.BUILDINGID, buildingId)
                .set(ROOM_INFO.LEVEL, level)
                .set(ROOM_INFO.ROOMNUM, roomNum)
                .set(ROOM_INFO.LIVINGNUM, livingNum)
                .set(ROOM_INFO.ROOMSTATUS, "EMPTY")
                .set(ROOM_INFO.ISDELETED, 1)
                .set(ROOM_INFO.CREATETIME, now)
                .set(ROOM_INFO.UPDATETIME, now)
                .returningResult(ROOM_INFO.ROOMID)
                .fetchOneInto(Long.class);
        return new RoomInfo(roomId, buildingId, level, roomNum, livingNum, "EMPTY");
    }

    public PagedResult<RoomInfo> queryRooms(String buildingId, String roomStatus, int page, int size) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(ROOM_INFO.ISDELETED.eq(1));
        if (buildingId != null && !buildingId.isBlank()) {
            conditions.add(ROOM_INFO.BUILDINGID.eq(buildingId));
        }
        if (roomStatus != null && !roomStatus.isBlank()) {
            conditions.add(ROOM_INFO.ROOMSTATUS.eq(roomStatus));
        }

        int total = dsl.selectCount().from(ROOM_INFO)
                .where(conditions)
                .fetchOneInto(Integer.class);

        List<RoomInfo> items = dsl
                .select(ROOM_INFO.ROOMID, ROOM_INFO.BUILDINGID, ROOM_INFO.LEVEL,
                        ROOM_INFO.ROOMNUM, ROOM_INFO.LIVINGNUM, ROOM_INFO.ROOMSTATUS)
                .from(ROOM_INFO)
                .where(conditions)
                .orderBy(ROOM_INFO.ROOMID)
                .limit((page - 1) * size, size)
                .fetch(r -> new RoomInfo(
                        r.get(ROOM_INFO.ROOMID),
                        r.get(ROOM_INFO.BUILDINGID),
                        r.get(ROOM_INFO.LEVEL),
                        r.get(ROOM_INFO.ROOMNUM),
                        r.get(ROOM_INFO.LIVINGNUM),
                        r.get(ROOM_INFO.ROOMSTATUS)));

        return new PagedResult<>(items, total);
    }
}
