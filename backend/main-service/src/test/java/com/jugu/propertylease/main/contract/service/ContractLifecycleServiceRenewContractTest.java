package com.jugu.propertylease.main.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.leasecontract.repo.ContractRepository;
import com.jugu.propertylease.main.customer.api.CustomerQueryPort;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import com.jugu.propertylease.main.leasecontract.service.ContractDetail;
import com.jugu.propertylease.main.leasecontract.service.ContractLifecycleService;
import com.jugu.propertylease.main.leasecontract.service.ContractNoGenerator;
import com.jugu.propertylease.main.leasecontract.service.CreateContractRoomCommand;
import com.jugu.propertylease.main.leasecontract.service.RenewContractCommand;
import com.jugu.propertylease.main.occupancy.api.OccupancyQueryPort;
import com.jugu.propertylease.main.assetmgr.api.AssetCommandPort;
import com.jugu.propertylease.main.assetmgr.api.AssetQueryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * renewContract 续租分支覆盖（G2）。
 */
@ExtendWith(MockitoExtension.class)
class ContractLifecycleServiceRenewContractTest {

    @Mock
    private ContractRepository repo;
    @Mock
    private ContractNoGenerator contractNoGenerator;
    @Mock
    private CustomerQueryPort customerQueryPort;
    @Mock
    private AssetQueryPort assetQueryPort;
    @Mock
    private AssetCommandPort assetCommandPort;
    @Mock
    private AccountingCommandPort accountingCommandPort;
    @Mock
    private OccupancyQueryPort occupancyQueryPort;

    @InjectMocks
    private ContractLifecycleService service;

    private Contract sourceContract(long id, String status, long enterpriseId, LocalDate endDate) {
        Contract c = new Contract();
        c.setId(id);
        c.setStatus(status);
        c.setEnterpriseId(enterpriseId);
        c.setEndDate(endDate);
        return c;
    }

    private RenewContractCommand cmd(LocalDate startDate, long... roomIds) {
        List<CreateContractRoomCommand> rooms = java.util.Arrays.stream(roomIds)
                .mapToObj(r -> new CreateContractRoomCommand(r, new BigDecimal("1500"),
                        startDate, startDate.plusYears(1)))
                .toList();
        return new RenewContractCommand(startDate, startDate.plusYears(1),
                "MONTHLY", null, rooms, List.of(), 1L);
    }

    @Test
    void renew_sourceNotInRentStatus_shouldReject() {
        when(repo.findById(9L)).thenReturn(Optional.of(
                sourceContract(9L, "DRAFT", 5L, LocalDate.of(2026, 12, 31))));

        assertThatThrownBy(() -> service.renewContract(9L, cmd(LocalDate.of(2027, 1, 1), 100L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可续租");
    }

    @Test
    void renew_startDateBeforeSourceEnd_shouldReject() {
        when(repo.findById(9L)).thenReturn(Optional.of(
                sourceContract(9L, "READY_FOR_CHECK_IN", 5L, LocalDate.of(2026, 12, 31))));

        assertThatThrownBy(() -> service.renewContract(9L, cmd(LocalDate.of(2026, 12, 30), 100L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不得早于原合同结束日");
    }

    @Test
    void renew_sameRoomOccupiedBySource_shouldPass() {
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(repo.findById(9L)).thenReturn(Optional.of(
                sourceContract(9L, "READY_FOR_CHECK_IN", 5L, end)));
        // 房间 100 被源合同 ACTIVE 占用 → 豁免占用校验
        ContractRoom active = new ContractRoom();
        active.setStatus("ACTIVE");
        when(repo.findRoomByContractAndRoom(9L, 100L)).thenReturn(Optional.of(active));
        when(contractNoGenerator.generate()).thenReturn("CT-2027-001");
        when(repo.insertRenewalContract(anyString(), eq(5L), eq("DRAFT"), any(), any(),
                any(), any(), eq(9L), anyLong(), any())).thenReturn(10L);
        when(repo.findById(10L)).thenReturn(Optional.of(
                sourceContract(10L, "DRAFT", 5L, end.plusYears(1))));

        ContractDetail detail = service.renewContract(9L, cmd(LocalDate.of(2027, 1, 1), 100L));

        assertThat(detail.contract().getId()).isEqualTo(10L);
        // 未触发 room_info 可用性/他合同占用校验
        verify(assetQueryPort, never()).isRoomAvailableForContract(anyLong());
        verify(repo, never()).existsActiveContractRoom(anyLong());
        verify(repo).insertContractRoom(eq(10L), eq(100L), any(), any(), any(), any());
    }

    @Test
    void renew_roomOccupiedByOtherContract_shouldReject() {
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(repo.findById(9L)).thenReturn(Optional.of(
                sourceContract(9L, "READY_FOR_CHECK_IN", 5L, end)));
        when(repo.findRoomByContractAndRoom(9L, 200L)).thenReturn(Optional.empty());
        when(assetQueryPort.isRoomAvailableForContract(200L)).thenReturn(false);

        assertThatThrownBy(() -> service.renewContract(9L, cmd(LocalDate.of(2027, 1, 1), 200L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("房间不可用");
    }

    @Test
    void renew_success_shouldInheritEnterpriseAndRecordSource() {
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(repo.findById(9L)).thenReturn(Optional.of(
                sourceContract(9L, "PARTIALLY_RETURNED", 5L, end)));
        when(repo.findRoomByContractAndRoom(9L, 300L)).thenReturn(Optional.empty());
        when(assetQueryPort.isRoomAvailableForContract(300L)).thenReturn(true);
        when(repo.existsActiveContractRoom(300L)).thenReturn(false);
        when(contractNoGenerator.generate()).thenReturn("CT-2027-002");
        when(repo.insertRenewalContract(anyString(), anyLong(), anyString(), any(), any(),
                any(), any(), anyLong(), anyLong(), any())).thenReturn(11L);
        Contract persisted = sourceContract(11L, "DRAFT", 5L, end.plusYears(1));
        persisted.setRenewedFromContractId(9L);
        when(repo.findById(11L)).thenReturn(Optional.of(persisted));

        ContractDetail detail = service.renewContract(9L, cmd(LocalDate.of(2027, 1, 1), 300L));

        ArgumentCaptor<Long> enterpriseCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> sourceCaptor = ArgumentCaptor.forClass(Long.class);
        verify(repo).insertRenewalContract(eq("CT-2027-002"), enterpriseCaptor.capture(),
                eq("DRAFT"), eq(LocalDate.of(2027, 1, 1)), any(), eq("MONTHLY"), isNull(),
                sourceCaptor.capture(), eq(1L), any());
        assertThat(enterpriseCaptor.getValue()).isEqualTo(5L);   // 企业沿用原合同
        assertThat(sourceCaptor.getValue()).isEqualTo(9L);       // 记录续租来源
        assertThat(detail.contract().getRenewedFromContractId()).isEqualTo(9L);
    }
}
