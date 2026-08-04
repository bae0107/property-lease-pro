package com.jugu.propertylease.main.metering.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.AccountingQueryPort;
import com.jugu.propertylease.main.accounting.api.model.DailyDeductionCommand;
import com.jugu.propertylease.main.accounting.api.model.DailyDeductionResult;
import com.jugu.propertylease.main.leasecontract.api.ContractQueryPort;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterDeviceBinding;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterReading;
import com.jugu.propertylease.main.metering.repo.MeteringRepository;
import com.jugu.propertylease.main.occupancy.api.OccupancyQueryPort;
import com.jugu.propertylease.main.assetmgr.api.AssetQueryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 换表/解绑期末结算（settleBindingFinal）分支覆盖。
 */
@ExtendWith(MockitoExtension.class)
class MeteringServiceReplaceSettleTest {

    @Mock
    private MeteringRepository repo;
    @Mock
    private OccupancyQueryPort occupancyQueryPort;
    @Mock
    private AssetQueryPort assetQueryPort;
    @Mock
    private AccountingCommandPort accountingCommandPort;
    @Mock
    private AccountingQueryPort accountingQueryPort;
    @Mock
    private ContractQueryPort contractQueryPort;

    @InjectMocks
    private MeteringService service;

    private MeterDeviceBinding binding(long id, long roomId, String meterType,
                                       BigDecimal initial, byte isActive) {
        MeterDeviceBinding b = new MeterDeviceBinding();
        b.setId(id);
        b.setRoomId(roomId);
        b.setMeterType(meterType);
        b.setInitialReading(initial);
        b.setIsActive(isActive);
        return b;
    }

    private MeterReading reading(BigDecimal value) {
        MeterReading r = new MeterReading();
        r.setReadingValue(value);
        r.setReadingTime(OffsetDateTime.now());
        return r;
    }

