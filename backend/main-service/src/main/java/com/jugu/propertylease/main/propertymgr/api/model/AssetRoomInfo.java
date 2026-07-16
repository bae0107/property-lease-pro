package com.jugu.propertylease.main.propertymgr.api.model;

/**
 * 房间基础信息（Port 层传输对象，与 jOOQ POJO 解耦）。
 *
 * @param id            room_info.RoomId
 * @param storeId       所属门店 ID
 * @param roomNo        房间编号（Level + RoomNum）
 * @param maxOccupancy  最大入住人数（room_info.LivingNum）
 * @param currentStatus 当前房间状态（propertymgr 枚举值字符串，如 "EMPTY"）
 */
public record AssetRoomInfo(
        Long id,
        Long storeId,
        String roomNo,
        int maxOccupancy,
        String currentStatus
) {}
