package com.jugu.propertylease.main.accounting.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 日结扣减命令。
 *
 * @param roomId 房间 ID
 * @param settlementDate 日结业务日期
 * @param totalAmount 当日应扣总额
 * @param enterpriseCoverAmount 企业子余额承担部分（metering 预先计算）
 * @param tenantApportionments 各租客分摊明细
 */
public record DailyDeductionCommand(
        Long roomId,
        LocalDate settlementDate,
        BigDecimal totalAmount,
        BigDecimal enterpriseCoverAmount,
        List<TenantDeductItem> tenantApportionments
) {}
