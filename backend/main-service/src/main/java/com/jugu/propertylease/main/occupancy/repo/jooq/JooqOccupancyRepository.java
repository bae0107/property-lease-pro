package com.jugu.propertylease.main.occupancy.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.ROOM_ASSIGNMENT;
import static com.jugu.propertylease.main.jooq.Tables.STAY;
import static com.jugu.propertylease.main.jooq.Tables.TRANSFER_RECORD;
import static org.jooq.impl.DSL.trueCondition;

import com.jugu.propertylease.main.jooq.tables.pojos.RoomAssignment;
import com.jugu.propertylease.main.jooq.tables.pojos.Stay;
import com.jugu.propertylease.main.occupancy.repo.OccupancyRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * {@link OccupancyRepository} 的 jOOQ 实现。
 */
@Repository
public class JooqOccupancyRepository implements OccupancyRepository {

    private final DSLContext dsl;

    public JooqOccupancyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ── RoomAssignment ──────────────────────────────────────────────────────

    @Override
    public Long insertAssignment(Long contractId, Long contractRoomId, Long roomId,
                                 Long tenantId, Long assignedBy, OffsetDateTime now) {
        return dsl.insertInto(ROOM_ASSIGNMENT)
                .set(ROOM_ASSIGNMENT.CONTRACT_ID, contractId)
                .set(ROOM_ASSIGNMENT.CONTRACT_ROOM_ID, contractRoomId)
                .set(ROOM_ASSIGNMENT.ROOM_ID, roomId)
                .set(ROOM_ASSIGNMENT.TENANT_ID, tenantId)
                .set(ROOM_ASSIGNMENT.STATUS, "ASSIGNED")
                .set(ROOM_ASSIGNMENT.ASSIGNED_AT, now)
                .set(ROOM_ASSIGNMENT.ASSIGNED_BY, assignedBy)
                .returning(ROOM_ASSIGNMENT.ID)
                .fetchOne(ROOM_ASSIGNMENT.ID);
    }

    @Override
    public Optional<RoomAssignment> findAssignmentById(Long id) {
        return Optional.ofNullable(dsl.selectFrom(ROOM_ASSIGNMENT)
                .where(ROOM_ASSIGNMENT.ID.eq(id))
                .fetchOneInto(RoomAssignment.class));
    }

    @Override
    public Optional<RoomAssignment> findActiveAssignment(Long contractRoomId, Long tenantId) {
        return Optional.ofNullable(dsl.selectFrom(ROOM_ASSIGNMENT)
                .where(ROOM_ASSIGNMENT.CONTRACT_ROOM_ID.eq(contractRoomId))
                .and(ROOM_ASSIGNMENT.TENANT_ID.eq(tenantId))
                .and(ROOM_ASSIGNMENT.STATUS.eq("ASSIGNED"))
                .fetchOneInto(RoomAssignment.class));
    }

    @Override
    public void updateAssignmentStatus(Long id, String status,
                                       OffsetDateTime cancelledAt, Long cancelledBy) {
        dsl.update(ROOM_ASSIGNMENT)
                .set(ROOM_ASSIGNMENT.STATUS, status)
                .set(ROOM_ASSIGNMENT.CANCELLED_AT, cancelledAt)
                .set(ROOM_ASSIGNMENT.CANCELLED_BY, cancelledBy)
                .where(ROOM_ASSIGNMENT.ID.eq(id))
                .execute();
    }

    @Override
    public List<RoomAssignment> findAssignments(Long contractId, Long roomId, Long tenantId,
                                                String status, int offset, int limit) {
        return dsl.selectFrom(ROOM_ASSIGNMENT)
                .where(assignmentCondition(contractId, roomId, tenantId, status))
                .orderBy(ROOM_ASSIGNMENT.ID.desc())
                .limit(offset, limit)
                .fetchInto(RoomAssignment.class);
    }

    @Override
    public int countAssignments(Long contractId, Long roomId, Long tenantId, String status) {
        return dsl.selectCount()
                .from(ROOM_ASSIGNMENT)
                .where(assignmentCondition(contractId, roomId, tenantId, status))
                .fetchOne(0, int.class);
    }

    @Override
    public int countAssignedByRoom(Long roomId) {
        return dsl.selectCount()
                .from(ROOM_ASSIGNMENT)
                .where(ROOM_ASSIGNMENT.ROOM_ID.eq(roomId))
                .and(ROOM_ASSIGNMENT.STATUS.eq("ASSIGNED"))
                .fetchOne(0, int.class);
    }

    private Condition assignmentCondition(Long contractId, Long roomId,
                                          Long tenantId, String status) {
        Condition c = trueCondition();
        if (contractId != null) c = c.and(ROOM_ASSIGNMENT.CONTRACT_ID.eq(contractId));
        if (roomId != null)     c = c.and(ROOM_ASSIGNMENT.ROOM_ID.eq(roomId));
        if (tenantId != null)   c = c.and(ROOM_ASSIGNMENT.TENANT_ID.eq(tenantId));
        if (status != null)     c = c.and(ROOM_ASSIGNMENT.STATUS.eq(status));
        return c;
    }

    // ── Stay ────────────────────────────────────────────────────────────────

