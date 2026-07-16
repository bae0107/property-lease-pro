package com.jugu.propertylease.main.contract.delegate;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.ContractRoomsApiDelegate;
import com.jugu.propertylease.main.api.model.ContractRoomPageResult;
import com.jugu.propertylease.main.api.model.ContractRoomQueryRequest;
import com.jugu.propertylease.main.contract.repo.ContractRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ContractRoomsApiDelegateImpl implements ContractRoomsApiDelegate {

    private final ContractRepository repo;

    public ContractRoomsApiDelegateImpl(ContractRepository repo) {
        this.repo = repo;
    }

    @Override
    public com.jugu.propertylease.main.api.model.ContractRoom getContractRoom(Long id) {
        ContractRoom room = repo.findRoomById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "CONTRACT_ROOM_NOT_FOUND", "合同房间不存在：" + id));
        return toApiRoom(room);
    }

    @Override
    public ContractRoomPageResult queryContractRooms(ContractRoomQueryRequest request) {
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 20;
        String status = request.getStatus() != null ? request.getStatus().getValue() : null;

        // ContractRepository 目前只支持按 contractId(+status) 查全量列表，没有分页方法；
        // 单个合同下房间数量通常很小（个位数到几十），这里用内存分页而不是再加一个仓库方法。
        var all = repo.findRoomsByContract(request.getContractId(), status);
        int total = all.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        var items = all.subList(fromIndex, toIndex);

        return new ContractRoomPageResult()
                .items(items.stream().map(this::toApiRoom).toList())
                .total(total)
                .page(page)
                .size(size);
    }

    private com.jugu.propertylease.main.api.model.ContractRoom toApiRoom(ContractRoom r) {
        return new com.jugu.propertylease.main.api.model.ContractRoom()
                .id(r.getId())
                .contractId(r.getContractId())
                .roomId(r.getRoomId())
                .signedRent(r.getSignedRent())
                .leaseStart(r.getLeaseStart())
                .leaseEnd(r.getLeaseEnd())
                .status(com.jugu.propertylease.main.api.model.ContractRoomStatus
                        .fromValue(r.getStatus()))
                .returnedAt(r.getReturnedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt());
    }
}
