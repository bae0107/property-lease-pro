package com.jugu.propertylease.main.contract.api;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.contract.repo.ContractRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * {@link ContractQueryPort} 实现。
 *
 * <p>⚠️ 重建说明：原 ContractQueryPortImpl.java 在本次上传的压缩包中缺失。本文件仅根据
 * {@link ContractQueryPort} 接口 Javadoc 中已明确写出的状态集合重建，未新增任何未声明的业务规则：
 * <ul>
 *   <li>isContractAllowingAssignment → SIGN_BILL_PENDING / READY_FOR_CHECK_IN</li>
 *   <li>isContractReadyForCheckIn → READY_FOR_CHECK_IN / PARTIALLY_RETURNED</li>
 * </ul>
 * findContractsDueBy 用的"活跃合同"范围目前复用 isContractReadyForCheckIn 的状态集合，
 * 这是本文件唯一的推测点 —— 原实现如果口径不同（比如把 SIGN_BILL_PENDING 也算活跃），
 * 请自行调整 {@link #DUE_CHECK_STATUSES}。
 */
@Service
public class ContractQueryPortImpl implements ContractQueryPort {

    private static final Set<String> ASSIGNMENT_ALLOWED_STATUSES =
            Set.of("SIGN_BILL_PENDING", "READY_FOR_CHECK_IN");

    private static final Set<String> CHECK_IN_ALLOWED_STATUSES =
            Set.of("READY_FOR_CHECK_IN", "PARTIALLY_RETURNED");

    /** ⚠️ 推测点，见类注释。*/
    private static final List<String> DUE_CHECK_STATUSES = List.copyOf(CHECK_IN_ALLOWED_STATUSES);

    private final ContractRepository repo;

    public ContractQueryPortImpl(ContractRepository repo) {
        this.repo = repo;
    }

    @Override
    public ContractInfo getContract(Long contractId) {
        return toContractInfo(requireContract(contractId));
    }

    @Override
    public boolean isContractAllowingAssignment(Long contractId) {
        return ASSIGNMENT_ALLOWED_STATUSES.contains(requireContract(contractId).getStatus());
    }

    @Override
    public boolean isContractReadyForCheckIn(Long contractId) {
        return CHECK_IN_ALLOWED_STATUSES.contains(requireContract(contractId).getStatus());
    }

    @Override
    public List<ContractRoomInfo> getActiveRoomsByContract(Long contractId) {
        return repo.findRoomsByContract(contractId, "ACTIVE").stream()
                .map(this::toContractRoomInfo)
                .toList();
    }

    @Override
    public ContractRoomInfo getContractRoomById(Long contractRoomId) {
        ContractRoom room = repo.findRoomById(contractRoomId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "CONTRACT_ROOM_NOT_FOUND", "合同房间不存在：" + contractRoomId));
        return toContractRoomInfo(room);
    }

    @Override
    public List<ContractInfo> findContractsDueBy(LocalDate date) {
        return repo.findExpiringContracts(date, DUE_CHECK_STATUSES).stream()
                .map(this::toContractInfo)
                .toList();
    }

    private Contract requireContract(Long contractId) {
        return repo.findById(contractId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "CONTRACT_NOT_FOUND", "合同不存在：" + contractId));
    }

    private ContractInfo toContractInfo(Contract c) {
        return new ContractInfo(c.getId(), c.getContractNo(), c.getEnterpriseId(),
                c.getStatus(), c.getStartDate(), c.getEndDate(), c.getPaymentMode());
    }

    private ContractRoomInfo toContractRoomInfo(ContractRoom r) {
        return new ContractRoomInfo(r.getId(), r.getContractId(), r.getRoomId(),
                r.getSignedRent(), r.getStatus());
    }
}
