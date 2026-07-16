package com.jugu.propertylease.main.metering.api;

import java.time.LocalDate;

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
