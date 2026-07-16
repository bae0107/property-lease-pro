package com.jugu.propertylease.main.metering.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 提交周期读数命令（PERIODIC，人工或 IoT 推送复用）。
 */
public record RecordReadingCommand(
        Long roomId,
        String meterType,
        BigDecimal readingValue,
        OffsetDateTime readingTime,
        String source,          // IOT | MANUAL
        String deviceIdRaw,     // IoT 幂等键（MANUAL 时为 null）
        Long operatorId         // MANUAL 时填
) {}
