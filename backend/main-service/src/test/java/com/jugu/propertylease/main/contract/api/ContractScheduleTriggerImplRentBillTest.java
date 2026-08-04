package com.jugu.propertylease.main.contract.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.model.CreateBillResult;
import com.jugu.propertylease.main.accounting.api.model.RentBillCommand;
import com.jugu.propertylease.main.leasecontract.api.ContractScheduleTriggerImpl;
import com.jugu.propertylease.main.leasecontract.repo.ContractRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * checkAndGenerateRentBills 分支覆盖（P2-1）。
 */
@ExtendWith(MockitoExtension.class)
class ContractScheduleTriggerImplRentBillTest {

    @Mock
    private ContractRepository repo;
    @Mock
    private AccountingCommandPort accountingCommandPort;

    @InjectMocks
    private ContractScheduleTriggerImpl trigger;

    private Contract contract(long id, String paymentMode, String startDate) {
        Contract c = new Contract();
        c.setId(id);
        c.setEnterpriseId(7L);
        c.setStatus("READY_FOR_CHECK_IN");
        c.setPaymentMode(paymentMode);
        c.setStartDate(LocalDate.parse(startDate));
        return c;
    }

    private ContractRoom room(long id, String signedRent) {
        ContractRoom r = new ContractRoom();
        r.setId(id);
        r.setStatus("ACTIVE");
        r.setSignedRent(new BigDecimal(signedRent));
        return r;
    }

    @Test
    void notFirstDayOfMonth_shouldDoNothing() {
        trigger.checkAndGenerateRentBills(LocalDate.parse("2026-07-15"));

        verifyNoInteractions(repo, accountingCommandPort);
    }

    @Test
    void monthlyContract_shouldBillSumOfActiveRoomRent() {
        when(repo.findByStatuses(any())).thenReturn(List.of(contract(9L, "MONTHLY", "2026-01-15")));
        when(repo.findRoomsByContract(9L, "ACTIVE"))
                .thenReturn(List.of(room(1L, "1000.00"), room(2L, "1500.00")));
        when(accountingCommandPort.createRentBill(any()))
                .thenReturn(new CreateBillResult(100L, 900L, "http://pay"));

        trigger.checkAndGenerateRentBills(LocalDate.parse("2026-07-01"));

        ArgumentCaptor<RentBillCommand> captor = ArgumentCaptor.forClass(RentBillCommand.class);
        verify(accountingCommandPort).createRentBill(captor.capture());
        assertThat(captor.getValue().contractId()).isEqualTo(9L);
        assertThat(captor.getValue().enterpriseId()).isEqualTo(7L);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("2500.00");
        assertThat(captor.getValue().period()).isEqualTo("2026-07");
    }

    @Test
    void quarterlyContract_quarterMonth_shouldBillThreeMonths() {
        when(repo.findByStatuses(any())).thenReturn(List.of(contract(9L, "QUARTERLY", "2026-01-15")));
        when(repo.findRoomsByContract(9L, "ACTIVE"))
                .thenReturn(List.of(room(1L, "1000.00")));
        when(accountingCommandPort.createRentBill(any()))
                .thenReturn(new CreateBillResult(100L, 900L, "http://pay"));

        // 2026-01 起租，2026-04 相隔 3 个月 → 出账
        trigger.checkAndGenerateRentBills(LocalDate.parse("2026-04-01"));

        ArgumentCaptor<RentBillCommand> captor = ArgumentCaptor.forClass(RentBillCommand.class);
        verify(accountingCommandPort).createRentBill(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("3000.00");
    }

    @Test
    void quarterlyContract_nonQuarterMonth_shouldSkip() {
        when(repo.findByStatuses(any())).thenReturn(List.of(contract(9L, "QUARTERLY", "2026-01-15")));

        // 相隔 4 个月，非季度月 → 不出账
        trigger.checkAndGenerateRentBills(LocalDate.parse("2026-05-01"));

        verify(accountingCommandPort, never()).createRentBill(any());
    }

    @Test
    void zeroRent_shouldSkip() {
        when(repo.findByStatuses(any())).thenReturn(List.of(contract(9L, "MONTHLY", "2026-01-15")));
        when(repo.findRoomsByContract(9L, "ACTIVE")).thenReturn(List.of());

        trigger.checkAndGenerateRentBills(LocalDate.parse("2026-07-01"));

        verify(accountingCommandPort, never()).createRentBill(any());
    }

    @Test
    void duplicateBill_returnsNull_shouldLogSkipWithoutError() {
        when(repo.findByStatuses(any())).thenReturn(List.of(contract(9L, "MONTHLY", "2026-01-15")));
        when(repo.findRoomsByContract(9L, "ACTIVE"))
                .thenReturn(List.of(room(1L, "1000.00")));
        when(accountingCommandPort.createRentBill(any())).thenReturn(null); // 当月已出账

        trigger.checkAndGenerateRentBills(LocalDate.parse("2026-07-01"));

        verify(accountingCommandPort).createRentBill(any()); // 调用但未重复生成（accounting 侧幂等）
    }
}
