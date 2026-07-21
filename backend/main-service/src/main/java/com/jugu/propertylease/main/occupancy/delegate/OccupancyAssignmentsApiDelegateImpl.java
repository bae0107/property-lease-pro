package com.jugu.propertylease.main.occupancy.delegate;

import com.jugu.propertylease.main.api.OccupancyAssignmentsApiDelegate;
import com.jugu.propertylease.main.api.model.AssignTenantRequest;
import com.jugu.propertylease.main.api.model.AssignmentPageResult;
import com.jugu.propertylease.main.api.model.AssignmentQueryRequest;
import com.jugu.propertylease.main.contract.api.ContractQueryPort;
import com.jugu.propertylease.main.contract.api.ContractRoomInfo;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAssignment;
import com.jugu.propertylease.main.occupancy.repo.OccupancyRepository;
import com.jugu.propertylease.main.occupancy.service.AssignmentService;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class OccupancyAssignmentsApiDelegateImpl implements OccupancyAssignmentsApiDelegate {

    private final AssignmentService assignmentService;
    private final OccupancyRepository repo;
    private final ContractQueryPort contractQueryPort;

    public OccupancyAssignmentsApiDelegateImpl(AssignmentService assignmentService,
                                                OccupancyRepository repo,
                                                ContractQueryPort contractQueryPort) {
        this.assignmentService = assignmentService;
        this.repo = repo;
        this.contractQueryPort = contractQueryPort;
    }

    @Override
    public com.jugu.propertylease.main.api.model.RoomAssignment assignTenantToRoom(
            AssignTenantRequest request) {
        Long operatorId = CurrentUser.getCurrentUserId();

        // 请求体只携带 contractRoomId + tenantId，contractId/roomId 由 contractRoomId 反查
        ContractRoomInfo contractRoom =
                contractQueryPort.getContractRoomById(request.getContractRoomId());

        Long assignmentId = assignmentService.assignTenantToRoom(
                contractRoom.contractId(), request.getContractRoomId(),
                contractRoom.roomId(), request.getTenantId(), operatorId);
        return toApiModel(repo.findAssignmentById(assignmentId).orElseThrow());
    }

    @Override
    public AssignmentPageResult queryAssignments(AssignmentQueryRequest request) {
        int page = request.getPageNo() != null ? request.getPageNo() : 1;
        int size = request.getPageSize() != null ? request.getPageSize() : 20;
        String status = request.getStatus() != null ? request.getStatus().getValue() : null;

        var items = repo.findAssignments(request.getContractId(), request.getRoomId(),
                request.getTenantId(), status, (page - 1) * size, size);
        int total = repo.countAssignments(request.getContractId(), request.getRoomId(),
                request.getTenantId(), status);

        return new AssignmentPageResult()
                .items(items.stream().map(this::toApiModel).toList())
                .total((long) total)
                .pageNo(page)
                .pageSize(size);
    }

    @Override
    public com.jugu.propertylease.main.api.model.RoomAssignment cancelAssignment(Long id) {
        Long operatorId = CurrentUser.getCurrentUserId();
        assignmentService.cancelAssignment(id, operatorId);
        return toApiModel(repo.findAssignmentById(id).orElseThrow());
    }

    private com.jugu.propertylease.main.api.model.RoomAssignment toApiModel(RoomAssignment a) {
        return new com.jugu.propertylease.main.api.model.RoomAssignment()
                .id(a.getId())
                .contractId(a.getContractId())
                .contractRoomId(a.getContractRoomId())
                .roomId(a.getRoomId())
                .tenantId(a.getTenantId())
                .status(com.jugu.propertylease.main.api.model.RoomAssignment.StatusEnum
                        .fromValue(a.getStatus()))
                .assignedAt(a.getAssignedAt())
                .assignedBy(a.getAssignedBy());
    }
}
