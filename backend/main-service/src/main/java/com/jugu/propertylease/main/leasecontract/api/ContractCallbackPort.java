package com.jugu.propertylease.main.leasecontract.api;

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
