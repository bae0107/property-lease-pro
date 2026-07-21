package com.jugu.propertylease.main.occupancy.delegate;

import com.jugu.propertylease.main.accounting.api.AccountingQueryPort;
import com.jugu.propertylease.main.accounting.api.model.DepositLedgerInfo;
import com.jugu.propertylease.main.api.OccupancyStaysApiDelegate;
import com.jugu.propertylease.main.api.model.CheckInRequest;
import com.jugu.propertylease.main.api.model.StayDetail;
import com.jugu.propertylease.main.api.model.StayPageResult;
import com.jugu.propertylease.main.api.model.StayQueryRequest;
import com.jugu.propertylease.main.api.model.TransferResult;
import com.jugu.propertylease.main.api.model.TransferTenantRequest;
import com.jugu.propertylease.main.jooq.tables.pojos.Stay;
import com.jugu.propertylease.main.occupancy.repo.OccupancyRepository;
import com.jugu.propertylease.main.occupancy.service.CheckInResult;
import com.jugu.propertylease.main.occupancy.service.CheckInService;
import com.jugu.propertylease.main.occupancy.service.CheckOutResult;
import com.jugu.propertylease.main.occupancy.service.CheckOutService;
import com.jugu.propertylease.main.occupancy.service.TransferOutcome;
import com.jugu.propertylease.main.occupancy.service.TransferService;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class OccupancyStaysApiDelegateImpl implements OccupancyStaysApiDelegate {

    private final CheckInService checkInService;
    private final CheckOutService checkOutService;
    private final TransferService transferService;
    private final OccupancyRepository repo;
    private final AccountingQueryPort accountingQueryPort;

    public OccupancyStaysApiDelegateImpl(CheckInService checkInService,
                                          CheckOutService checkOutService,
                                          TransferService transferService,
                                          OccupancyRepository repo,
                                          AccountingQueryPort accountingQueryPort) {
        this.checkInService = checkInService;
        this.checkOutService = checkOutService;
        this.transferService = transferService;
        this.repo = repo;
        this.accountingQueryPort = accountingQueryPort;
    }

    @Override
    public StayDetail checkIn(CheckInRequest request) {
        Long operatorId = CurrentUser.getCurrentUserId();
        CheckInResult result = checkInService.checkIn(request.getAssignmentId(), operatorId);
        return toStayDetail(result.stay(), result.personalDepositBillId(),
                result.personalDepositStatus());
    }

    @Override
    public StayPageResult queryStays(StayQueryRequest request) {
        int page = request.getPageNo() != null ? request.getPageNo() : 1;
        int size = request.getPageSize() != null ? request.getPageSize() : 20;
        String stayStatus = request.getStayStatus() != null
                ? request.getStayStatus().getValue() : null;

        var items = repo.findStays(request.getContractId(), request.getRoomId(),
                request.getTenantId(), stayStatus, (page - 1) * size, size);
        int total = repo.countStays(request.getContractId(), request.getRoomId(),
                request.getTenantId(), stayStatus);

        return new StayPageResult()
                .items(items.stream().map(this::toApiStay).toList())
                .total((long) total)
                .pageNo(page)
                .pageSize(size);
    }

    @Override
    public StayDetail getStay(Long id) {
        Stay stay = repo.findStayById(id).orElseThrow();
        DepositLedgerInfo deposit = findDepositLedgerSafely(stay);
        return toStayDetail(stay,
                deposit != null ? deposit.id() : null,
                deposit != null ? deposit.status() : null);
    }

    @Override
    public StayDetail checkOut(Long id) {
        Long operatorId = CurrentUser.getCurrentUserId();
        CheckOutResult result = checkOutService.checkOut(id, operatorId);
        DepositLedgerInfo deposit = findDepositLedgerSafely(result.stay());
        return toStayDetail(result.stay(),
                result.personalDepositBillId() != null
                        ? result.personalDepositBillId()
                        : (deposit != null ? deposit.id() : null),
                deposit != null ? deposit.status() : null);
    }

    @Override
    public TransferResult transferTenant(Long id, TransferTenantRequest request) {
        Long operatorId = CurrentUser.getCurrentUserId();
        TransferOutcome outcome = transferService.transferTenant(
                id, request.getToContractRoomId(), operatorId);
        return new TransferResult()
                .fromStay(toApiStay(outcome.fromStay()))
                .toStay(toApiStay(outcome.toStay()))
                .transferRecordId(outcome.transferRecordId());
    }

    /**
     * 查询个人押金台账状态；未开通押金（理论上不会发生，防御性处理）时返回 null，不阻断详情查询。
     */
    private DepositLedgerInfo findDepositLedgerSafely(Stay stay) {
        try {
            return accountingQueryPort.getPersonalDepositLedger(stay.getTenantId(), stay.getId());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private com.jugu.propertylease.main.api.model.Stay toApiStay(Stay stay) {
        return new com.jugu.propertylease.main.api.model.Stay()
                .id(stay.getId())
                .sourceAssignmentId(stay.getSourceAssignmentId())
                .contractId(stay.getContractId())
                .contractRoomId(stay.getContractRoomId())
                .roomId(stay.getRoomId())
                .tenantId(stay.getTenantId())
                .stayStatus(com.jugu.propertylease.main.api.model.Stay.StayStatusEnum
                        .fromValue(stay.getStayStatus()))
                .checkInAt(stay.getCheckInAt())
                .checkOutAt(stay.getCheckOutAt());
    }

    private StayDetail toStayDetail(Stay stay, Long personalDepositBillId,
                                     String personalDepositStatus) {
        return new StayDetail()
                .id(stay.getId())
                .sourceAssignmentId(stay.getSourceAssignmentId())
                .contractId(stay.getContractId())
                .contractRoomId(stay.getContractRoomId())
                .roomId(stay.getRoomId())
                .tenantId(stay.getTenantId())
                .stayStatus(StayDetail.StayStatusEnum
                        .fromValue(stay.getStayStatus()))
                .checkInAt(stay.getCheckInAt())
                .checkOutAt(stay.getCheckOutAt())
                .personalDepositBillId(personalDepositBillId)
                .personalDepositStatus(personalDepositStatus != null
                        ? StayDetail.PersonalDepositStatusEnum.fromValue(personalDepositStatus)
                        : null);
    }
}
