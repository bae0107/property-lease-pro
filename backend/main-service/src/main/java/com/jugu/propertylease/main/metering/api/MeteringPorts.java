package com.jugu.propertylease.main.metering.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

// ══════════════════════════════════════════════════════════════════════════════
// MeteringCommandPort — 供 occupancy 调用（入住/退宿锚点读数采集）
// ══════════════════════════════════════════════════════════════════════════════

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

// ══════════════════════════════════════════════════════════════════════════════
// MeteringScheduleTrigger — 供 schedule 调用（日结触发）
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 日结触发 Port（进程内，由 schedule 在 02:00 Cron 中注入调用）。
 */
public interface MeteringScheduleTrigger {
    /**
     * 执行全量房间水电日结。
     * 幂等：room_daily_charge(room_id, settlement_date) 唯一约束保证重跑安全。
     *
     * @param date 业务日期（通常为昨日）
     */
    DailySettlementResult runDailySettlement(LocalDate date);
}

// ══════════════════════════════════════════════════════════════════════════════
// Command / Result Records
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 采集锚点读数命令（CHECK_IN / CHECK_OUT 通用）。
 */
record CollectReadingsCommand(
        Long stayId,
        Long roomId,
        String anchorType,                    // CHECK_IN | CHECK_OUT
        List<ManualReadingEntry> manualReadings,  // 设备离线时的人工录入
        Long operatorId
) {}

/**
 * 人工录入单表计读数。
 */
record ManualReadingEntry(
        String meterType,          // WATER | ELECTRICITY | HOT_WATER
        BigDecimal readingValue    // 表盘累计读数
) {}

/**
 * 退宿时返回的用量摘要（入住期间累计）。
 */
record UsageSummary(
        Long stayId,
        BigDecimal waterUsage,
        BigDecimal electricityUsage,
        BigDecimal hotWaterUsage,
        BigDecimal totalAmount,       // 按当期单价计算的应付总额
        BigDecimal alreadyDeducted,   // 日结期间已成功扣款总额
        BigDecimal outstanding        // 欠费（尚未扣款部分）
) {}

/**
 * 提交周期读数命令（PERIODIC，人工或 IoT 推送复用）。
 */
record RecordReadingCommand(
        Long roomId,
        String meterType,
        BigDecimal readingValue,
        OffsetDateTime readingTime,
        String source,          // IOT | MANUAL
        String deviceIdRaw,     // IoT 幂等键（MANUAL 时为 null）
        Long operatorId         // MANUAL 时填
) {}

/**
 * 日结执行结果（供 schedule 记录日志用）。
 */
record DailySettlementResult(
        LocalDate date,
        int totalRooms,
        int settledRooms,
        int partialRooms,    // 有欠费的房间数（PARTIAL）
        int failedRooms,
        BigDecimal totalAmount,
        BigDecimal totalShortfall
) {}
