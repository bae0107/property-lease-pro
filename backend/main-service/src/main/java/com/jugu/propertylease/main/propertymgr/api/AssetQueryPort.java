package com.jugu.propertylease.main.propertymgr.api;

import com.jugu.propertylease.main.propertymgr.api.model.AssetRoomInfo;

import java.util.List;

/**
 * 房源查询 Port（进程内，供 contract / occupancy / metering 调用）。
 *
 * <p>⚠️ Provisional Adapter：当前 RoomStatus 状态机与新设计不完全对齐，
 * 本接口实现使用保守映射（EMPTY → 可用），待状态机正式对齐后修订实现，接口本身不变。
 */
public interface AssetQueryPort {

    /**
     * 获取单个房间基础信息。
     *
     * @throws com.jugu.propertylease.common.exception.BusinessException 404 若房间不存在
     */
    AssetRoomInfo getRoomInfo(Long roomId);

    /**
     * 批量获取房间信息（用于合同确认前校验）。
     */
    List<AssetRoomInfo> getRoomsByIds(List<Long> roomIds);

    /**
     * 获取房间最大入住人数（读取 room_info.LivingNum）。
     */
    int getMaxOccupancy(Long roomId);

    /**
     * 获取房间所属门店 ID（metering 日结时查找计费单价用）。
     * 路径：room_info.BuildingId → building_info.StoreId。
     */
    Long getStoreIdByRoomId(Long roomId);

    /**
     * 检查房间是否可被锁定到新合同（未被其他合同占用）。
     *
     * <p>⚠️ Provisional：当前用 RoomStatus.EMPTY 判断，待状态机对齐后修订。
     */
    boolean isRoomAvailableForContract(Long roomId);
}
