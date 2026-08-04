package com.jugu.propertylease.main.occupancy.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.leasecontract.api.ContractQueryPort;
import com.jugu.propertylease.main.customer.api.CustomerQueryPort;
import com.jugu.propertylease.main.customer.api.model.CustomerEmployeeInfo;
import com.jugu.propertylease.main.iam.api.IamTenantPort;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAssignment;
import com.jugu.propertylease.main.occupancy.repo.OccupancyRepository;
import com.jugu.propertylease.main.assetmgr.api.AssetQueryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class AssignmentService {

    private final OccupancyRepository repo;
    private final ContractQueryPort contractQueryPort;
    private final CustomerQueryPort customerQueryPort;
    private final AssetQueryPort assetQueryPort;
    private final IamTenantPort iamTenantPort;

    public AssignmentService(OccupancyRepository repo,
                              ContractQueryPort contractQueryPort,
                              CustomerQueryPort customerQueryPort,
                              AssetQueryPort assetQueryPort,
                              IamTenantPort iamTenantPort) {
        this.repo = repo;
        this.contractQueryPort = contractQueryPort;
        this.customerQueryPort = customerQueryPort;
        this.assetQueryPort = assetQueryPort;
        this.iamTenantPort = iamTenantPort;
    }

    /**
     * 为员工创建分配记录（STAFF 操作）。
     *
     * <p>编排逻辑：
     * <ol>
     *   <li>校验合同状态允许分配</li>
     *   <li>校验员工属于合同对应企业</li>
     *   <li>容量校验：ASSIGNED + CHECKED_IN &lt; maxOccupancy</li>
     *   <li>防重：同一 contractRoom + tenantId 不能有两条 ASSIGNED 记录</li>
     *   <li>创建 ASSIGNED assignment</li>
     *   <li>IAM 激活员工小程序登录能力（分配时即创建/激活，无需等到入住）</li>
     * </ol>
     *
     * @return 新建的 assignment.id
     */
    @Transactional
    public Long assignTenantToRoom(Long contractId, Long contractRoomId,
                                    Long roomId, Long tenantId, Long operatorId) {
        // 1. 校验合同状态
        if (!contractQueryPort.isContractAllowingAssignment(contractId)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "CONTRACT_NOT_ALLOW_ASSIGNMENT",
                    "合同当前状态不允许分配入住资格 contractId=" + contractId);
        }

        // 2. 获取企业 ID，校验员工归属
        var contractInfo = contractQueryPort.getContract(contractId);
        if (!customerQueryPort.isEmployeeOfEnterprise(tenantId, contractInfo.enterpriseId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "EMPLOYEE_NOT_IN_ENTERPRISE",
                    "员工不属于该企业 tenantId=" + tenantId
                            + " enterpriseId=" + contractInfo.enterpriseId());
        }

        // 3. 容量校验
        int maxOccupancy = assetQueryPort.getMaxOccupancy(roomId);
        int assigned  = repo.countAssignedByRoom(roomId);
        int checkedIn = repo.countCheckedInByRoom(roomId);
        if (assigned + checkedIn >= maxOccupancy) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ROOM_CAPACITY_FULL",
                    "房间容量已满（maxOccupancy=" + maxOccupancy
                            + " assigned=" + assigned + " checkedIn=" + checkedIn + "）");
        }

        // 4. 防重：同 contractRoom + tenant 已有 ASSIGNED 记录
        repo.findActiveAssignment(contractRoomId, tenantId).ifPresent(existing -> {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ASSIGNMENT_ALREADY_EXISTS",
                    "该员工在此房间已有待消费的分配记录 assignmentId=" + existing.getId());
        });

        // 5. 创建 ASSIGNED 记录
        OffsetDateTime now = OffsetDateTime.now();
        Long assignmentId = repo.insertAssignment(contractId, contractRoomId,
                roomId, tenantId, operatorId, now);

        // 6. 激活员工 IAM 账号（幂等，已存在则复用）
        CustomerEmployeeInfo employee = customerQueryPort.getEmployee(tenantId);
        iamTenantPort.createOrEnableTenantUser(employee.mobile(), employee.name());

        return assignmentId;
    }

    /**
     * 取消分配（ASSIGNED → CANCELLED）。
     */
    @Transactional
    public void cancelAssignment(Long assignmentId, Long operatorId) {
        RoomAssignment assignment = repo.findAssignmentById(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "ASSIGNMENT_NOT_FOUND", "分配记录不存在：" + assignmentId));

        if (!"ASSIGNED".equals(assignment.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ASSIGNMENT_STATUS_INVALID",
                    "仅 ASSIGNED 状态可取消，当前：" + assignment.getStatus());
        }

        repo.updateAssignmentStatus(assignmentId, "CANCELLED",
                OffsetDateTime.now(), operatorId);
    }
}
