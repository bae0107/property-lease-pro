// =============================================================================
// 模块间 Port Interface 定义（所有模块）
// 包路径规则：com.jugu.propertylease.main.{module}.api
// 其他模块只能依赖 api 包，不能依赖 service / repo 包
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// CONTRACT MODULE Ports
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.api;

import java.time.LocalDate;
import java.util.List;

/** 供 occupancy / metering / accounting 查询合同数据 */
public interface ContractQueryPort {
    ContractInfo getContract(Long contractId);
    ContractRoomInfo getContractRoom(Long contractRoomId);
    /** 校验合同当前状态是否允许分配（SIGN_BILL_PENDING 或 READY_FOR_CHECK_IN）*/
    boolean isContractAllowingAssignment(Long contractId);
    /** 校验合同当前状态是否允许入住（READY_FOR_CHECK_IN）*/
    boolean isContractReadyForCheckIn(Long contractId);
    /** schedule 用：查询即将到期的合同 */
    List<ContractInfo> findContractsDueBy(LocalDate date);
}

/** 供 accounting 回调驱动合同状态推进 */
public interface ContractCallbackPort {
    /** 签约账单支付成功 → READY_FOR_CHECK_IN；幂等 */
    void onSignBillPaid(Long contractId, Long billId);
    /** 退房结算完成 → COMPLETED */
    void onSettlementCompleted(Long contractId);
}

// Records
package com.jugu.propertylease.main.contract.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractInfo(
    Long       id,
    String     contractNo,
    Long       enterpriseId,
    String     contractStatus,
    LocalDate  startDate,
    LocalDate  endDate,
    String     paymentMode
) {}

public record ContractRoomInfo(
    Long       id,
    Long       contractId,
    Long       roomId,
    BigDecimal signedRent,
    String     status
) {}

// ─────────────────────────────────────────────────────────────────────────────
// OCCUPANCY MODULE Ports
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.occupancy.api;

import java.time.LocalDate;
import java.util.List;

/** 供 metering / accounting / contract 查询居住关系 */
public interface OccupancyQueryPort {
    /** metering 日结用：查询指定日期有 CHECKED_IN stay 的所有 roomId */
    List<Long> findRoomIdsWithCheckedInStays(LocalDate date);
    /** metering 日结用：查询房间当前所有 CHECKED_IN stay */
    List<StayInfo> getCheckedInStaysByRoom(Long roomId);
    /** accounting 结算用：查询房间是否有 CHECKED_IN stay（退房前置校验）*/
    boolean hasCheckedInStays(Long roomId);
    /** 查询 stay 详情 */
    StayInfo getStay(Long stayId);
    /** 查询当前有效 assignment 数量（容量校验）*/
    int countAssignedByRoom(Long roomId);
    /** 查询当前 CHECKED_IN stay 数量（容量校验）*/
    int countCheckedInByRoom(Long roomId);
}

// Records
package com.jugu.propertylease.main.occupancy.api;

import java.time.OffsetDateTime;

public record StayInfo(
    Long           id,
    Long           contractId,
    Long           contractRoomId,
    Long           roomId,
    Long           tenantId,
    String         stayStatus,
    OffsetDateTime checkInAt
) {}

// ─────────────────────────────────────────────────────────────────────────────
// METERING MODULE Ports
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.metering.api;

import java.time.LocalDate;

/** 供 occupancy 在入住/退宿/换宿时触发锚点读数采集 */
public interface MeteringCommandPort {
    /** 入住锚点读数采集，返回 readingGroupId */
    String collectCheckInReadings(CollectReadingsCommand cmd);
    /** 退宿锚点读数采集，返回用量摘要（供 accounting 结算参考）*/
    StayUsageSummary collectCheckOutReadings(CollectReadingsCommand cmd);
}

/** 供 schedule 触发日结 */
public interface MeteringScheduleTrigger {
    /** 幂等；date 通常为 yesterday */
    DailySettlementResult runDailySettlement(LocalDate date);
}

// Records
package com.jugu.propertylease.main.metering.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CollectReadingsCommand(
    Long   stayId,
    Long   roomId,
    String anchorType,          // CHECK_IN | CHECK_OUT
    List<ManualReadingEntry> manualReadings,  // 设备离线时人工补录
    Long   operatorId
) {}

public record ManualReadingEntry(String meterType, BigDecimal readingValue) {}

