package com.jugu.propertylease.main.contract.api;

import java.time.LocalDate;
import java.util.List;

// ══════════════════════════════════════════════════════════════════════════════
// ContractQueryPort — 供 occupancy / metering / schedule 查询合同数据
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 合同查询 Port（进程内，供 occupancy / metering / schedule 注入调用）。
 */
public interface ContractQueryPort {

    ContractInfo getContract(Long contractId);

    /** 校验合同是否允许分配（SIGN_BILL_PENDING 或 READY_FOR_CHECK_IN）。*/
    boolean isContractAllowingAssignment(Long contractId);

    /** 校验合同是否允许入住（READY_FOR_CHECK_IN 或 PARTIALLY_RETURNED）。*/
    boolean isContractReadyForCheckIn(Long contractId);

    List<ContractRoomInfo> getActiveRoomsByContract(Long contractId);

    /** schedule 用：查询即将到期（endDate <= date）的活跃合同。*/
    List<ContractInfo> findContractsDueBy(LocalDate date);
}

// ══════════════════════════════════════════════════════════════════════════════
// ContractCallbackPort — 供 accounting 回调推进合同状态
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 合同回调 Port（进程内，由 accounting 在支付/结算完成后注入调用）。
 *
 * <p>替代 v4 中 contract-internal.yaml 的 HTTP 接口，
 * accounting 直接调用此接口，无需 HTTP 往返。
 */
public interface ContractCallbackPort {

    /**
     * 企业签约账单支付成功 → 合同 SIGN_BILL_PENDING → READY_FOR_CHECK_IN。
     * 同时为合同下所有 contract_room 激活 ROOM_SHARED 水电账户（通过 AccountingCommandPort）。
     * 幂等：已为 READY_FOR_CHECK_IN 则直接返回。
     */
    void onSignBillPaid(Long contractId, Long billId);

    /**
     * 退房结算完成 → 合同 SETTLING → COMPLETED。
     */
    void onSettlementCompleted(Long contractId);
}

// ══════════════════════════════════════════════════════════════════════════════
// ContractScheduleTrigger — 供 schedule 触发合同周期任务
// ══════════════════════════════════════════════════════════════════════════════

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
}

// ══════════════════════════════════════════════════════════════════════════════
// Port Records
// ══════════════════════════════════════════════════════════════════════════════

package com.jugu.propertylease.main.contract.api;

import java.math.BigDecimal;
import LocalDate;

/**
 * 合同基础信息（Port 层传输对象）。
 */
public record ContractInfo(
        Long id,
        String contractNo,
        Long enterpriseId,
        String contractStatus,
        LocalDate startDate,
        LocalDate endDate,
        String paymentMode
) {}

/**
 * 合同房间信息（Port 层传输对象）。
 */
public record ContractRoomInfo(
        Long id,
        Long contractId,
        Long roomId,
        BigDecimal signedRent,
        String status
) {}
