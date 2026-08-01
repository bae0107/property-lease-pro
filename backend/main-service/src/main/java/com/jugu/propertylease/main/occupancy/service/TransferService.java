package com.jugu.propertylease.main.occupancy.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.AccountingQueryPort;
import com.jugu.propertylease.main.accounting.api.model.DepositLedgerInfo;
import com.jugu.propertylease.main.contract.api.ContractQueryPort;
import com.jugu.propertylease.main.contract.api.ContractRoomInfo;
import com.jugu.propertylease.main.jooq.tables.pojos.Stay;
import com.jugu.propertylease.main.metering.api.CollectReadingsCommand;
import com.jugu.propertylease.main.metering.api.MeteringCommandPort;
import com.jugu.propertylease.main.occupancy.repo.OccupancyRepository;
import com.jugu.propertylease.main.propertymgr.api.AssetQueryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 跨合同换宿编排服务（单一 @Transactional）。
 *
 * <p>编排步骤：
 * <ol>
 *   <li>前置校验：fromStay.status==CHECKED_IN；个人押金 ACTIVE；目标合同 READY_FOR_CHECK_IN；目标容量充足</li>
 *   <li>MeteringCommandPort.collectCheckOutReadings(fromStay)</li>
 *   <li>fromStay → TRANSFERRED</li>
 *   <li>创建目标房间 assignment（ASSIGNED → 立即 CONSUMED，内部直接消费，无需租客二次确认）</li>
 *   <li>创建新 Stay(CHECKED_IN)，沿用原 iam_user_id</li>
 *   <li>MeteringCommandPort.collectCheckInReadings(toStay)</li>
 *   <li>AccountingCommandPort.transferPersonalDepositEligibility</li>
 *   <li>AccountingCommandPort.transferTenantSubBalance</li>
 *   <li>DoorCredentialService：旧 stay 密码作废回收 + 新 stay 生成新密码下发</li>
 *   <li>记录 TransferRecord</li>
 * </ol>
 */
@Service
public class TransferService {

    private static final String DEPOSIT_STATUS_ACTIVE = "ACTIVE";

    private final OccupancyRepository repo;
    private final ContractQueryPort contractQueryPort;
    private final AssetQueryPort assetQueryPort;
    private final MeteringCommandPort meteringCommandPort;
    private final AccountingCommandPort accountingCommandPort;
    private final AccountingQueryPort accountingQueryPort;
    private final DoorCredentialService doorCredentialService;

    public TransferService(OccupancyRepository repo,
                            ContractQueryPort contractQueryPort,
                            AssetQueryPort assetQueryPort,
                            MeteringCommandPort meteringCommandPort,
                            AccountingCommandPort accountingCommandPort,
                            AccountingQueryPort accountingQueryPort,
                            DoorCredentialService doorCredentialService) {
        this.repo = repo;
        this.contractQueryPort = contractQueryPort;
        this.assetQueryPort = assetQueryPort;
        this.meteringCommandPort = meteringCommandPort;
        this.accountingCommandPort = accountingCommandPort;
        this.accountingQueryPort = accountingQueryPort;
        this.doorCredentialService = doorCredentialService;
    }

    @Transactional
    public TransferOutcome transferTenant(Long fromStayId, Long toContractRoomId, Long operatorId) {
        // 1. 校验原 stay
        Stay fromStay = repo.findStayById(fromStayId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "STAY_NOT_FOUND", "入住记录不存在：" + fromStayId));

        if (!"CHECKED_IN".equals(fromStay.getStayStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "STAY_STATUS_INVALID",
                    "仅 CHECKED_IN 状态可换宿，当前：" + fromStay.getStayStatus());
        }

        // 2. 校验个人押金 ACTIVE
        DepositLedgerInfo deposit =
                accountingQueryPort.getPersonalDepositLedger(fromStay.getTenantId(), fromStayId);
        if (deposit == null || !DEPOSIT_STATUS_ACTIVE.equals(deposit.status())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "DEPOSIT_NOT_ACTIVE", "个人押金未处于 ACTIVE 状态，无法换宿 stayId=" + fromStayId);
        }

        // 3. 解析目标合同房间，校验目标合同状态
        ContractRoomInfo toContractRoom = contractQueryPort.getContractRoomById(toContractRoomId);
        Long toContractId = toContractRoom.contractId();
        Long toRoomId = toContractRoom.roomId();

        if (!contractQueryPort.isContractReadyForCheckIn(toContractId)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "CONTRACT_NOT_READY_FOR_CHECK_IN",
                    "目标合同当前状态不允许入住 contractId=" + toContractId);
        }

        // 4. 目标房间容量校验
        int maxOccupancy = assetQueryPort.getMaxOccupancy(toRoomId);
        int assigned = repo.countAssignedByRoom(toRoomId);
        int checkedIn = repo.countCheckedInByRoom(toRoomId);
        if (assigned + checkedIn >= maxOccupancy) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ROOM_CAPACITY_FULL",
                    "目标房间容量已满 roomId=" + toRoomId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long tenantId = fromStay.getTenantId();

        // 5. 采集原房间退宿读数
        meteringCommandPort.collectCheckOutReadings(new CollectReadingsCommand(
                fromStayId, fromStay.getRoomId(), "CHECK_OUT", List.of(), operatorId));

        // 6. fromStay → TRANSFERRED
        repo.updateStayStatus(fromStayId, "TRANSFERRED", now, now);

        // 7. 创建目标房间 assignment，内部直接消费（无需租客二次确认）
        Long newAssignmentId = repo.insertAssignment(toContractId, toContractRoomId,
                toRoomId, tenantId, operatorId, now);
        repo.updateAssignmentStatus(newAssignmentId, "CONSUMED", null, operatorId);

        // 8. 创建新 Stay(CHECKED_IN)
        Long toStayId = repo.insertStay(newAssignmentId, toContractId, toContractRoomId,
                toRoomId, tenantId, operatorId, now, now);

        // 沿用原 iam_user_id（换宿不改变租客的小程序登录身份）
        if (fromStay.getIamUserId() != null) {
            repo.updateIamUserId(toStayId, fromStay.getIamUserId());
        }

        // 9. 采集新房间入住读数
        meteringCommandPort.collectCheckInReadings(new CollectReadingsCommand(
                toStayId, toRoomId, "CHECK_IN", List.of(), operatorId));

        // 10. 迁移押金资格（不重缴）
        accountingCommandPort.transferPersonalDepositEligibility(tenantId, fromStayId, toStayId);

        // 11. 迁移租客子余额
        accountingCommandPort.transferTenantSubBalance(tenantId, fromStay.getRoomId(), toRoomId);

        // 12. 门锁切换：旧 stay 密码作废回收，新 stay 生成新密码下发
        doorCredentialService.revokeForStay(fromStayId, fromStay.getRoomId(), tenantId);
        doorCredentialService.issueForStay(toStayId, toRoomId, tenantId, fromStay.getIamUserId());

        // 13. 记录换宿流水
        Long transferRecordId = repo.insertTransferRecord(tenantId, fromStayId,
                fromStay.getContractId(), fromStay.getRoomId(),
                toStayId, toContractId, toContractRoomId, toRoomId,
                operatorId, now);

        Stay updatedFromStay = repo.findStayById(fromStayId).orElseThrow();
        Stay toStay = repo.findStayById(toStayId).orElseThrow();
        return new TransferOutcome(updatedFromStay, toStay, transferRecordId);
    }
}