public record StayUsageSummary(
    Long       stayId,
    BigDecimal waterUsage,
    BigDecimal electricUsage,
    BigDecimal hotWaterUsage,
    BigDecimal totalAmount,        // 按单价计算的应付总额
    BigDecimal alreadyDeducted,    // 日结期间已扣款
    BigDecimal outstanding         // 欠费（尚未扣款）
) {}

public record DailySettlementResult(
    LocalDate  date,
    int        totalRooms,
    int        settledRooms,
    int        partialRooms,
    int        failedRooms,
    BigDecimal totalAmount,
    BigDecimal totalShortfall
) {}

// ─────────────────────────────────────────────────────────────────────────────
// ACCOUNTING MODULE Ports
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.accounting.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供 contract / occupancy / metering 触发账务操作。
 * 所有写操作幂等设计，重复调用安全。
 */
public interface AccountingCommandPort {

    // ── contract 触发 ──────────────────────────────────────────────

    /** confirmContract：创建企业签约账单 + 企业押金台账 */
    CreateBillResult createEnterpriseSignBill(EnterpriseSignBillCommand cmd);

    /** 部分退房：对退回房间触发结算 */
    void settlePartialReturn(PartialReturnCommand cmd);

    /** 全部退房：对所有房间 + 企业押金触发结算 */
    void settleFullReturn(Long contractId);

    // ── occupancy 触发 ──────────────────────────────────────────────

    /** checkIn：创建个人押金账单 + 个人押金台账 */
    CreateBillResult createPersonalDepositBill(PersonalDepositBillCommand cmd);

    /** transfer：迁移押金资格（current_stay_id 更新） */
    void transferPersonalDepositEligibility(Long tenantId, Long fromStayId, Long toStayId);

    /** transfer：迁移租客子余额（全额迁移到目标房间账户） */
    TransferSubBalanceResult transferTenantSubBalance(Long tenantId, Long fromRoomId, Long toRoomId);

    /** checkOut：结算个人押金（生成退款账单） */
    DepositSettlementResult settlePersonalDepositOnCheckout(Long tenantId, Long stayId);

    // ── metering 触发 ──────────────────────────────────────────────

    /** 日结扣减：企业子余额 → 租客子余额均摊 */
    DailyDeductionResult deductForDailySettlement(DailyDeductionCommand cmd);
}

/** 供 contract / schedule 查询账务信息 */
public interface AccountingQueryPort {
    /** 查询房间账户子余额（metering 日结前查企业余额） */
    BigDecimal getEnterpriseSubBalance(Long roomId, Long enterpriseId);
    /** 查询押金台账状态 */
    DepositLedgerInfo getPersonalDepositLedger(Long tenantId, Long stayId);
}

// ── Command / Result Records ──────────────────────────────────────────────────

package com.jugu.propertylease.main.accounting.api;

import java.math.BigDecimal;
import java.util.List;

public record EnterpriseSignBillCommand(
    Long       contractId,
    Long       enterpriseId,
    BigDecimal totalAmount,    // 首月租金 + 企业押金
    BigDecimal depositAmount   // 其中押金部分
) {}

public record PersonalDepositBillCommand(
    Long tenantId,
    Long stayId,
    Long contractId,
    Long roomId
) {}

public record PartialReturnCommand(
    Long       contractId,
    List<Long> returnedRoomIds
) {}

public record DailyDeductionCommand(
    Long             roomId,
    java.time.LocalDate settlementDate,
    BigDecimal       totalAmount,
    BigDecimal       enterpriseCoverAmount,
    List<TenantDeductItem> tenantApportionments
) {}

public record TenantDeductItem(
    Long       tenantId,
    Long       stayId,
    BigDecimal amount
) {}

public record CreateBillResult(
    Long   billId,
    Long   billingServiceBillId,
    String paymentUrl
) {}

public record TransferSubBalanceResult(BigDecimal migratedAmount) {}

public record DepositSettlementResult(
    Long       refundBillId,
    BigDecimal refundAmount
) {}

public record DailyDeductionResult(
    BigDecimal enterpriseDeducted,
    BigDecimal tenantsDeducted,
    BigDecimal shortfall
) {}

public record DepositLedgerInfo(
    Long       id,
    String     status,
    BigDecimal originalAmount,
    BigDecimal occupiedAmount,
    BigDecimal refundableAmount
) {}

// ─────────────────────────────────────────────────────────────────────────────
// IAM MODULE Ports（IAM 模块新增，供 occupancy 调用）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.iam.api;

