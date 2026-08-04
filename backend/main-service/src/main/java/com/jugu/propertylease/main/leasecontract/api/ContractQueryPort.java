package com.jugu.propertylease.main.leasecontract.api;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * 按 contract_room.id 反查合同房间信息（换宿场景：请求只携带 toContractRoomId）。
     *
     * @throws com.jugu.propertylease.common.exception.BusinessException 404 若不存在
     */
    ContractRoomInfo getContractRoomById(Long contractRoomId);

    /** schedule 用：查询即将到期（endDate <= date）的活跃合同。*/
    List<ContractInfo> findContractsDueBy(LocalDate date);
}
