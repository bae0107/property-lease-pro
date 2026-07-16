// =============================================================================
// Repository 接口定义（所有模块）
// 命名规则：{Entity}Repository（接口）→ Jooq{Entity}Repository（实现）
// 包路径：com.jugu.propertylease.main.{module}.repo
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// AccountRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.repo;

import java.math.BigDecimal;
import java.util.Optional;

public interface AccountRepository {
    Optional<Object> findById(Long id);
    Optional<Object> findActiveByOwner(String ownerType, Long ownerId);
    Optional<Object> findByOwner(String ownerType, Long ownerId);
    Long insert(Object entity);
    void updateBalance(Long id, BigDecimal balance, BigDecimal frozenBalance);
    void updateStatus(Long id, String status);
}

// ─────────────────────────────────────────────────────────────────────────────
// AccountTransactionRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.account.repo;

import java.math.BigDecimal;

public interface AccountTransactionRepository {
    Long insert(Long accountId, String txnType, String direction,
                BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                String refType, Long refId, String note, Long createdBy);
    boolean existsByReferenceTypeAndId(String refType, Long refId);
}

// ─────────────────────────────────────────────────────────────────────────────
// MeterReadingRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.repo;

import com.jugu.propertylease.main.meter.api.MeterReadingInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface MeterReadingRepository {
    Long insert(Object entity);
    Optional<MeterReadingInfo> findLatestReading(Long roomId, String meterType);
    boolean existsByDeviceIdAndReadAt(String deviceId, OffsetDateTime readAt);

    /**
     * 查询日结起始读数：
     * 优先取 date 前一天的最新 PERIODIC 读数，
     * 若不存在则取 CHECK_IN 底数（tenancy 最早的入住读数）。
     */
    Optional<BigDecimal> findStartReading(Long roomId, String meterType, LocalDate date);

    /**
     * 查询日结截止读数：date 当日最新 PERIODIC 读数。
     */
    Optional<BigDecimal> findEndReading(Long roomId, String meterType, LocalDate date);

    /**
     * 查询某 tenancy 入住期间所有 PERIODIC 读数，用于用量汇总。
     */
    java.util.List<Object> findByTenancyPeriod(Long roomId, String meterType,
                                                OffsetDateTime checkInAt, OffsetDateTime checkOutAt);
}

// ─────────────────────────────────────────────────────────────────────────────
// MeterDailySettlementRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface MeterDailySettlementRepository {
    boolean exists(Long roomId, LocalDate date, String meterType);
    Long insert(Long roomId, LocalDate date, String meterType,
                BigDecimal startReading, BigDecimal endReading,
                BigDecimal usage, BigDecimal unitPrice, BigDecimal totalAmount,
                BigDecimal deductedFromShared, BigDecimal deductedFromTenants,
                BigDecimal shortfall, String status);
    void insertFailed(Long roomId, String meterType, LocalDate date, String errorMsg);

    /** settlement 用：汇总某 tenancy 入住期间所有日结记录 */
    List<Object> findByRoomAndPeriod(Long roomId, LocalDate from, LocalDate to);
}

// ─────────────────────────────────────────────────────────────────────────────
// MeterPriceConfigRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeterPriceConfigRepository {
    /**
     * 查找有效单价：优先 store_id 匹配，fallback null（全局默认）。
     * effective_from <= date AND (effective_to IS NULL OR effective_to >= date)
     */
    Optional<BigDecimal> findEffectivePrice(Long storeId, String meterType, LocalDate date);
    void closeCurrentVersion(Long storeId, String meterType, LocalDate effectiveTo);
    Long insert(Long storeId, String meterType, BigDecimal unitPrice, LocalDate effectiveFrom);
    List<Object> findAll(Long storeId, String meterType);
}

// ─────────────────────────────────────────────────────────────────────────────
// ContractRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.repo;

import com.jugu.propertylease.main.contract.api.ContractInfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractRepository {
    Optional<Object> findById(Long id);
    Long insert(Object entity);
    void updateStatus(Long id, String status);
    void updateDepositBillId(Long id, Long billId);
    void updateDepositPaidAt(Long id, java.time.OffsetDateTime paidAt);
    void updateTerminateReason(Long id, String reason);
    void updateCancelReason(Long id, String reason);
    void updateTermsDocUrl(Long id, String url);

    /** schedule: 查询所有需要生成租金账单的 IN_PROGRESS 合同 */
    List<ContractInfo> findContractsNeedingRentBill(LocalDate date);

    /** schedule: 查询即将到期或已到期的合同 */
    List<ContractInfo> findContractsDueBy(LocalDate date);
}

