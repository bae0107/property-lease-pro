package com.jugu.propertylease.main.occupancy.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.model.CreateBillResult;
import com.jugu.propertylease.main.accounting.api.model.PersonalDepositBillCommand;
import com.jugu.propertylease.main.leasecontract.api.ContractQueryPort;
import com.jugu.propertylease.main.customer.api.CustomerQueryPort;
import com.jugu.propertylease.main.customer.api.model.CustomerEmployeeInfo;
import com.jugu.propertylease.main.iam.api.IamTenantPort;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAssignment;
import com.jugu.propertylease.main.jooq.tables.pojos.Stay;
import com.jugu.propertylease.main.metering.api.CollectReadingsCommand;
import com.jugu.propertylease.main.metering.api.MeteringCommandPort;
import com.jugu.propertylease.main.occupancy.repo.OccupancyRepository;
import com.jugu.propertylease.main.assetmgr.api.AssetQueryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 入住办理编排服务（单一 @Transactional，跨 contract / metering / accounting / 门锁 / IAM 协调）。
 *
 * <p>编排步骤：
 * <ol>
 *   <li>校验 assignment.status == ASSIGNED</li>
 *   <li>校验 ContractQueryPort.isContractReadyForCheckIn</li>
 *   <li>容量再校验（CHECKED_IN &lt; maxOccupancy，防并发）</li>
 *   <li>assignment → CONSUMED</li>
 *   <li>创建 Stay(CHECKED_IN)</li>
 *   <li>MeteringCommandPort.collectCheckInReadings</li>
 *   <li>AccountingCommandPort.createPersonalDepositBill</li>
 *   <li>回写 stay.iam_user_id（幂等复用 assignTenantToRoom 阶段已创建的 IAM 用户）</li>
 *   <li>DoorCredentialService.issueForStay（生成门锁密码，加密落库并下发）</li>
 * </ol>
 */
@Service
public class CheckInService {

    private static final String DEPOSIT_STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";

    private final OccupancyRepository repo;
    private final ContractQueryPort contractQueryPort;
    private final CustomerQueryPort customerQueryPort;
    private final AssetQueryPort assetQueryPort;
    private final MeteringCommandPort meteringCommandPort;
    private final AccountingCommandPort accountingCommandPort;
    private final DoorCredentialService doorCredentialService;
    private final IamTenantPort iamTenantPort;

    public CheckInService(OccupancyRepository repo,
                           ContractQueryPort contractQueryPort,
                           CustomerQueryPort customerQueryPort,
                           AssetQueryPort assetQueryPort,
                           MeteringCommandPort meteringCommandPort,
                           AccountingCommandPort accountingCommandPort,
                           DoorCredentialService doorCredentialService,
                           IamTenantPort iamTenantPort) {
        this.repo = repo;
        this.contractQueryPort = contractQueryPort;
        this.customerQueryPort = customerQueryPort;
        this.assetQueryPort = assetQueryPort;
        this.meteringCommandPort = meteringCommandPort;
        this.accountingCommandPort = accountingCommandPort;
        this.doorCredentialService = doorCredentialService;
        this.iamTenantPort = iamTenantPort;
    }

    @Transactional
    public CheckInResult checkIn(Long assignmentId, Long operatorId) {
        // 1. 校验 assignment
        RoomAssignment assignment = repo.findAssignmentById(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "ASSIGNMENT_NOT_FOUND", "分配记录不存在：" + assignmentId));

        if (!"ASSIGNED".equals(assignment.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ASSIGNMENT_STATUS_INVALID",
                    "仅 ASSIGNED 状态可办理入住，当前：" + assignment.getStatus());
        }

        // 2. 校验合同状态
        if (!contractQueryPort.isContractReadyForCheckIn(assignment.getContractId())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "CONTRACT_NOT_READY_FOR_CHECK_IN",
                    "合同当前状态不允许入住 contractId=" + assignment.getContractId());
        }

        // 3. 容量再校验（防并发：分配后到入住前可能有其他并发入住）
        int maxOccupancy = assetQueryPort.getMaxOccupancy(assignment.getRoomId());
        int checkedIn = repo.countCheckedInByRoom(assignment.getRoomId());
        if (checkedIn >= maxOccupancy) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ROOM_CAPACITY_FULL",
                    "房间当前入住人数已达上限 roomId=" + assignment.getRoomId());
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 4. assignment → CONSUMED
        repo.updateAssignmentStatus(assignmentId, "CONSUMED", null, operatorId);

        // 5. 创建 Stay(CHECKED_IN)
        Long stayId = repo.insertStay(assignmentId, assignment.getContractId(),
                assignment.getContractRoomId(), assignment.getRoomId(),
                assignment.getTenantId(), operatorId, now, now);

        // 6. metering 采集入住锚点读数
        meteringCommandPort.collectCheckInReadings(new CollectReadingsCommand(
                stayId, assignment.getRoomId(), "CHECK_IN", List.of(), operatorId));

        // 7. accounting 创建个人押金账单
        CreateBillResult depositBill = accountingCommandPort.createPersonalDepositBill(
                new PersonalDepositBillCommand(assignment.getTenantId(), stayId,
                        assignment.getContractId(), assignment.getRoomId()));

        // 8. 回写 stay.iam_user_id
        //    assignTenantToRoom 阶段已调用 createOrEnableTenantUser 激活账号，但未落库其返回的 id；
        //    此处再次调用同一幂等接口以取回 iam_user.id 并写入 stay（接口文档明确保证幂等：已存在则直接返回）。
        CustomerEmployeeInfo employee = customerQueryPort.getEmployee(assignment.getTenantId());
        Long iamUserId = iamTenantPort.createOrEnableTenantUser(employee.mobile(), employee.name());
        repo.updateIamUserId(stayId, iamUserId);

        // 9. 生成门锁密码（加密落库 ACTIVE + 下发），须在 iam_user_id 回写之后
        doorCredentialService.issueForStay(stayId, assignment.getRoomId(),
                assignment.getTenantId(), iamUserId);

        Stay stay = repo.findStayById(stayId).orElseThrow();
        return new CheckInResult(stay, depositBill.billId(), DEPOSIT_STATUS_PENDING_PAYMENT);
    }
}
