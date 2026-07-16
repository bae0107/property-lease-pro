package com.jugu.propertylease.main.metering.api;

import java.math.BigDecimal;

/**
 * 人工录入单表计读数。
 */
public record ManualReadingEntry(
        String meterType,          // WATER | ELECTRICITY | HOT_WATER
        BigDecimal readingValue    // 表盘累计读数
) {}