// ─────────────────────────────────────────────────────────────────────────────
// ContractRoomRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ContractRoomRepository {
    Optional<Object> findActiveByContractAndRoom(Long contractId, Long roomId);
    List<Object> findByContractId(Long contractId);
    List<Object> findActiveByContractId(Long contractId);
    boolean existsActiveByRoomId(Long roomId);   // 校验房间是否已在某活跃合同中
    Long insert(Long contractId, Long roomId, Long operatorId);
    void updateSharedAccountId(Long contractRoomId, Long sharedAccountId);
    void updateStatus(Long contractRoomId, String status, OffsetDateTime releasedAt);
    int countActiveByContract(Long contractId);  // 用于判断合同是否可进入 TERMINATED
}

// ─────────────────────────────────────────────────────────────────────────────
// TenancyRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.repo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TenancyRepository {
    Optional<Object> findById(Long id);
    Optional<Object> findActiveByEmployee(Long employeeId);   // PENDING/CHECKED_IN/CHECKING_OUT
    List<Object> findByContractRoomId(Long contractRoomId);
    List<Object> findActiveByRoomId(Long roomId);             // meter 日结查询入住人

    Long insert(Long contractId, Long contractRoomId, Long roomId,
                Long employeeId, Long iamUserId, Long tenantAccountId,
                String status, OffsetDateTime checkInAt,
                LocalDate expectedCheckOutAt, Long createdBy);

    void updateStatus(Long id, String status, OffsetDateTime checkOutAt);
    void updateIamUserId(Long id, Long iamUserId);
    void updateTenantAccountId(Long id, Long accountId);

    int countActiveByContractRoom(Long contractRoomId); // 判断 contractRoom 是否可 RELEASED

    /** ContractQueryPort 用：meter 日结查询所有有活跃 tenancy 的 roomId */
    List<Long> findRoomIdsWithActiveTenancies();
}

// ─────────────────────────────────────────────────────────────────────────────
// ContractRentBillRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ContractRentBillRepository {
    boolean exists(Long contractId, LocalDate periodStart);
    Long insert(Long contractId, Long billId, LocalDate periodStart, LocalDate periodEnd,
                BigDecimal amount, String status);
    void updateStatus(Long id, String status, java.time.OffsetDateTime paidAt);
    BigDecimal sumUnpaid(Long contractId);  // 合同退房时计算未付租金总额
}

// ─────────────────────────────────────────────────────────────────────────────
// ContractOperationLogRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.contract.repo;

public interface ContractOperationLogRepository {
    void insert(Long contractId, String action, Long targetId, Long operatorId, String note);
}

// ─────────────────────────────────────────────────────────────────────────────
// SettlementRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.settlement.repo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface SettlementRepository {
    Optional<Object> findById(Long id);
    Long insert(String settlementNo, String type, Long referenceId, String status);
    void update(Long id, BigDecimal outstanding, BigDecimal surplus,
                BigDecimal depositDeduction, BigDecimal depositRefund,
                Long settlementBillId, String status);
    void updateStatus(Long id, String status);
    void setCompletedAt(Long id, OffsetDateTime completedAt);
}

// ─────────────────────────────────────────────────────────────────────────────
// SettlementItemRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.settlement.repo;

import java.math.BigDecimal;
import java.util.List;

public interface SettlementItemRepository {
    void insertAll(Long settlementId,
                   List<TenantCheckoutSettlementService.SettlementItemRecord> items);
}

// ─────────────────────────────────────────────────────────────────────────────
// ScheduleTaskLogRepository.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.schedule.repo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface ScheduleTaskLogRepository {
    Optional<Object> findByTypeAndDate(String taskType, LocalDate date);
    Long insert(String taskType, LocalDate date, String status,
                String triggeredBy, OffsetDateTime startedAt);
    void updateToRunning(Long id, OffsetDateTime startedAt);
    void updateFinished(Long id, String status, int total, int success, int failure,
                        String errorSummary, OffsetDateTime finishedAt);
}