    @Override
    public Long insertStay(Long assignmentId, Long contractId, Long contractRoomId, Long roomId,
                           Long tenantId, Long checkedInBy, OffsetDateTime checkInAt,
                           OffsetDateTime now) {
        return dsl.insertInto(STAY)
                .set(STAY.SOURCE_ASSIGNMENT_ID, assignmentId)
                .set(STAY.CONTRACT_ID, contractId)
                .set(STAY.CONTRACT_ROOM_ID, contractRoomId)
                .set(STAY.ROOM_ID, roomId)
                .set(STAY.TENANT_ID, tenantId)
                .set(STAY.STAY_STATUS, "CHECKED_IN")
                .set(STAY.CHECK_IN_AT, checkInAt)
                .set(STAY.CHECKED_IN_BY, checkedInBy)
                .set(STAY.CREATED_AT, now)
                .set(STAY.UPDATED_AT, now)
                .returning(STAY.ID)
                .fetchOne(STAY.ID);
    }

    @Override
    public Optional<Stay> findStayById(Long id) {
        return Optional.ofNullable(dsl.selectFrom(STAY)
                .where(STAY.ID.eq(id))
                .fetchOneInto(Stay.class));
    }

    @Override
    public void updateStayStatus(Long id, String status,
                                 OffsetDateTime checkOutAt, OffsetDateTime now) {
        dsl.update(STAY)
                .set(STAY.STAY_STATUS, status)
                .set(STAY.CHECK_OUT_AT, checkOutAt)
                .set(STAY.UPDATED_AT, now)
                .where(STAY.ID.eq(id))
                .execute();
    }

    @Override
    public void updateIamUserId(Long stayId, Long iamUserId) {
        dsl.update(STAY)
                .set(STAY.IAM_USER_ID, iamUserId)
                .set(STAY.UPDATED_AT, OffsetDateTime.now())
                .where(STAY.ID.eq(stayId))
                .execute();
    }

    @Override
    public List<Stay> findCheckedInStaysByRoom(Long roomId) {
        return dsl.selectFrom(STAY)
                .where(STAY.ROOM_ID.eq(roomId))
                .and(STAY.STAY_STATUS.eq("CHECKED_IN"))
                .fetchInto(Stay.class);
    }

    @Override
    public List<Long> findRoomIdsWithCheckedInStays(LocalDate date) {
        // 与 ShanghaiOffsetDateTimeConverter 一致，按 +08:00 计算当日 24:00 边界
        OffsetDateTime endOfDay = date.plusDays(1).atStartOfDay()
                .atOffset(ZoneOffset.ofHours(8));
        return dsl.selectDistinct(STAY.ROOM_ID)
                .from(STAY)
                .where(STAY.STAY_STATUS.eq("CHECKED_IN"))
                .and(STAY.CHECK_IN_AT.lt(endOfDay))
                .fetch(STAY.ROOM_ID);
    }

    @Override
    public boolean hasCheckedInStays(Long roomId) {
        return dsl.fetchExists(STAY,
                STAY.ROOM_ID.eq(roomId),
                STAY.STAY_STATUS.eq("CHECKED_IN"));
    }

    @Override
    public int countCheckedInByRoom(Long roomId) {
        return dsl.selectCount()
                .from(STAY)
                .where(STAY.ROOM_ID.eq(roomId))
                .and(STAY.STAY_STATUS.eq("CHECKED_IN"))
                .fetchOne(0, int.class);
    }

    @Override
    public List<Stay> findStays(Long contractId, Long roomId, Long tenantId,
                                String stayStatus, int offset, int limit) {
        return dsl.selectFrom(STAY)
                .where(stayCondition(contractId, roomId, tenantId, stayStatus))
                .orderBy(STAY.ID.desc())
                .limit(offset, limit)
                .fetchInto(Stay.class);
    }

    @Override
    public int countStays(Long contractId, Long roomId, Long tenantId, String stayStatus) {
        return dsl.selectCount()
                .from(STAY)
                .where(stayCondition(contractId, roomId, tenantId, stayStatus))
                .fetchOne(0, int.class);
    }

    private Condition stayCondition(Long contractId, Long roomId,
                                    Long tenantId, String stayStatus) {
        Condition c = trueCondition();
        if (contractId != null) c = c.and(STAY.CONTRACT_ID.eq(contractId));
        if (roomId != null)     c = c.and(STAY.ROOM_ID.eq(roomId));
        if (tenantId != null)   c = c.and(STAY.TENANT_ID.eq(tenantId));
        if (stayStatus != null) c = c.and(STAY.STAY_STATUS.eq(stayStatus));
        return c;
    }

    // ── TransferRecord ──────────────────────────────────────────────────────

    @Override
    public Long insertTransferRecord(Long tenantId, Long fromStayId, Long fromContractId,
                                     Long fromRoomId, Long toStayId, Long toContractId,
                                     Long toContractRoomId, Long toRoomId,
                                     Long transferredBy, OffsetDateTime transferAt) {
        return dsl.insertInto(TRANSFER_RECORD)
                .set(TRANSFER_RECORD.TENANT_ID, tenantId)
                .set(TRANSFER_RECORD.FROM_STAY_ID, fromStayId)
                .set(TRANSFER_RECORD.FROM_CONTRACT_ID, fromContractId)
                .set(TRANSFER_RECORD.FROM_ROOM_ID, fromRoomId)
                .set(TRANSFER_RECORD.TO_STAY_ID, toStayId)
                .set(TRANSFER_RECORD.TO_CONTRACT_ID, toContractId)
                .set(TRANSFER_RECORD.TO_CONTRACT_ROOM_ID, toContractRoomId)
                .set(TRANSFER_RECORD.TO_ROOM_ID, toRoomId)
                .set(TRANSFER_RECORD.TRANSFERRED_BY, transferredBy)
                .set(TRANSFER_RECORD.TRANSFER_AT, transferAt)
                .returning(TRANSFER_RECORD.ID)
                .fetchOne(TRANSFER_RECORD.ID);
    }
}
