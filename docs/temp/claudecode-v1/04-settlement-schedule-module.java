// ─────────────────────────────────────────────────────────────────────────────
// SettlementCommandPort.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.settlement.api;

public interface SettlementCommandPort {

    /**
     * 发起个人退宿结算（由 contract.CheckoutService 调用）。
     * 结算为异步流程，完成后回调 /internal/v1/contract/tenancy-settlement-complete。
     */
    void initiateCheckout(InitiateCheckoutCommand cmd);

    /**
     * 发起合同退房结算（所有 tenancy 均 CHECKED_OUT 后由 contract.CheckoutService 调用）。
     * 负责押金结算、ROOM_SHARED 账户余额处理。
     */
    void initiateContractTermination(Long contractId);
}

// ─────────────────────────────────────────────────────────────────────────────
// InitiateCheckoutCommand.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.settlement.api;

public record InitiateCheckoutCommand(
    Long tenancyId,
    Long contractId,
    Long contractRoomId,
    Long roomId,
    Long employeeId,
    Long tenantAccountId
) {}

// ─────────────────────────────────────────────────────────────────────────────
// TenantCheckoutSettlementService.java  ← 个人退宿结算核心
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.settlement.service;

import com.jugu.propertylease.main.account.api.*;
import com.jugu.propertylease.main.meter.api.MeterQueryPort;
import com.jugu.propertylease.main.meter.api.TenancyUsageSummary;
import com.jugu.propertylease.main.settlement.api.*;
import com.jugu.propertylease.main.settlement.repo.SettlementItemRepository;
import com.jugu.propertylease.main.settlement.repo.SettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;  // 或封装的 HTTP Client

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TenantCheckoutSettlementService {

    private static final Logger log = LoggerFactory.getLogger(TenantCheckoutSettlementService.class);

    private final SettlementRepository settlementRepo;
    private final SettlementItemRepository itemRepo;
    private final MeterQueryPort meterQueryPort;
    private final AccountQueryPort accountQueryPort;
    private final AccountCommandPort accountCommandPort;
    // 注入 billingServiceClient（生成追缴账单）
    // 注入 contractCallbackClient（回调 contract /internal/v1/contract/tenancy-settlement-complete）

    // 构造器注入（省略）

    /**
     * 发起个人退宿结算。
     * 当前实现为同步执行，未来可改为异步（MQ）。
     * 完成后通过内部接口回调 contract 模块。
     */
    @Transactional
    public void initiateCheckout(InitiateCheckoutCommand cmd) {
        // 1. 创建结算单（CALCULATING）
        String settlementNo = generateSettlementNo();
        Long settlementId = settlementRepo.insert(
            settlementNo, "TENANT_CHECKOUT", cmd.tenancyId(), "CALCULATING"
        );

        try {
            executeCheckoutSettlement(settlementId, cmd);
        } catch (Exception e) {
            log.error("退宿结算失败 settlementId={} tenancyId={}", settlementId, cmd.tenancyId(), e);
            settlementRepo.updateStatus(settlementId, "FAILED");
            // 失败不回调 contract，等待人工确认或重试
        }
    }

    @Transactional
    protected void executeCheckoutSettlement(Long settlementId, InitiateCheckoutCommand cmd) {
        List<SettlementItemRecord> items = new ArrayList<>();

        // 2. 查询 meter 用量汇总（含日结欠费 outstanding）
        TenancyUsageSummary usage = meterQueryPort.getUsageSummaryForTenancy(cmd.tenancyId());
        BigDecimal outstanding = usage.outstanding();

        // 3. 查询个人账户余额
        BigDecimal surplus = accountQueryPort.getAvailableBalance(cmd.tenantAccountId());

        BigDecimal waterOutstanding = BigDecimal.ZERO;
        BigDecimal electricityOutstanding = BigDecimal.ZERO;
        // 注：目前 outstanding 是水+电合计，可在 TenancyUsageSummary 扩展分项
        // 简化处理：将总欠费记为一项
        BigDecimal totalOutstanding = outstanding;

        // ── 4. 结算逻辑 ──────────────────────────────────────────────

        if (totalOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            // 有欠费
            items.add(new SettlementItemRecord(
                "WATER_ELECTRIC_OUTSTANDING",
                "入住期间水电欠费",
                totalOutstanding, "CHARGE"
            ));

            BigDecimal canCoverFromAccount = surplus.min(totalOutstanding);
            BigDecimal netCharge = totalOutstanding.subtract(canCoverFromAccount);

            if (canCoverFromAccount.compareTo(BigDecimal.ZERO) > 0) {
                // 先冻结账户余额用于抵扣
                accountCommandPort.freeze(cmd.tenantAccountId(), canCoverFromAccount, "退宿结算冻结");
            }

            if (netCharge.compareTo(BigDecimal.ZERO) > 0) {
                // 余额不足以覆盖全部欠费 → 生成追缴账单
                // Long billId = billingServiceClient.createSettlementBill(settlementId, netCharge, ...)
                Long billId = 0L; // TODO: replace with actual call

                settlementRepo.update(settlementId,
                    totalOutstanding, surplus.subtract(canCoverFromAccount),
                    BigDecimal.ZERO, BigDecimal.ZERO, billId, "PENDING_PAYMENT"
                );
                itemRepo.insertAll(settlementId, items);
                // 等待 Billing Service 回调 /internal/v1/settlement/bill-paid
                return;
            } else {
                // 账户余额足以覆盖全部欠费
                accountCommandPort.deduct(new DeductCommand(
                    cmd.tenantAccountId(), totalOutstanding,
                    "SETTLEMENT", settlementId, "退宿结算扣款"
                ));
                BigDecimal remainSurplus = surplus.subtract(totalOutstanding);
                if (remainSurplus.compareTo(BigDecimal.ZERO) > 0) {
                    items.add(new SettlementItemRecord(
                        "ACCOUNT_SURPLUS", "账户剩余余额退还", remainSurplus, "REFUND"
                    ));
                    // accountCommandPort.refund(cmd.tenantAccountId(), remainSurplus, ...)
                    // TODO: 调 Billing Service 发起退款
                }
            }
        } else {
            // 无欠费
            if (surplus.compareTo(BigDecimal.ZERO) > 0) {
                items.add(new SettlementItemRecord(
                    "ACCOUNT_SURPLUS", "账户剩余余额退还", surplus, "REFUND"
                ));
                // TODO: 调 Billing Service 发起退款
            }
        }

        // 关闭账户
        accountCommandPort.closeAccount(cmd.tenantAccountId(), "退宿结算完成");

        // 更新结算单 → COMPLETED
        settlementRepo.update(settlementId,
            totalOutstanding, surplus, BigDecimal.ZERO, BigDecimal.ZERO,
            null, "COMPLETED"
        );
        settlementRepo.setCompletedAt(settlementId, OffsetDateTime.now());
        itemRepo.insertAll(settlementId, items);

        // 回调 contract 模块
        callbackContractSettlementComplete(cmd.tenancyId(), settlementId);
    }

    /**
     * 处理追缴账单支付成功回调（由 InternalSettlementApiDelegateImpl 调用）。
     */
    @Transactional
    public void onSettlementBillPaid(Long settlementId, Long billId, OffsetDateTime paidAt) {
        Object settlement = settlementRepo.findById(settlementId)
            .orElseThrow(() -> new IllegalArgumentException("结算单不存在 id=" + settlementId));

        // 幂等：已 COMPLETED 则直接返回
        if ("COMPLETED".equals(getStatus(settlement))) return;

        Long tenantAccountId = getTenantAccountIdFromContext(settlement); // 从 cmd 存储或查 tenancy

        // 解冻并扣款
        BigDecimal frozenAmount = getFrozenAmount(settlement);
        accountCommandPort.deduct(new DeductCommand(
            tenantAccountId, frozenAmount, "SETTLEMENT", settlementId, "追缴账单已支付"
        ));
        accountCommandPort.closeAccount(tenantAccountId, "追缴账单支付完成，结算关闭");

        // 更新结算单 → COMPLETED
        settlementRepo.updateStatus(settlementId, "COMPLETED");
        settlementRepo.setCompletedAt(settlementId, paidAt);

        // 查 tenancyId 回调 contract
        Long tenancyId = getTenancyIdFromSettlement(settlement);
        callbackContractSettlementComplete(tenancyId, settlementId);
    }

    private void callbackContractSettlementComplete(Long tenancyId, Long settlementId) {
        // 调用 contract 内部接口（同进程直接调用 CheckoutService.onTenancySettlementComplete）
        // 微服务化后改为 HTTP 调用 /internal/v1/contract/tenancy-settlement-complete
        // 当前：直接注入 CheckoutService 调用（避免循环依赖：settlement → contract，单向）
        // 注意：CheckoutService 需要通过 Spring 注入而非直接 new，此处省略
        throw new UnsupportedOperationException("TODO: inject CheckoutService and call onTenancySettlementComplete");
    }

    private String generateSettlementNo() {
        // STL-{yyyyMM}-{seq}
        throw new UnsupportedOperationException("TODO: implement");
    }

    // 以下 getter 均待 jOOQ codegen 后实现
    private String getStatus(Object settlement) { throw new UnsupportedOperationException("TODO"); }
    private BigDecimal getFrozenAmount(Object s) { throw new UnsupportedOperationException("TODO"); }
    private Long getTenantAccountIdFromContext(Object s) { throw new UnsupportedOperationException("TODO"); }
    private Long getTenancyIdFromSettlement(Object s) { throw new UnsupportedOperationException("TODO"); }

    record SettlementItemRecord(String itemType, String description, BigDecimal amount, String direction) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// ContractTerminationSettlementService.java  ← 合同退房结算
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.settlement.service;

import com.jugu.propertylease.main.account.api.*;
import com.jugu.propertylease.main.contract.api.*;
import com.jugu.propertylease.main.settlement.repo.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class ContractTerminationSettlementService {

    private final SettlementRepository settlementRepo;
    private final ContractQueryPort contractQueryPort;
    private final AccountQueryPort accountQueryPort;
    private final AccountCommandPort accountCommandPort;
    // 注入 billingServiceClient

    // 构造器注入（省略）

    public void initiateContractTermination(Long contractId) {
        String settlementNo = generateSettlementNo();
        Long settlementId = settlementRepo.insert(
            settlementNo, "CONTRACT_TERMINATION", contractId, "CALCULATING"
        );

        // 1. 汇总所有 ROOM_SHARED 账户余额（全部退还企业）
        List<ContractRoomInfo> rooms = contractQueryPort.getActiveRoomsByContract(contractId);
        BigDecimal totalSharedSurplus = BigDecimal.ZERO;
        for (ContractRoomInfo room : rooms) {
            if (room.sharedAccountId() != null) {
                BigDecimal bal = accountQueryPort.getAvailableBalance(room.sharedAccountId());
                totalSharedSurplus = totalSharedSurplus.add(bal);
                // 关闭 ROOM_SHARED 账户
                accountCommandPort.closeAccount(room.sharedAccountId(), "合同终止结算");
            }
        }

        // 2. 押金计算：deposit_amount - 未付租金账单金额 = 应退押金
        ContractInfo contract = contractQueryPort.getContract(contractId);
        BigDecimal depositAmount = contract.depositAmount();
        // BigDecimal unpaidRent = rentBillRepo.sumUnpaid(contractId);  // 未付租金
        // BigDecimal depositDeduction = unpaidRent.min(depositAmount);
        // BigDecimal depositRefund = depositAmount.subtract(depositDeduction);
        BigDecimal depositDeduction = BigDecimal.ZERO; // TODO: 实现未付租金计算
        BigDecimal depositRefund = depositAmount.subtract(depositDeduction);

        // 3. 调 Billing Service 生成最终退款账单（ROOM_SHARED 余额 + 押金应退部分）
        BigDecimal totalRefund = totalSharedSurplus.add(depositRefund);
        // billingServiceClient.createRefundBill(contractId, totalRefund, ...)

        // 4. 更新结算单 → COMPLETED
        // 5. 更新 contract.status = TERMINATED
        throw new UnsupportedOperationException("TODO: implement");
    }

    private String generateSettlementNo() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettlementCommandPortImpl.java（Port 组合实现）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.settlement.service;

import com.jugu.propertylease.main.settlement.api.*;
import org.springframework.stereotype.Service;

@Service
public class SettlementCommandPortImpl implements SettlementCommandPort {

    private final TenantCheckoutSettlementService checkoutService;
    private final ContractTerminationSettlementService terminationService;

    public SettlementCommandPortImpl(
        TenantCheckoutSettlementService checkoutService,
        ContractTerminationSettlementService terminationService
    ) {
        this.checkoutService = checkoutService;
        this.terminationService = terminationService;
    }

    @Override
    public void initiateCheckout(InitiateCheckoutCommand cmd) {
        checkoutService.initiateCheckout(cmd);
    }

    @Override
    public void initiateContractTermination(Long contractId) {
        terminationService.initiateContractTermination(contractId);
    }
}

// =============================================================================
// SCHEDULE MODULE
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// ScheduleTaskRunner.java  ← Cron 入口（只触发，无业务逻辑）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.schedule.service;

import com.jugu.propertylease.main.contract.api.ContractScheduleTrigger;
import com.jugu.propertylease.main.meter.api.MeterScheduleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 定时任务唯一入口。
 *
 * <p>原则：本类只负责触发，不含任何业务逻辑。
 * 实际工作委托给各模块的 ScheduleTrigger Interface 实现。
 *
 * <p>需要在 MainServiceApplication 或配置类上添加 @EnableScheduling。
 */
@Service
public class ScheduleTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTaskRunner.class);

    private final ScheduleTaskLogService taskLogService;
    private final MeterScheduleTrigger meterScheduleTrigger;
    private final ContractScheduleTrigger contractScheduleTrigger;

    public ScheduleTaskRunner(
        ScheduleTaskLogService taskLogService,
        MeterScheduleTrigger meterScheduleTrigger,
        ContractScheduleTrigger contractScheduleTrigger
    ) {
        this.taskLogService = taskLogService;
        this.meterScheduleTrigger = meterScheduleTrigger;
        this.contractScheduleTrigger = contractScheduleTrigger;
    }

    /** 每日 02:00 — 水电日结 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMeterSettlement() {
        LocalDate targetDate = LocalDate.now().minusDays(1); // 结算昨日数据
        runTask("DAILY_METER_SETTLEMENT", targetDate, false, () -> {
            var result = meterScheduleTrigger.triggerDailySettlement(targetDate);
            return new TaskRunResult(result.totalRooms(), result.settledRooms(), result.failedRooms(),
                result.failedRooms() > 0 ? "部分失败，failedRooms=" + result.failedRooms() : null);
        });
    }

    /** 每日 03:00 — 租金账单检查 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void rentBillCheck() {
        LocalDate today = LocalDate.now();
        runTask("RENT_BILL_CHECK", today, false, () -> {
            contractScheduleTrigger.checkAndGenerateRentBills(today);
            return new TaskRunResult(0, 0, 0, null);
        });
    }

    /** 每日 01:00 — 合同到期检查 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void contractExpiryCheck() {
        LocalDate today = LocalDate.now();
        runTask("CONTRACT_EXPIRY_CHECK", today, false, () -> {
            contractScheduleTrigger.checkContractExpiry(today);
            return new TaskRunResult(0, 0, 0, null);
        });
    }

    /**
     * 通用任务执行包装（防重 + 日志记录）。
     */
    public void runTask(String taskType, LocalDate targetDate, boolean force, TaskBody body) {
        Long logId = taskLogService.tryStartTask(taskType, targetDate, force, "CRON");
        if (logId == null) {
            log.info("任务已执行，跳过 taskType={} date={}", taskType, targetDate);
            return;
        }
        try {
            TaskRunResult result = body.run();
            String status = result.failureCount() > 0 ? "PARTIAL_FAILURE" : "SUCCESS";
            taskLogService.finishTask(logId, status,
                result.totalCount(), result.successCount(), result.failureCount(),
                result.errorSummary());
        } catch (Exception e) {
            log.error("任务执行失败 taskType={} date={}", taskType, targetDate, e);
            taskLogService.finishTask(logId, "FAILED", 0, 0, 0, e.getMessage());
        }
    }

    @FunctionalInterface
    public interface TaskBody {
        TaskRunResult run() throws Exception;
    }

    public record TaskRunResult(
        int totalCount, int successCount, int failureCount, String errorSummary
    ) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// ScheduleTaskLogService.java  ← 防重 + 日志记录
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.schedule.service;

import com.jugu.propertylease.main.schedule.repo.ScheduleTaskLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
public class ScheduleTaskLogService {

    private final ScheduleTaskLogRepository logRepo;

    public ScheduleTaskLogService(ScheduleTaskLogRepository logRepo) {
        this.logRepo = logRepo;
    }

    /**
     * 尝试开始任务（防重检查 + 插入执行记录）。
     *
     * <p>利用 (task_type, scheduled_date) 唯一约束实现防重：
     * - 若已存在 SUCCESS 记录且 force=false → 返回 null（跳过）
     * - 若已存在 RUNNING 记录 → 返回 null（跳过，防并发重入）
     * - 若已存在 FAILED/PARTIAL_FAILURE 记录 → 更新状态为 RUNNING（允许重跑）
     * - 若不存在 → 插入新记录
     *
     * <p>使用独立事务（REQUIRES_NEW），确保日志记录不受外层事务影响。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long tryStartTask(String taskType, LocalDate date, boolean force, String triggeredBy) {
        return logRepo.findByTypeAndDate(taskType, date)
            .map(existing -> {
                String status = getStatus(existing);
                if ("RUNNING".equals(status)) {
                    return null; // 正在执行，跳过
                }
                if ("SUCCESS".equals(status) && !force) {
                    return null; // 已成功且非强制，跳过
                }
                // FAILED / PARTIAL_FAILURE / SUCCESS(force) → 更新为 RUNNING 重跑
                logRepo.updateToRunning(getId(existing), OffsetDateTime.now());
                return getId(existing);
            })
            .orElseGet(() -> logRepo.insert(taskType, date, "RUNNING", triggeredBy, OffsetDateTime.now()));
    }

    /**
     * 任务完成时更新日志（独立事务，确保即便业务失败也能记录）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishTask(Long logId, String status,
                           int total, int success, int failure, String errorSummary) {
        logRepo.updateFinished(logId, status, total, success, failure,
            errorSummary, OffsetDateTime.now());
    }

    private String getStatus(Object log) { throw new UnsupportedOperationException("TODO after jOOQ codegen"); }
    private Long   getId(Object log)     { throw new UnsupportedOperationException("TODO after jOOQ codegen"); }
}

// ─────────────────────────────────────────────────────────────────────────────
// ScheduleTasksApiDelegateImpl.java  ← 手动触发 API 委托实现
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.schedule.delegate;

import com.jugu.propertylease.main.contract.api.ContractScheduleTrigger;
import com.jugu.propertylease.main.meter.api.MeterScheduleTrigger;
import com.jugu.propertylease.main.schedule.service.ScheduleTaskLogService;
import com.jugu.propertylease.main.schedule.service.ScheduleTaskRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Schedule 外部 API 委托实现（薄转接层）。
 * 手动触发时复用 ScheduleTaskRunner.runTask() 确保防重逻辑一致。
 */
@Service
public class ScheduleTasksApiDelegateImpl
    implements com.jugu.propertylease.main.api.ScheduleTasksApiDelegate {

    private final ScheduleTaskRunner taskRunner;
    private final ScheduleTaskLogService taskLogService;
    private final MeterScheduleTrigger meterScheduleTrigger;
    private final ContractScheduleTrigger contractScheduleTrigger;

    // 构造器注入（省略）

    // operationId: triggerTask
    public Object triggerTask(String taskType, Object request) {
        // 解析 request.targetDate（默认昨日）、request.force（默认 false）
        LocalDate targetDate = LocalDate.now().minusDays(1); // TODO: parse from request
        boolean force = false; // TODO: parse from request

        // 复用 ScheduleTaskRunner 的统一防重逻辑
        final String taskTypeFinal = taskType;
        final LocalDate dateFinal = targetDate;
        taskRunner.runTask(taskType, targetDate, force, () -> {
            return switch (taskTypeFinal) {
                case "DAILY_METER_SETTLEMENT" -> {
                    var r = meterScheduleTrigger.triggerDailySettlement(dateFinal);
                    yield new ScheduleTaskRunner.TaskRunResult(
                        r.totalRooms(), r.settledRooms(), r.failedRooms(),
                        r.failedRooms() > 0 ? "failedRooms=" + r.failedRooms() : null
                    );
                }
                case "RENT_BILL_CHECK" -> {
                    contractScheduleTrigger.checkAndGenerateRentBills(dateFinal);
                    yield new ScheduleTaskRunner.TaskRunResult(0, 0, 0, null);
                }
                case "CONTRACT_EXPIRY_CHECK" -> {
                    contractScheduleTrigger.checkContractExpiry(dateFinal);
                    yield new ScheduleTaskRunner.TaskRunResult(0, 0, 0, null);
                }
                default -> throw new IllegalArgumentException("未知任务类型: " + taskTypeFinal);
            };
        });

        // 返回 TaskTriggerResult（taskLogId, taskType, targetDate, message）
        throw new UnsupportedOperationException("TODO: build TaskTriggerResult response");
    }
}
