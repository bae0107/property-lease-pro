package com.jugu.propertylease.main.occupancy.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Occupancy 查询 Port（进程内，供 metering / contract / accounting 注入调用）。
 */
public interface OccupancyQueryPort {

    /** metering 日结：查询指定日期有 CHECKED_IN stay 的所有 roomId。*/
    List<Long> findRoomIdsWithCheckedInStays(LocalDate date);

    /** metering 日结：查询房间当前所有 CHECKED_IN stay（用于均摊计算）。*/
    List<StayInfo> getCheckedInStaysByRoom(Long roomId);

    /** contract 退房前置校验：该房间是否有活跃 stay。*/
    boolean hasCheckedInStays(Long roomId);

    StayInfo getStay(Long stayId);

    int countAssignedByRoom(Long roomId);

    int countCheckedInByRoom(Long roomId);
}
