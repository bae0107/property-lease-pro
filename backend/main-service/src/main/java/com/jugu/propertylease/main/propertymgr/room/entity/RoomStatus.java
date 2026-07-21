package com.jugu.propertylease.main.propertymgr.room.entity;

/**
 * 房间状态。
 *
 * <p>⚠️ Provisional：与旧 propertymgr 状态机正式对齐前，仅保留
 * Asset 适配器（{@code AssetQueryPortImpl} / {@code AssetCommandPortImpl}）
 * 实际用到的取值。对齐后在此扩展完整状态机，适配器实现随之修订。
 */
public enum RoomStatus {
    /** 空闲（可分配签约） */
    EMPTY,
    /** 待入住（签约后锁定，等待 checkIn） */
    WAIT_CHECK_IN
}
