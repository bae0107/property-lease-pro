package com.jugu.propertylease.main.contract.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.model.PartialReturnCommand;
import com.jugu.propertylease.main.contract.repo.ContractRepository;
import com.jugu.propertylease.main.customer.api.CustomerQueryPort;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import com.jugu.propertylease.main.occupancy.api.OccupancyQueryPort;
import com.jugu.propertylease.main.propertymgr.api.AssetCommandPort;
import com.jugu.propertylease.main.propertymgr.api.AssetQueryPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * applyPartialReturn 状态机分支覆盖（P1-5）。
 */
@ExtendWith(MockitoExtension.class)
class ContractLifecycleServiceApplyPartialReturnTest {

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

    private Contract contract(long id, String status) {
        Contract c = new Contract();
        c.setId(id);
        c.setStatus(status);
        return c;
    }

    private ContractRoom room(long id, long contractId, long roomId, String status) {
        ContractRoom r = new ContractRoom();
        r.setId(id);
        r.setContractId(contractId);
        r.setRoomId(roomId);
        r.setStatus(status);
        return r;
    }

    @Test
    void applyPartialReturn_roomsRemain_shouldStayPartiallyReturned() {
        when(repo.findById(9L)).thenReturn(Optional.of(contract(9L, "READY_FOR_CHECK_IN")));
        when(repo.findRoomById(1L))
                .thenReturn(Optional.of(room(1L, 9L, 100L, "ACTIVE")));
        when(occupancyQueryPort.hasCheckedInStays(100L)).thenReturn(false);
        // 退完 room1 后仍有 room2 ACTIVE
        when(repo.findRoomsByContract(9L, "ACTIVE"))
                .thenReturn(List.of(room(2L, 9L, 101L, "ACTIVE")));

        service.applyPartialReturn(9L, List.of(1L), 42L);

        verify(accountingCommandPort).settlePartialReturn(
                new PartialReturnCommand(9L, List.of(100L)));
        verify(assetCommandPort).releaseRoom(100L, 9L);
        verify(repo).updateRoomStatus(eq(1L), eq("RETURNED"), any(), any());
        verify(repo).updateStatus(eq(9L), eq("PARTIALLY_RETURNED"), any());
        // 不触发整体结算
        verify(accountingCommandPort, never()).settleFullReturn(any());
        verify(repo, never()).updateStatus(eq(9L), eq("FULLY_RETURNED"), any());
    }

    @Test
    void applyPartialReturn_allRoomsReturned_shouldAutoSettleToCompleted() {
        // 从 PARTIALLY_RETURNED 继续退最后一间房
        when(repo.findById(9L)).thenReturn(Optional.of(contract(9L, "PARTIALLY_RETURNED")));
        when(repo.findRoomById(1L))
                .thenReturn(Optional.of(room(1L, 9L, 100L, "ACTIVE")));
        when(occupancyQueryPort.hasCheckedInStays(100L)).thenReturn(false);
        when(repo.findRoomsByContract(9L, "ACTIVE")).thenReturn(List.of());

        service.applyPartialReturn(9L, List.of(1L), 42L);

        verify(accountingCommandPort).settlePartialReturn(
                new PartialReturnCommand(9L, List.of(100L)));
        verify(accountingCommandPort).settleFullReturn(9L);

        InOrder inOrder = inOrder(repo);
        inOrder.verify(repo).updateStatus(eq(9L), eq("FULLY_RETURNED"), any());
        inOrder.verify(repo).updateStatus(eq(9L), eq("SETTLING"), any());
        inOrder.verify(repo).updateStatus(eq(9L), eq("COMPLETED"), any());
    }

    @Test
    void applyPartialReturn_invalidStatus_shouldReject() {
        when(repo.findById(9L)).thenReturn(Optional.of(contract(9L, "SETTLING")));

        assertThatThrownBy(() -> service.applyPartialReturn(9L, List.of(1L), 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SETTLING");

        verifyNoInteractions(accountingCommandPort, assetCommandPort);
    }

    @Test
    void applyPartialReturn_roomHasCheckedInTenant_shouldReject() {
        when(repo.findById(9L)).thenReturn(Optional.of(contract(9L, "READY_FOR_CHECK_IN")));
        when(repo.findRoomById(1L))
                .thenReturn(Optional.of(room(1L, 9L, 100L, "ACTIVE")));
        when(occupancyQueryPort.hasCheckedInStays(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.applyPartialReturn(9L, List.of(1L), 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("在住");

        verifyNoInteractions(accountingCommandPort, assetCommandPort);
    }
}
