package com.jugu.propertylease.main.contract.api;

import java.time.LocalDate;

/**
 * 合同调度触发 Port（进程内，由 schedule 在 01:00 Cron 中注入调用）。
 */
public interface ContractScheduleTrigger {

    /**
     * 检查合同到期情况：
     * <ul>
     *   <li>endDate < today：记录警告日志（不自动终止，需 STAFF 手动发起 terminate）</li>
     *   <li>endDate - today <= 7天：发送到期提醒通知</li>
     * </ul>
     */
    void checkContractExpiry(LocalDate date);

    /**
     * 月租账单生成：仅每月 1 号执行（其他日期直接返回）。
     * 对 READY_FOR_CHECK_IN / PARTIALLY_RETURNED 合同，按 ACTIVE 房间 signed_rent 求和，
     * 调用 AccountingCommandPort.createRentBill（同合同当月幂等跳过）。
     * payment_mode=QUARTERLY 时仅在与 start_date 相隔 3 的倍数的月份出账，金额为 3 个月租金。
     */
    void checkAndGenerateRentBills(LocalDate date);
}
