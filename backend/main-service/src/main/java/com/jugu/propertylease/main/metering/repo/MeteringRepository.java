package com.jugu.propertylease.main.metering.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.MeterDeviceBinding;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterPriceConfig;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterReading;
import com.jugu.propertylease.main.jooq.tables.pojos.RoomDailyCharge;
import com.jugu.propertylease.main.jooq.tables.pojos.SettlementAnchor;
import com.jugu.propertylease.main.jooq.tables.pojos.TenantApportionment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MeteringRepository {

    // ── MeterDeviceBinding ──────────────────────────────────────────────────

    Long insertBinding(Long roomId, Long deviceId, String meterType,
                       OffsetDateTime startAt, BigDecimal initialReading, Long createdBy);

    /** 关闭旧版本绑定（binding_end_at = now, is_active = 0）。*/
    void closeBinding(Long bindingId, OffsetDateTime endAt);

    /** 查找房间当前所有有效绑定（is_active=1）。*/
    List<MeterDeviceBinding> findActiveBindings(Long roomId);

    Optional<MeterDeviceBinding> findActiveBinding(Long roomId, String meterType);

    List<MeterDeviceBinding> findAllBindings(Long roomId);

    // ── MeterReading ────────────────────────────────────────────────────────

    Long insertReading(Long roomId, Long deviceBindingId, String meterType,
                       BigDecimal readingValue, OffsetDateTime readingTime,
                       String source, String anchorType, Long stayId,
                       String readingGroupId, String deviceIdRaw, Long operatorId);

    boolean existsByDeviceIdRawAndTime(String deviceIdRaw, OffsetDateTime readingTime);

    /** 查询指定日期之前最新的读数（日结起始值）。*/
    Optional<MeterReading> findLatestBefore(Long roomId, String meterType, OffsetDateTime before);

    /** 查询指定日期区间内最新的读数（日结截止值）。*/
    Optional<MeterReading> findLatestInRange(Long roomId, String meterType,
                                              OffsetDateTime from, OffsetDateTime to);

    /** 查询某 stay 入住期间所有读数（用于 UsageSummary 计算）。*/
    List<MeterReading> findByStayAndAnchorType(Long stayId, String anchorType);

    /** 分页查询（外部 API 用）。*/
    List<MeterReading> findAll(Long roomId, String meterType, String anchorType,
                                String source, OffsetDateTime startTime,
                                OffsetDateTime endTime, int offset, int limit);

    int countAll(Long roomId, String meterType, String anchorType,
                 String source, OffsetDateTime startTime, OffsetDateTime endTime);

    // ── MeterPriceConfig ────────────────────────────────────────────────────

    /** 查询有效单价：优先门店级别，fallback 全局默认（store_id IS NULL）。*/
    Optional<BigDecimal> findEffectivePrice(Long storeId, String meterType, LocalDate date);

    /** 关闭当前有效版本（effective_to = effectiveFrom - 1 天）。*/
    void closeCurrentPrice(Long storeId, String meterType, LocalDate effectiveTo);

    Long insertPrice(Long storeId, String meterType, BigDecimal unitPrice,
                     LocalDate effectiveFrom, Long createdBy);

    List<MeterPriceConfig> findPrices(Long storeId, String meterType);

    // ── SettlementAnchor ────────────────────────────────────────────────────

    Long insertAnchor(Long roomId, Long stayId, String anchorType,
                      String readingGroupId, OffsetDateTime anchorTime);

    // ── RoomDailyCharge ─────────────────────────────────────────────────────

    boolean existsCharge(Long roomId, LocalDate date);

    Long insertCharge(Long roomId, LocalDate date, BigDecimal waterAmount,
                      BigDecimal electricAmount, BigDecimal hotWaterAmount,
                      BigDecimal totalAmount, String status, OffsetDateTime now);

    void insertFailedCharge(Long roomId, LocalDate date, String error, OffsetDateTime now);

    void updateChargeStatus(Long id, String status, OffsetDateTime now);

    List<RoomDailyCharge> findCharges(Long roomId, String status,
                                       LocalDate startDate, LocalDate endDate,
                                       int offset, int limit);

    int countCharges(Long roomId, String status, LocalDate startDate, LocalDate endDate);

    Optional<RoomDailyCharge> findChargeById(Long id);

    // ── TenantApportionment ─────────────────────────────────────────────────

    void insertApportionment(Long roomDailyChargeId, Long roomId, Long tenantId,
                              Long stayId, LocalDate date, BigDecimal amount, OffsetDateTime now);

    List<TenantApportionment> findApportionmentsByChargeId(Long roomDailyChargeId);
}
