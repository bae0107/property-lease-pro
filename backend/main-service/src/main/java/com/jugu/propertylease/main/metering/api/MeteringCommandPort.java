package com.jugu.propertylease.main.metering.api;

/**
 * Metering 命令 Port（进程内，由 occupancy 在 checkIn / checkOut / transfer 时注入调用）。
 */
public interface MeteringCommandPort {

    /**
     * 采集入住锚点读数（CHECK_IN）。
     * 为房间所有已激活绑定设备（is_active=1）采集当前读数，建立 SettlementAnchor。
     * 若 IoT 设备离线，可通过 manualReadings 传入人工底数。
     *
     * @return readingGroupId（同一入住动作的所有表计读数共享此 ID）
     */
    String collectCheckInReadings(CollectReadingsCommand cmd);

    /**
     * 采集退宿锚点读数（CHECK_OUT）。
     * 建立 CHECK_OUT SettlementAnchor，并返回入住期间的用量摘要（供 accounting 参考）。
     */
    UsageSummary collectCheckOutReadings(CollectReadingsCommand cmd);

    /**
     * 提交 PERIODIC 周期读数（人工录入，由外部 API 触发）。
     */
    void submitPeriodicReading(RecordReadingCommand cmd);
}
