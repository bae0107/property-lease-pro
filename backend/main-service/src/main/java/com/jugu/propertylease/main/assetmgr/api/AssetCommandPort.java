package com.jugu.propertylease.main.assetmgr.api;

/**
 * 房源命令 Port（进程内，供 contract 调用）。
 *
 * <p>⚠️ Provisional Adapter：当前实现直接更新 RoomStatus，
 * 待 RoomStatus 状态机与新设计正式对齐后修订，接口本身不变。
 */
public interface AssetCommandPort {

    /**
     * 合同确认时锁定房间（confirmContract 时调用）。
     *
     * <p>⚠️ Provisional：当前实现将 RoomStatus 更新为 WAIT_CHECK_IN，
     * 语义上表示"已被预订，不再可分配给其他合同"。
     *
     * @param roomId     room_info.RoomId
     * @param contractId 占用此房间的合同 ID（记录用，当前实现可能只更新状态）
     */
    void lockRoom(Long roomId, Long contractId);

    /**
     * 退房后释放房间（applyPartialReturn / applyFullReturn 完成后调用）。
     *
     * <p>⚠️ Provisional：当前实现将 RoomStatus 更新为 EMPTY。
     *
     * @param roomId     room_info.RoomId
     * @param contractId 原占用合同 ID
     */
    void releaseRoom(Long roomId, Long contractId);
}
