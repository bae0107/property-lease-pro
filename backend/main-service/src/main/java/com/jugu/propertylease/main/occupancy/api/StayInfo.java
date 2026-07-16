package com.jugu.propertylease.main.occupancy.api;

import java.time.OffsetDateTime;

/**
 * 入住记录摘要（Port 层传输对象，供 metering / accounting 使用）。
 *
 * @param id              stay.id
 * @param contractId      所属合同 ID
 * @param contractRoomId  合同房间关联 ID
 * @param roomId          房间 ID（冗余，加速查询）
 * @param tenantId        员工 ID（即 customer.employee.id）
 * @param stayStatus      入住状态（CHECKED_IN / TRANSFERRED / CHECKED_OUT）
 * @param checkInAt       入住时间
 */
public record StayInfo(
        Long id,
        Long contractId,
        Long contractRoomId,
        Long roomId,
        Long tenantId,
        String stayStatus,
        OffsetDateTime checkInAt
) {}