public interface IamTenantPort {
    /**
     * 分配时调用：创建或激活 TENANT 用户，赋予自助入住权限。
     * 幂等：已存在则更新权限并返回 iamUserId。
     */
    Long createOrEnableTenantUser(CreateTenantUserCommand cmd);

    /**
     * 退宿时调用：软删除 IAM 用户，注销自助入住权限。
     */
    void disableTenantUser(Long iamUserId);
}

public record CreateTenantUserCommand(
    Long   tenantId,
    String mobile,
    String realName
) {}

// ─────────────────────────────────────────────────────────────────────────────
// SCHEDULE MODULE（无对外 Port，直接依赖各模块的 ScheduleTrigger）
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.schedule.service;

import com.jugu.propertylease.main.contract.api.ContractQueryPort;
import com.jugu.propertylease.main.metering.api.MeteringScheduleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ScheduleTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTaskRunner.class);

    private final ScheduleTaskLogService logService;
    private final MeteringScheduleTrigger meteringTrigger;
    private final ContractQueryPort contractQueryPort;

    public ScheduleTaskRunner(
        ScheduleTaskLogService logService,
        MeteringScheduleTrigger meteringTrigger,
        ContractQueryPort contractQueryPort
    ) {
        this.logService = logService;
        this.meteringTrigger = meteringTrigger;
        this.contractQueryPort = contractQueryPort;
    }

    /** 每日 02:00 — 水电日结 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMeterSettlement() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        run("DAILY_METER_SETTLEMENT", targetDate, false, () -> {
            var r = meteringTrigger.runDailySettlement(targetDate);
            return TaskRunResult.of(r.totalRooms(), r.settledRooms(), r.failedRooms(),
                r.failedRooms() > 0 ? "failedRooms=" + r.failedRooms() : null);
        });
    }

    /** 每日 01:00 — 合同到期检查 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void contractExpiryCheck() {
        LocalDate today = LocalDate.now();
        run("CONTRACT_EXPIRY_CHECK", today, false, () -> {
            var expiring = contractQueryPort.findContractsDueBy(today.plusDays(7));
            // TODO: 触发通知
            return TaskRunResult.of(expiring.size(), expiring.size(), 0, null);
        });
    }

    /**
     * 通用执行包装：防重检查 + 日志记录 + 异常兜底。
     * force=true 时允许对已 SUCCESS 的记录重跑。
     */
    public void run(String taskType, LocalDate date, boolean force, TaskBody body) {
        Long logId = logService.tryStart(taskType, date, force, "CRON");
        if (logId == null) {
            log.info("[schedule] skip {} {} — already executed", taskType, date);
            return;
        }
        try {
            TaskRunResult r = body.run();
            String status = r.failureCount() > 0 ? "PARTIAL_FAILURE" : "SUCCESS";
            logService.finish(logId, status, r.totalCount(), r.successCount(), r.failureCount(), r.errorSummary());
        } catch (Exception e) {
            log.error("[schedule] task failed {} {}", taskType, date, e);
            logService.finish(logId, "FAILED", 0, 0, 0, e.getMessage());
        }
    }

    @FunctionalInterface
    public interface TaskBody {
        TaskRunResult run() throws Exception;
    }

    public record TaskRunResult(int totalCount, int successCount, int failureCount, String errorSummary) {
        public static TaskRunResult of(int total, int success, int failure, String error) {
            return new TaskRunResult(total, success, failure, error);
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ScheduleTaskLogService（防重核心，REQUIRES_NEW 独立事务）
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

    private final ScheduleTaskLogRepository repo;

    public ScheduleTaskLogService(ScheduleTaskLogRepository repo) {
        this.repo = repo;
    }

    /**
     * 尝试开启任务：
     * - 无记录 → 插入 RUNNING，返回 logId
     * - 已有 RUNNING → null（跳过，防并发重入）
     * - 已有 SUCCESS 且 force=false → null（跳过）
     * - 已有 SUCCESS 且 force=true / 已有 FAILED / PARTIAL_FAILURE → 更新为 RUNNING，返回 logId
     *
     * 使用 REQUIRES_NEW 确保日志记录不受外层事务影响。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long tryStart(String taskType, LocalDate date, boolean force, String triggeredBy) {
        return repo.findByTypeAndDate(taskType, date)
            .map(existing -> {
                String status = repo.getStatus(existing);
                if ("RUNNING".equals(status)) return null;
                if ("SUCCESS".equals(status) && !force) return null;
                repo.updateToRunning(repo.getId(existing), OffsetDateTime.now(), triggeredBy);
                return repo.getId(existing);
            })
            .orElseGet(() -> repo.insert(taskType, date, "RUNNING", triggeredBy, OffsetDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long logId, String status, int total, int success, int failure, String errorSummary) {
        repo.updateFinished(logId, status, total, success, failure, errorSummary, OffsetDateTime.now());
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 包结构总览（参考）
// ─────────────────────────────────────────────────────────────────────────────
/*
main-service/src/main/java/com/jugu/propertylease/main/

├── contract/
│   ├── api/                ← ContractQueryPort, ContractCallbackPort + Records
│   ├── delegate/           ← ContractContractsApiDelegateImpl, InternalContractApiDelegateImpl
│   ├── repo/               ← ContractRepository, ContractRoomRepository, ContractChargeRuleRepository
│   │   └── jooq/
│   └── service/
│       ├── ContractLifecycleService   ← createDraft / confirm / cancel
│       ├── ContractReturnService      ← partialReturn / fullReturn
│       └── ContractQueryService       ← ContractQueryPort 实现

├── occupancy/
│   ├── api/                ← OccupancyQueryPort + Records
│   ├── delegate/           ← OccupancyAssignmentsApiDelegateImpl, OccupancyStaysApiDelegateImpl
│   ├── repo/               ← AssignmentRepository, StayRepository, TransferRecordRepository
│   └── service/
│       ├── AssignmentService           ← assignTenantToRoom / cancelAssignment
│       ├── CheckInService              ← checkIn（编排：metering+accounting+iam+device）
│       ├── CheckOutService             ← checkOut（编排：metering+accounting+iam+device）
│       ├── TransferService             ← transferTenant（编排：metering+accounting+iam+device）
│       └── OccupancyQueryService       ← OccupancyQueryPort 实现

├── metering/
│   ├── api/                ← MeteringCommandPort, MeteringScheduleTrigger + Records
│   ├── delegate/           ← MeteringReadingsApiDelegateImpl, InternalMeteringApiDelegateImpl
│   ├── repo/               ← MeterDeviceBindingRepository, MeterReadingRepository,
│   │   └── jooq/             SettlementAnchorRepository, RoomDailyChargeRepository,
│   │                         TenantApportionmentRepository, MeterPriceConfigRepository
│   └── service/
│       ├── DeviceBindingService        ← 绑定/解绑/版本管理
│       ├── MeterReadingService         ← 人工录入、IoT 推送（幂等）
│       ├── AnchorReadingService        ← MeteringCommandPort 实现（CHECK_IN/CHECK_OUT 锚点）
│       ├── DailySettlementService      ← MeteringScheduleTrigger 实现（日结核心）
│       └── MeterPriceService           ← 单价版本管理

├── accounting/
│   ├── api/                ← AccountingCommandPort, AccountingQueryPort + Records
│   ├── delegate/           ← AccountingRoomAccountsApiDelegateImpl,
│   │                         AccountingBillsApiDelegateImpl,
│   │                         AccountingDepositLedgersApiDelegateImpl,
│   │                         InternalAccountingApiDelegateImpl
│   ├── repo/               ← RoomAccountRepository, RoomAccountSubBalanceRepository,
│   │   └── jooq/             RoomAccountEntryRepository, BillRepository,
│   │                         DepositLedgerRepository, SystemConfigRepository
│   └── service/
│       ├── RoomAccountService          ← 账户生命周期（创建/激活/关闭）
│       ├── SubBalanceDeductService     ← 日结扣减（AccountingCommandPort.deductForDailySettlement）
│       ├── SubBalanceTransferService   ← 换宿余额迁移
│       ├── BillService                 ← 账单创建/状态管理/支付回调路由
│       ├── DepositLedgerService        ← 押金台账 CRUD + 结算
│       ├── SignBillService             ← 企业签约账单
│       ├── RechargeService             ← 充值账单发起
│       └── SettlementService           ← 退房结算（partial/full）

├── schedule/
│   ├── delegate/           ← ScheduleTasksApiDelegateImpl
│   ├── repo/               ← ScheduleTaskLogRepository
│   └── service/
│       ├── ScheduleTaskRunner          ← @Scheduled Cron 入口
│       └── ScheduleTaskLogService      ← 防重 + 日志记录

└── iam/  (已完成，新增)
    └── api/
        └── IamTenantPort               ← 新增（供 occupancy 调用）
*/