    @Test
    void bindDevice_firstBinding_rejectsOldFinalReading() {
        when(repo.findActiveBinding(1L, "WATER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bindDevice(1L, 99L, "WATER",
                new BigDecimal("0"), new BigDecimal("100"), 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("METER_FINAL_READING_INVALID"));

        verify(repo, never()).insertBinding(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bindDevice_replace_requiresOldFinalReading() {
        MeterDeviceBinding old = binding(1L, 1L, "WATER", new BigDecimal("100"), (byte) 1);
        when(repo.findActiveBinding(1L, "WATER")).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.bindDevice(1L, 99L, "WATER",
                new BigDecimal("10"), null, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("METER_FINAL_READING_INVALID"));

        verify(repo, never()).closeBinding(anyLong(), any());
        verify(repo, never()).insertBinding(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bindDevice_replace_finalReadingBelowLatest_rejected() {
        MeterDeviceBinding old = binding(1L, 1L, "WATER", new BigDecimal("100"), (byte) 1);
        when(repo.findActiveBinding(1L, "WATER")).thenReturn(Optional.of(old));
        when(repo.findLatestBefore(eq(1L), eq("WATER"), any()))
                .thenReturn(Optional.of(reading(new BigDecimal("120"))));

        assertThatThrownBy(() -> service.bindDevice(1L, 99L, "WATER",
                new BigDecimal("10"), new BigDecimal("110"), 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("METER_FINAL_READING_INVALID"));

        verify(repo, never()).insertCharge(any(), any(), any(), any(), any(), any(), any(), any());
        verify(repo, never()).closeBinding(anyLong(), any());
    }

    @Test
    void bindDevice_replace_afterDailySettlement_rejected409() {
        MeterDeviceBinding old = binding(1L, 1L, "WATER", new BigDecimal("100"), (byte) 1);
        when(repo.findActiveBinding(1L, "WATER")).thenReturn(Optional.of(old));
        when(repo.existsCharge(1L, LocalDate.now())).thenReturn(true);

        assertThatThrownBy(() -> service.bindDevice(1L, 99L, "WATER",
                new BigDecimal("10"), new BigDecimal("130"), 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("METER_REPLACE_AFTER_DAILY_SETTLEMENT"));

        verify(repo, never()).closeBinding(anyLong(), any());
    }

    @Test
    void bindDevice_replace_happyPath_settlesDeductsAnchorsAndRebinds() {
        MeterDeviceBinding old = binding(1L, 1L, "WATER", new BigDecimal("100"), (byte) 1);
        when(repo.findActiveBinding(1L, "WATER")).thenReturn(Optional.of(old));
        when(repo.existsCharge(1L, LocalDate.now())).thenReturn(false);
        // settleBindingFinal 校验与 settleRoomInternal 起始读数共用该查询：最新读数 100
        when(repo.findLatestBefore(eq(1L), eq("WATER"), any()))
                .thenReturn(Optional.of(reading(new BigDecimal("100"))));
        when(assetQueryPort.getStoreIdByRoomId(1L)).thenReturn(7L);
        when(repo.findEffectivePrice(eq(7L), eq("WATER"), any()))
                .thenReturn(Optional.of(new BigDecimal("2")));
        when(occupancyQueryPort.getCheckedInStaysByRoom(1L)).thenReturn(List.of());
        when(accountingQueryPort.getEnterpriseSubBalance(eq(1L), anyLong()))
                .thenReturn(BigDecimal.ZERO);
        when(accountingCommandPort.deductForDailySettlement(any()))
                .thenReturn(new DailyDeductionResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        // final=130，latest=100 → 用量 30 × 单价 2 = 60.00
        service.bindDevice(1L, 99L, "WATER", new BigDecimal("10"), new BigDecimal("130"), 7L);

        // 扣款命令：总额 60.00，企业承担 0，无租客分摊
        ArgumentCaptor<DailyDeductionCommand> cmdCaptor =
                ArgumentCaptor.forClass(DailyDeductionCommand.class);
        verify(accountingCommandPort).deductForDailySettlement(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().totalAmount()).isEqualByComparingTo("60.00");
        assertThat(cmdCaptor.getValue().enterpriseCoverAmount()).isEqualByComparingTo("0");
        assertThat(cmdCaptor.getValue().tenantApportionments()).isEmpty();

        // charge 落账：water 60.00，其余 0，SETTLED
        verify(repo).insertCharge(eq(1L), eq(LocalDate.now()),
                eq(new BigDecimal("60.00")), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO),
                eq(new BigDecimal("60.00")), eq("SETTLED"), any());

        // REPLACE_FINAL 锚点读数挂旧 binding
        verify(repo).insertReading(eq(1L), eq(1L), eq("WATER"), eq(new BigDecimal("130")),
                any(), eq("MANUAL"), eq("REPLACE_FINAL"), eq(null), eq(null), eq(null), eq(7L));

        // 关旧开新
        verify(repo).closeBinding(eq(1L), any());
        verify(repo).insertBinding(eq(1L), eq(99L), eq("WATER"), any(),
                eq(new BigDecimal("10")), eq(7L));
    }

    @Test
    void unbindDevice_zeroUsage_stillWritesSettledChargeAndAnchor() {
        MeterDeviceBinding b = binding(1L, 1L, "WATER", new BigDecimal("100"), (byte) 1);
        when(repo.findAllBindings(1L)).thenReturn(List.of(b));
        when(repo.findActiveBinding(1L, "WATER")).thenReturn(Optional.of(b));
        when(repo.existsCharge(1L, LocalDate.now())).thenReturn(false);
        when(repo.findLatestBefore(eq(1L), eq("WATER"), any()))
                .thenReturn(Optional.of(reading(new BigDecimal("100"))));
        when(assetQueryPort.getStoreIdByRoomId(1L)).thenReturn(7L);
        when(repo.findEffectivePrice(eq(7L), eq("WATER"), any()))
                .thenReturn(Optional.of(new BigDecimal("2")));

        // final = latest → 用量 0 → 照常写 SETTLED 零元记录，不触发扣款
        service.unbindDevice(1L, 1L, new BigDecimal("100"), 7L);

        verify(repo).insertCharge(eq(1L), eq(LocalDate.now()),
                eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO), eq("SETTLED"), any());
        verify(accountingCommandPort, never()).deductForDailySettlement(any());
        verify(repo).insertReading(eq(1L), eq(1L), eq("WATER"), eq(new BigDecimal("100")),
                any(), eq("MANUAL"), eq("REPLACE_FINAL"), eq(null), eq(null), eq(null), eq(7L));
        verify(repo).closeBinding(eq(1L), any());
    }

    @Test
    void unbindDevice_bindingNotFound_404() {
        when(repo.findAllBindings(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.unbindDevice(1L, 1L, new BigDecimal("100"), 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("METER_BINDING_NOT_FOUND"));
    }

    @Test
    void unbindDevice_alreadyClosed_400() {
        MeterDeviceBinding closed = binding(1L, 1L, "WATER", new BigDecimal("100"), (byte) 0);
        when(repo.findAllBindings(1L)).thenReturn(List.of(closed));

        assertThatThrownBy(() -> service.unbindDevice(1L, 1L, new BigDecimal("100"), 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("METER_BINDING_NOT_ACTIVE"));

        verify(repo, never()).closeBinding(anyLong(), any());
    }
}
