package com.jugu.propertylease.main.metering.delegate;

import com.jugu.propertylease.main.api.MeteringBindingsApiDelegate;
import com.jugu.propertylease.main.api.MeteringDailyChargesApiDelegate;
import com.jugu.propertylease.main.api.MeteringPricesApiDelegate;
import com.jugu.propertylease.main.api.MeteringReadingsApiDelegate;
import com.jugu.propertylease.main.api.model.*;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterDeviceBinding;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterPriceConfig;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterReading;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomDailyCharge;
import com.jugu.propertylease.main.jooq.tables.pojos.TenantApportionment;
import com.jugu.propertylease.main.metering.api.RecordReadingCommand;
import com.jugu.propertylease.main.metering.repo.MeteringRepository;
import com.jugu.propertylease.main.metering.service.MeteringService;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// ═════════════════════════════════════════════════════════════════════════════
// 设备绑定 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class MeteringBindingsApiDelegateImpl implements MeteringBindingsApiDelegate {

    private final MeteringService svc;
    private final MeteringRepository repo;

    MeteringBindingsApiDelegateImpl(MeteringService svc, MeteringRepository repo) {
        this.svc = svc;
        this.repo = repo;
    }

    @Override
    public MeterDeviceBindingListResult listRoomDeviceBindings(Long roomId, Boolean activeOnly) {
        List<MeterDeviceBinding> bindings = Boolean.TRUE.equals(activeOnly)
                ? repo.findActiveBindings(roomId)
                : repo.findAllBindings(roomId);
        return new MeterDeviceBindingListResult()
                .items(bindings.stream().map(this::toModel).toList());
    }

    @Override
    public com.jugu.propertylease.main.api.model.MeterDeviceBinding bindDevice(
            Long roomId, BindDeviceRequest req) {
        Long operatorId = CurrentUser.getCurrentUserId();
        svc.bindDevice(roomId,
                req.getDeviceId(),
                req.getMeterType().getValue(),
                BigDecimal.valueOf(req.getInitialReading()),
                operatorId);
        return repo.findActiveBinding(roomId, req.getMeterType().getValue())
                .map(this::toModel)
                .orElseThrow();
    }

    @Override
    public com.jugu.propertylease.main.api.model.MeterDeviceBinding unbindDevice(
            Long roomId, Long bindingId) {
        svc.unbindDevice(roomId, bindingId);
        return repo.findAllBindings(roomId).stream()
                .filter(b -> b.getId().equals(bindingId))
                .map(this::toModel).findFirst().orElseThrow();
    }

    private com.jugu.propertylease.main.api.model.MeterDeviceBinding toModel(MeterDeviceBinding b) {
        return new com.jugu.propertylease.main.api.model.MeterDeviceBinding()
                .id(b.getId())
                .roomId(b.getRoomId())
                .deviceId(b.getDeviceId())
                .meterType(com.jugu.propertylease.main.api.model.MeterDeviceBinding.MeterTypeEnum
                        .fromValue(b.getMeterType()))
                .bindingStartAt(b.getBindingStartAt())
                .bindingEndAt(b.getBindingEndAt())
                .initialReading(b.getInitialReading() != null
                        ? b.getInitialReading().doubleValue() : null)
                .isActive(b.getIsActive() != null && b.getIsActive() == 1);
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 读数 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class MeteringReadingsApiDelegateImpl implements MeteringReadingsApiDelegate {

    private final MeteringService svc;
    private final MeteringRepository repo;

    MeteringReadingsApiDelegateImpl(MeteringService svc, MeteringRepository repo) {
        this.svc = svc;
        this.repo = repo;
    }

    @Override
    public com.jugu.propertylease.main.api.model.MeterReading submitMeterReading(
            SubmitReadingRequest req) {
        Long operatorId = CurrentUser.getCurrentUserId();
        svc.submitPeriodicReading(new RecordReadingCommand(
                req.getRoomId(),
                req.getMeterType().getValue(),
                BigDecimal.valueOf(req.getReadingValue()),
                req.getReadingTime(),
                "MANUAL",
                null,
                operatorId
        ));
        // 返回刚写入的最新读数
        return repo.findLatestInRange(req.getRoomId(), req.getMeterType().getValue(),
                        req.getReadingTime().minusSeconds(1), req.getReadingTime().plusSeconds(1))
                .map(this::toModel).orElseThrow();
    }

    @Override
    public ReadingPageResult queryMeterReadings(ReadingQueryRequest req) {
        int page = req.getPageNo() != null ? req.getPageNo() : 1;
        int size = req.getPageSize() != null ? req.getPageSize() : 20;
        String meterType  = req.getMeterType()  != null ? req.getMeterType().getValue()  : null;
        String anchorType = req.getAnchorType() != null ? req.getAnchorType().getValue() : null;
        String source     = req.getSource()     != null ? req.getSource().getValue()     : null;

        List<MeterReading> readings = repo.findAll(req.getRoomId(), meterType,
                anchorType, source, req.getStartTime(), req.getEndTime(),
                (page - 1) * size, size);
        int total = repo.countAll(req.getRoomId(), meterType, anchorType,
                source, req.getStartTime(), req.getEndTime());

        return new ReadingPageResult()
                .items(readings.stream().map(this::toModel).toList())
                .total((long) total).pageNo(page).pageSize(size);
    }

    private com.jugu.propertylease.main.api.model.MeterReading toModel(MeterReading r) {
        return new com.jugu.propertylease.main.api.model.MeterReading()
                .id(r.getId())
                .roomId(r.getRoomId())
                .deviceBindingId(r.getDeviceBindingId())
                .meterType(com.jugu.propertylease.main.api.model.MeterReading.MeterTypeEnum
                        .fromValue(r.getMeterType()))
                .readingValue(r.getReadingValue().doubleValue())
                .readingTime(r.getReadingTime())
                .source(com.jugu.propertylease.main.api.model.MeterReading.SourceEnum
                        .fromValue(r.getSource()))
                .anchorType(r.getAnchorType() != null
                        ? com.jugu.propertylease.main.api.model.MeterReading.AnchorTypeEnum
                                .fromValue(r.getAnchorType()) : null)
                .stayId(r.getStayId())
                .readingGroupId(r.getReadingGroupId());
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 单价 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class MeteringPricesApiDelegateImpl implements MeteringPricesApiDelegate {

    private final MeteringService svc;
    private final MeteringRepository repo;

    MeteringPricesApiDelegateImpl(MeteringService svc, MeteringRepository repo) {
        this.svc = svc;
        this.repo = repo;
    }

    @Override
    public MeterPriceConfigListResult listMeterPrices(Long storeId, String meterType) {
        List<MeterPriceConfig> prices = repo.findPrices(storeId, meterType);
        return new MeterPriceConfigListResult()
                .items(prices.stream().map(this::toModel).toList());
    }

    @Override
    public com.jugu.propertylease.main.api.model.MeterPriceConfig createMeterPrice(
            CreateMeterPriceRequest req) {
        Long operatorId = CurrentUser.getCurrentUserId();
        svc.createMeterPrice(
                req.getStoreId(),
                req.getMeterType().getValue(),
                BigDecimal.valueOf(req.getUnitPrice()),
                req.getEffectiveFrom(),
                operatorId);
        return repo.findPrices(req.getStoreId(), req.getMeterType().getValue())
                .stream().filter(p -> p.getEffectiveTo() == null)
                .map(this::toModel).findFirst().orElseThrow();
    }

    private com.jugu.propertylease.main.api.model.MeterPriceConfig toModel(MeterPriceConfig p) {
        return new com.jugu.propertylease.main.api.model.MeterPriceConfig()
                .id(p.getId())
                .storeId(p.getStoreId())
                .meterType(com.jugu.propertylease.main.api.model.MeterPriceConfig.MeterTypeEnum
                        .fromValue(p.getMeterType()))
                .unitPrice(p.getUnitPrice().doubleValue())
                .effectiveFrom(p.getEffectiveFrom())
                .effectiveTo(p.getEffectiveTo());
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 日费用 Delegate
// ═════════════════════════════════════════════════════════════════════════════

@Service
class MeteringDailyChargesApiDelegateImpl implements MeteringDailyChargesApiDelegate {

    private final MeteringRepository repo;

    MeteringDailyChargesApiDelegateImpl(MeteringRepository repo) {
        this.repo = repo;
    }

    @Override
    public DailyChargePageResult queryRoomDailyCharges(DailyChargeQueryRequest req) {
        int page = req.getPageNo() != null ? req.getPageNo() : 1;
        int size = req.getPageSize() != null ? req.getPageSize() : 20;
        String status = req.getStatus() != null ? req.getStatus().getValue() : null;

        List<RoomDailyCharge> charges = repo.findCharges(req.getRoomId(), status,
                req.getStartDate(), req.getEndDate(), (page - 1) * size, size);
        int total = repo.countCharges(req.getRoomId(), status,
                req.getStartDate(), req.getEndDate());

        return new DailyChargePageResult()
                .items(charges.stream().map(this::toModel).toList())
                .total((long) total).pageNo(page).pageSize(size);
    }

    @Override
    public TenantApportionmentListResult getDailyChargeApportionments(Long id) {
        List<TenantApportionment> list = repo.findApportionmentsByChargeId(id);
        return new TenantApportionmentListResult()
                .items(list.stream().map(this::toAppModel).toList());
    }

    private com.jugu.propertylease.main.api.model.RoomDailyCharge toModel(RoomDailyCharge c) {
        return new com.jugu.propertylease.main.api.model.RoomDailyCharge()
                .id(c.getId())
                .roomId(c.getRoomId())
                .settlementDate(c.getSettlementDate())
                .waterAmount(c.getWaterAmount().doubleValue())
                .electricAmount(c.getElectricAmount().doubleValue())
                .hotWaterAmount(c.getHotWaterAmount().doubleValue())
                .totalAmount(c.getTotalAmount().doubleValue())
                .status(com.jugu.propertylease.main.api.model.RoomDailyCharge.StatusEnum
                        .fromValue(c.getStatus()));
    }

    private com.jugu.propertylease.main.api.model.TenantApportionment toAppModel(
            TenantApportionment a) {
        return new com.jugu.propertylease.main.api.model.TenantApportionment()
                .id(a.getId())
                .roomDailyChargeId(a.getRoomDailyChargeId())
                .roomId(a.getRoomId())
                .tenantId(a.getTenantId())
                .stayId(a.getStayId())
                .settlementDate(a.getSettlementDate())
                .apportionedAmount(a.getApportionedAmount().doubleValue());
    }
}
