package com.jugu.propertylease.main.accounting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Accounting 模块命令 Port（进程内，供 contract / occupancy / metering 注入调用）。
 */
public interface AccountingCommandPort {

    // ── 企业签约账单（contract.confirmContract 调用）─────────────────────

    /**
     * 创建企业签约账单（首月租金 + 企业押金合计），同时创建 DepositLedger(ENTERPRISE, PENDING_PAYMENT)。
     * 调用 BillingServicePort.createBill 生成支付单（stub 阶段返回模拟数据）。
     */
    CreateBillResult createEnterpriseSignBill(EnterpriseSignBillCommand cmd);

    /**
     * 激活房间共享（ROOM_SHARED）水电账户（contract.onSignBillPaid 调用）。
     *
     * <p>确保 room_account 行存在（幂等：已存在则直接返回，不重复创建），
     * 供后续 metering 日结 / 换宿迁移等操作使用。内部委托已有的
     * {@code JooqAccountingRepository.upsertRoomAccount}（此前只在换宿场景被调用，
     * "签约后激活账户"这条路径此前未接线，属于遗留缺口，本次补上）。
     */
    void activateRoomSharedAccount(Long roomId, Long contractId);

    // ── 个人押金（occupancy.checkIn 调用）─────────────────────────────────

    /**
     * 创建个人押金账单，金额取 system_config.personal_deposit_amount。
     * 同时创建 DepositLedger(PERSONAL, PENDING_PAYMENT)。
     */
    CreateBillResult createPersonalDepositBill(PersonalDepositBillCommand cmd);

    // ── 换宿账务操作（occupancy.transfer 调用）───────────────────────────

    /** 换宿：迁移押金资格（DepositLedger.current_stay_id 更新，押金不重缴）。*/
    void transferPersonalDepositEligibility(Long tenantId, Long fromStayId, Long toStayId);

    /** 换宿：将租客子余额从原房间账户全额迁移至新房间账户（企业子余额不影响）。*/
    TransferSubBalanceResult transferTenantSubBalance(Long tenantId, Long fromRoomId, Long toRoomId);

    // ── 退宿结算（occupancy.checkOut 调用）───────────────────────────────

    /**
     * 退宿：结算个人押金。
     * 计算 refundableAmount = originalAmount - occupiedAmount，
     * DepositLedger → REFUND_PENDING，调用 BillingServicePort.createRefund（stub）。
     */
    DepositSettlementResult settlePersonalDepositOnCheckout(Long tenantId, Long stayId);

    // ── 退房结算（contract.applyPartialReturn / applyFullReturn 调用）───

    /** 部分退房：对指定房间触发余额退还 + 房间账户关闭。*/
    void settlePartialReturn(PartialReturnCommand cmd);

    /** 全部退房：所有房间 + 企业押金全量结算。*/
    void settleFullReturn(Long contractId);

    // ── 日结扣减（metering.runDailySettlement 调用）──────────────────────

    /**
     * 执行日结扣减：企业子余额优先，不足部分按 tenantApportionments 均摊。
     * 幂等键：(roomId, settlementDate)，重复调用直接返回上次结果。
     */
    DailyDeductionResult deductForDailySettlement(DailyDeductionCommand cmd);
}
