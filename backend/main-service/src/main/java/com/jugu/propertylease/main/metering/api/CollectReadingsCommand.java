package com.jugu.propertylease.main.metering.api;

import java.util.List;

/**
 * 采集锚点读数命令（CHECK_IN / CHECK_OUT 通用）。
 */
public record CollectReadingsCommand(
        Long stayId,
        Long roomId,
        String anchorType,                        // CHECK_IN | CHECK_OUT
        List<ManualReadingEntry> manualReadings,   // 设备离线时的人工录入
        Long operatorId
) {}
