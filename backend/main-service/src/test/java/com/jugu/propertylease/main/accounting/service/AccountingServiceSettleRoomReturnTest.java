package com.jugu.propertylease.main.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.main.accounting.api.model.PartialReturnCommand;
import com.jugu.propertylease.main.accounting.outer.BillingServicePort;
import com.jugu.propertylease.main.accounting.repo.OwnerArrears;
import com.jugu.propertylease.main.accounting.repo.jooq.JooqAccountingRepository;
import com.jugu.propertylease.main.leasecontract.api.ContractCallbackPort;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccount;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomAccountSubBalance;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * settlePartialReturn → settleRoomReturn 的分支覆盖（P1-6 欠费追缴）。
 */
@ExtendWith(MockitoExtension.class)
class AccountingServiceSettleRoomReturnTest {

    @Mock
    private JooqAccountingRepository repo;
    @Mock
    private BillingServicePort billingServicePort;
    @Mock
    private ContractCallbackPort contractCallbackPort;
    @Mock
    private BillNoGenerator billNoGenerator;

    @InjectMocks
    private AccountingService service;

    private RoomAccount account(long id, long roomId) {
        RoomAccount a = new RoomAccount();
        a.setId(id);
        a.setRoomId(roomId);
        return a;
    }

    private RoomAccountSubBalance sub(long id, String ownerType, long ownerId, String available) {
        RoomAccountSubBalance s = new RoomAccountSubBalance();
        s.setId(id);
        s.setOwnerType(ownerType);
        s.setOwnerId(ownerId);
        s.setAvailableBalance(new BigDecimal(available));
        s.setFrozenBalance(BigDecimal.ZERO);
        return s;
    }

    @Test
    void settlePartialReturn_withArrears_shouldCreateSettlementBill() {
        // 房间账户：企业子余额 50 待退 + 租客 55 欠费 30
        when(repo.findByRoomId(10L)).thenReturn(Optional.of(account(100L, 10L)));
        when(repo.findAllByAccountId(100L))
                .thenReturn(List.of(sub(1L, "ENTERPRISE", 7L, "50.00")));
        when(billNoGenerator.generate(BillNoGenerator.Prefix.REFUND)).thenReturn("RFD-X");
        when(billNoGenerator.generate(BillNoGenerator.Prefix.SETTLEMENT)).thenReturn("STL-X");
        when(repo.sumInsufficientByOwner(100L))
                .thenReturn(List.of(new OwnerArrears("TENANT", 55L, new BigDecimal("30.00"))));

        service.settlePartialReturn(new PartialReturnCommand(9L, List.of(10L)));

        // 余额退还：REFUND_BILL + 余额清零 + REFUND 流水
        verify(repo).insert(eq("RFD-X"), eq("REFUND_BILL"), eq("ENTERPRISE"), eq(7L),
                eq(9L), eq(10L), isNull(), eq(new BigDecimal("50.00")), isNull(), any());
        verify(repo).updateBalance(eq(1L), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), any());
        verify(billingServicePort).createRefund(any());

        // 欠费追缴：SETTLEMENT_BILL（TENANT 回写 tenantId）+ 通知 billing-service
        verify(repo).insert(eq("STL-X"), eq("SETTLEMENT_BILL"), eq("TENANT"), eq(55L),
                eq(9L), eq(10L), eq(55L), eq(new BigDecimal("30.00")), isNull(), any());
        ArgumentCaptor<BillingServicePort.BillingCreateCommand> captor =
                ArgumentCaptor.forClass(BillingServicePort.BillingCreateCommand.class);
        verify(billingServicePort).createBill(captor.capture());
        assertThat(captor.getValue().billType()).isEqualTo("SETTLEMENT_BILL");
        assertThat(captor.getValue().amount()).isEqualByComparingTo("30.00");

        // 账户关闭
        verify(repo).updateStatus(eq(100L), eq("CLOSED"), any());
    }

    @Test
    void settlePartialReturn_noArrearsNoBalance_shouldOnlyCloseAccount() {
        when(repo.findByRoomId(10L)).thenReturn(Optional.of(account(100L, 10L)));
        when(repo.findAllByAccountId(100L))
                .thenReturn(List.of(sub(1L, "ENTERPRISE", 7L, "0.00")));
        when(repo.sumInsufficientByOwner(100L)).thenReturn(List.of());

        service.settlePartialReturn(new PartialReturnCommand(9L, List.of(10L)));

        verify(repo, never()).insert(any(), eq("REFUND_BILL"), any(), any(),
                any(), any(), any(), any(), any(), any());
        verify(repo, never()).insert(any(), eq("SETTLEMENT_BILL"), any(), any(),
                any(), any(), any(), any(), any(), any());
        verify(billingServicePort, never()).createBill(any());
        verify(billingServicePort, never()).createRefund(any());
        verify(repo).updateStatus(eq(100L), eq("CLOSED"), any());
    }

    @Test
    void settlePartialReturn_noAccount_shouldDoNothing() {
        when(repo.findByRoomId(10L)).thenReturn(Optional.empty());

        service.settlePartialReturn(new PartialReturnCommand(9L, List.of(10L)));

        verify(repo, never()).updateStatus(any(), any(), any());
        verify(billingServicePort, never()).createBill(any());
    }
}
