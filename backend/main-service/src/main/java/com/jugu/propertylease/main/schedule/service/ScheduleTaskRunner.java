package com.jugu.propertylease.main.schedule.service;

import com.jugu.propertylease.main.contract.api.ContractScheduleTrigger;
import com.jugu.propertylease.main.metering.api.MeteringScheduleTrigger;
import com.jugu.propertylease.main.metering.api.DailySettlementResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 定时任务唯一调度入口。
 *
 * <p><b>原则：本类只负责触发，不含任何业务逻辑。</b>
 * 实际工作委托给各模块的 ScheduleTrigger Port Interface 实现。
 *
 * <p>需要在 {@code MainServiceApplication} 上添加 {@code @EnableScheduling}。
 *
 * <p>防重机制：每次执行前调用 {@link ScheduleTaskLogService#tryStart}，
 * 利用 DB 唯一约束保证同一业务日期同一任务类型只执行一次。
 */
@Service
public class ScheduleTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTaskRunner.class);

    private final ScheduleTaskLogService logService;
    private final MeteringScheduleTrigger meteringTrigger;
    private final ContractScheduleTrigger contractTrigger;

    public ScheduleTaskRunner(ScheduleTaskLogService logService,
                              MeteringScheduleTrigger meteringTrigger,
                              ContractScheduleTrigger contractTrigger) {
        this.logService      = logService;
        this.meteringTrigger = meteringTrigger;
        this.contractTrigger = contractTrigger;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Cron 定时任务
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 每日 02:00 — 水电日结（处理昨日数据）。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMeterSettlement() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        run("DAILY_METER_SETTLEMENT", targetDate, false, "CRON", () -> {
            DailySettlementResult r = meteringTrigger.runDailySettlement(targetDate);
            String errorSummary = r.failedRooms() > 0
                    ? "failedRooms=" + r.failedRooms() + " shortfall=" + r.totalShortfall()
                    : null;
            return new TaskRunResult(r.totalRooms(), r.settledRooms(),
                    r.failedRooms(), errorSummary);
        });
    }

    /**
     * 每日 01:00 — 合同到期检查。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void contractExpiryCheck() {
        LocalDate today = LocalDate.now();
        run("CONTRACT_EXPIRY_CHECK", today, false, "CRON", () -> {
            contractTrigger.checkContractExpiry(today);
            return new TaskRunResult(0, 0, 0, null);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 通用任务执行包装（防重 + 日志）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 统一任务执行入口（Cron 和手动触发共用）。
     *
     * @param taskType    任务类型
     * @param date        业务日期（幂等键之一）
     * @param force       是否强制重跑（已 SUCCESS 的记录）
     * @param triggeredBy "CRON" | "MANUAL"
     * @param body        实际业务逻辑
     */
    public void run(String taskType, LocalDate date, boolean force,
                    String triggeredBy, TaskBody body) {
        Long logId = logService.tryStart(taskType, date, force, triggeredBy);
        if (logId == null) {
            log.info("[schedule] 跳过任务 taskType={} date={} （已执行或正在执行）",
                    taskType, date);
            return;
        }

        log.info("[schedule] 开始任务 taskType={} date={} triggeredBy={}", taskType, date, triggeredBy);
        try {
            TaskRunResult result = body.run();
            String status = result.failureCount() > 0 ? "PARTIAL_FAILURE" : "SUCCESS";
            logService.finish(logId, status, result.totalCount(),
                    result.successCount(), result.failureCount(), result.errorSummary());
            log.info("[schedule] 完成任务 taskType={} date={} status={} total={} failed={}",
                    taskType, date, status, result.totalCount(), result.failureCount());
        } catch (Exception e) {
            log.error("[schedule] 任务失败 taskType={} date={}", taskType, date, e);
            logService.finish(logId, "FAILED", 0, 0, 0,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 内部类型定义
    // ══════════════════════════════════════════════════════════════════════

    @FunctionalInterface
    public interface TaskBody {
        TaskRunResult run() throws Exception;
    }

    public record TaskRunResult(
            int totalCount,
            int successCount,
            int failureCount,
            String errorSummary
    ) {}
}
