package com.jugu.propertylease.main.metering.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日结执行结果（供 schedule 记录日志用）。
 */
public record DailySettlementResult(
        LocalDate date,
        int totalRooms,
        int settledRooms,
        int partialRooms,    // 有欠费的房间数（PARTIAL）
        int failedRooms,
        BigDecimal totalAmount,
        BigDecimal totalShortfall
) {}
