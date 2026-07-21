package com.jugu.propertylease.main.occupancy.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.RoomAssignment;
import com.jugu.propertylease.main.jooq.tables.pojos.Stay;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * occupancy 模块仓储接口。
 *
 * <p>实现：{@code occupancy/repo/jooq/JooqOccupancyRepository}。
 */
public interface OccupancyRepository {

    // ── RoomAssignment ──────────────────────────────────────────────────────

    Long insertAssignment(Long contractId, Long contractRoomId, Long roomId,
                          Long tenantId, Long assignedBy, OffsetDateTime now);

    Optional<RoomAssignment> findAssignmentById(Long id);

    /** 查找未消费的分配（status = ASSIGNED），用于防重复分配。*/
    Optional<RoomAssignment> findActiveAssignment(Long contractRoomId, Long tenantId);

    void updateAssignmentStatus(Long id, String status,
                                OffsetDateTime cancelledAt, Long cancelledBy);

    List<RoomAssignment> findAssignments(Long contractId, Long roomId, Long tenantId,
                                         String status, int offset, int limit);

    int countAssignments(Long contractId, Long roomId, Long tenantId, String status);

    int countAssignedByRoom(Long roomId);

    // ── Stay ────────────────────────────────────────────────────────────────

    Long insertStay(Long assignmentId, Long contractId, Long contractRoomId, Long roomId,
                    Long tenantId, Long checkedInBy, OffsetDateTime checkInAt, OffsetDateTime now);

    Optional<Stay> findStayById(Long id);

    void updateStayStatus(Long id, String status, OffsetDateTime checkOutAt, OffsetDateTime now);

    void updateIamUserId(Long stayId, Long iamUserId);

    List<Stay> findCheckedInStaysByRoom(Long roomId);

    /** 日结时仍在住（CHECKED_IN 且当日 24:00 前已入住）的房间 ID 列表。*/
    List<Long> findRoomIdsWithCheckedInStays(LocalDate date);

    boolean hasCheckedInStays(Long roomId);

    int countCheckedInByRoom(Long roomId);

    List<Stay> findStays(Long contractId, Long roomId, Long tenantId,
                         String stayStatus, int offset, int limit);

    int countStays(Long contractId, Long roomId, Long tenantId, String stayStatus);

    // ── TransferRecord ──────────────────────────────────────────────────────

    Long insertTransferRecord(Long tenantId, Long fromStayId, Long fromContractId,
                              Long fromRoomId, Long toStayId, Long toContractId,
                              Long toContractRoomId, Long toRoomId,
                              Long transferredBy, OffsetDateTime transferAt);
}
