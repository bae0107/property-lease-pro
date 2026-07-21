package com.jugu.propertylease.main.contract.delegate;

import com.jugu.propertylease.main.api.ContractContractsApiDelegate;
import com.jugu.propertylease.main.api.model.ContractDetail;
import com.jugu.propertylease.main.api.model.ContractPageResult;
import com.jugu.propertylease.main.api.model.ContractQueryRequest;
import com.jugu.propertylease.main.api.model.CreateContractRequest;
import com.jugu.propertylease.main.api.model.PartialReturnRequest;
import com.jugu.propertylease.main.contract.repo.ContractRepository;
import com.jugu.propertylease.main.contract.service.ContractLifecycleService;
import com.jugu.propertylease.main.contract.service.CreateChargeRuleCommand;
import com.jugu.propertylease.main.contract.service.CreateContractRoomCommand;
import com.jugu.propertylease.main.contract.service.CreateDraftCommand;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractChargeRule;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContractContractsApiDelegateImpl implements ContractContractsApiDelegate {

    private final ContractLifecycleService lifecycleService;
    private final ContractRepository repo;

    public ContractContractsApiDelegateImpl(ContractLifecycleService lifecycleService,
                                             ContractRepository repo) {
        this.lifecycleService = lifecycleService;
        this.repo = repo;
    }

    @Override
    public ContractDetail createContract(CreateContractRequest request) {
        Long operatorId = CurrentUser.getCurrentUserId();

        var rooms = request.getRooms().stream()
                .map(r -> new CreateContractRoomCommand(r.getRoomId(), r.getSignedRent(),
                        r.getLeaseStart(), r.getLeaseEnd()))
                .toList();

        var chargeRules = request.getChargeRules() == null ? List.<CreateChargeRuleCommand>of()
                : request.getChargeRules().stream()
                        .map(r -> new CreateChargeRuleCommand(r.getChargeType(), r.getPayerType(),
                                r.getAmount(), r.getRuleSnapshot()))
                        .toList();

        var result = lifecycleService.createDraft(new CreateDraftCommand(
                request.getEnterpriseId(), request.getStartDate(), request.getEndDate(),
                request.getPaymentMode(), request.getRemark(), rooms, chargeRules, operatorId));

        return toContractDetail(result.contract(), result.rooms(), result.chargeRules());
    }

    @Override
    public ContractPageResult queryContracts(ContractQueryRequest request) {
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 20;
        String status = request.getStatus() != null ? request.getStatus().getValue() : null;

        var items = repo.findByFilter(request.getEnterpriseId(), status,
                request.getStartDateFrom(), request.getEndDateTo(), (page - 1) * size, size);
        int total = repo.countByFilter(request.getEnterpriseId(), status,
                request.getStartDateFrom(), request.getEndDateTo());

        return new ContractPageResult()
                .items(items.stream().map(this::toApiContract).toList())
                .total((long) total)
                .pageNo(page)
                .pageSize(size);
    }

    @Override
    public ContractDetail getContract(Long id) {
        Contract contract = requireContract(id);
        List<ContractRoom> rooms = repo.findRoomsByContract(id, null);
        List<ContractChargeRule> rules = repo.findChargeRules(id);
        return toContractDetail(contract, rooms, rules);
    }

    @Override
    public ContractDetail confirmContract(Long id) {
        Long operatorId = CurrentUser.getCurrentUserId();
        var result = lifecycleService.confirmContract(id, operatorId);
        return toContractDetail(result.contract(), result.rooms(), result.chargeRules());
    }

    @Override
    public ContractDetail cancelContract(Long id) {
        Long operatorId = CurrentUser.getCurrentUserId();
        lifecycleService.cancel(id, operatorId);
        return getContract(id);
    }

    @Override
    public ContractDetail applyPartialReturn(Long id, PartialReturnRequest request) {
        Long operatorId = CurrentUser.getCurrentUserId();
        lifecycleService.applyPartialReturn(id, request.getContractRoomIds(), operatorId);
        return getContract(id);
    }

    @Override
    public ContractDetail applyFullReturn(Long id) {
        Long operatorId = CurrentUser.getCurrentUserId();
        lifecycleService.applyFullReturn(id, operatorId);
        return getContract(id);
    }

    private Contract requireContract(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new com.jugu.propertylease.common.exception.BusinessException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "CONTRACT_NOT_FOUND", "合同不存在：" + id));
    }

    private com.jugu.propertylease.main.api.model.Contract toApiContract(Contract c) {
        return new com.jugu.propertylease.main.api.model.Contract()
                .id(c.getId())
                .contractNo(c.getContractNo())
                .enterpriseId(c.getEnterpriseId())
                .status(com.jugu.propertylease.main.api.model.ContractStatus.fromValue(c.getStatus()))
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .paymentMode(c.getPaymentMode())
                .remark(c.getRemark())
                .signBillId(c.getSignBillId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt());
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

    private com.jugu.propertylease.main.api.model.ContractChargeRule toApiChargeRule(
            ContractChargeRule r) {
        return new com.jugu.propertylease.main.api.model.ContractChargeRule()
                .id(r.getId())
                .contractId(r.getContractId())
                .chargeType(r.getChargeType())
                .payerType(r.getPayerType())
                .amount(r.getAmount())
                .ruleSnapshot(r.getRuleSnapshot())
                .createdAt(r.getCreatedAt());
    }

    private ContractDetail toContractDetail(Contract c, List<ContractRoom> rooms,
                                             List<ContractChargeRule> rules) {
        return new ContractDetail()
                .id(c.getId())
                .contractNo(c.getContractNo())
                .enterpriseId(c.getEnterpriseId())
                .status(com.jugu.propertylease.main.api.model.ContractStatus.fromValue(c.getStatus()))
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .paymentMode(c.getPaymentMode())
                .remark(c.getRemark())
                .signBillId(c.getSignBillId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .rooms(rooms.stream().map(this::toApiRoom).toList())
                .chargeRules(rules.stream().map(this::toApiChargeRule).toList());
    }
}
