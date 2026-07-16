package com.jugu.propertylease.main.metering.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.*;
import static org.jooq.impl.DSL.trueCondition;

import com.jugu.propertylease.main.jooq.tables.pojos.*;
import com.jugu.propertylease.main.metering.repo.MeteringRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JooqMeteringRepository implements MeteringRepository {

    private final DSLContext dsl;

    public JooqMeteringRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ── MeterDeviceBinding ──────────────────────────────────────────────────

    @Override
    public Long insertBinding(Long roomId, Long deviceId, String meterType,
                              OffsetDateTime startAt, BigDecimal initialReading, Long createdBy) {
        return dsl.insertInto(METER_DEVICE_BINDING)
                .set(METER_DEVICE_BINDING.ROOM_ID, roomId)
                .set(METER_DEVICE_BINDING.DEVICE_ID, deviceId)
                .set(METER_DEVICE_BINDING.METER_TYPE, meterType)
                .set(METER_DEVICE_BINDING.BINDING_START_AT, startAt)
                .set(METER_DEVICE_BINDING.INITIAL_READING, initialReading)
                .set(METER_DEVICE_BINDING.IS_ACTIVE, (byte) 1)
                .set(METER_DEVICE_BINDING.CREATED_BY, createdBy)
                .set(METER_DEVICE_BINDING.CREATED_AT, startAt)
                .returning(METER_DEVICE_BINDING.ID)
                .fetchOne(METER_DEVICE_BINDING.ID);
    }

    @Override
    public void closeBinding(Long bindingId, OffsetDateTime endAt) {
        dsl.update(METER_DEVICE_BINDING)
                .set(METER_DEVICE_BINDING.BINDING_END_AT, endAt)
                .set(METER_DEVICE_BINDING.IS_ACTIVE, (byte) 0)
                .where(METER_DEVICE_BINDING.ID.eq(bindingId))
                .execute();
    }

    @Override
    public List<MeterDeviceBinding> findActiveBindings(Long roomId) {
        return dsl.selectFrom(METER_DEVICE_BINDING)
                .where(METER_DEVICE_BINDING.ROOM_ID.eq(roomId))
                .and(METER_DEVICE_BINDING.IS_ACTIVE.eq((byte) 1))
                .fetchInto(MeterDeviceBinding.class);
    }

    @Override
    public Optional<MeterDeviceBinding> findActiveBinding(Long roomId, String meterType) {
        return Optional.ofNullable(
                dsl.selectFrom(METER_DEVICE_BINDING)
                        .where(METER_DEVICE_BINDING.ROOM_ID.eq(roomId))
                        .and(METER_DEVICE_BINDING.METER_TYPE.eq(meterType))
                        .and(METER_DEVICE_BINDING.IS_ACTIVE.eq((byte) 1))
                        .fetchOneInto(MeterDeviceBinding.class));
    }

    @Override
    public List<MeterDeviceBinding> findAllBindings(Long roomId) {
        return dsl.selectFrom(METER_DEVICE_BINDING)
                .where(METER_DEVICE_BINDING.ROOM_ID.eq(roomId))
                .orderBy(METER_DEVICE_BINDING.BINDING_START_AT.desc())
                .fetchInto(MeterDeviceBinding.class);
    }

    // ── MeterReading ────────────────────────────────────────────────────────

    @Override
    public Long insertReading(Long roomId, Long deviceBindingId, String meterType,
                              BigDecimal readingValue, OffsetDateTime readingTime,
                              String source, String anchorType, Long stayId,
                              String readingGroupId, String deviceIdRaw, Long operatorId) {
        return dsl.insertInto(METER_READING)
                .set(METER_READING.ROOM_ID, roomId)
                .set(METER_READING.DEVICE_BINDING_ID, deviceBindingId)
                .set(METER_READING.METER_TYPE, meterType)
                .set(METER_READING.READING_VALUE, readingValue)
                .set(METER_READING.READING_TIME, readingTime)
                .set(METER_READING.SOURCE, source)
                .set(METER_READING.ANCHOR_TYPE, anchorType)
                .set(METER_READING.STAY_ID, stayId)
                .set(METER_READING.READING_GROUP_ID, readingGroupId)
                .set(METER_READING.DEVICE_ID_RAW, deviceIdRaw)
                .set(METER_READING.OPERATOR_ID, operatorId)
                .set(METER_READING.CREATED_AT, OffsetDateTime.now())
                .returning(METER_READING.ID)
                .fetchOne(METER_READING.ID);
    }

    @Override
    public boolean existsByDeviceIdRawAndTime(String deviceIdRaw, OffsetDateTime readingTime) {
        return dsl.fetchExists(
                dsl.selectOne().from(METER_READING)
                        .where(METER_READING.DEVICE_ID_RAW.eq(deviceIdRaw))
                        .and(METER_READING.READING_TIME.eq(readingTime)));
    }

    @Override
    public Optional<MeterReading> findLatestBefore(Long roomId, String meterType,
                                                    OffsetDateTime before) {
        return Optional.ofNullable(
                dsl.selectFrom(METER_READING)
                        .where(METER_READING.ROOM_ID.eq(roomId))
                        .and(METER_READING.METER_TYPE.eq(meterType))
                        .and(METER_READING.READING_TIME.lt(before))
                        .orderBy(METER_READING.READING_TIME.desc())
                        .limit(1)
                        .fetchOneInto(MeterReading.class));
    }

    @Override
    public Optional<MeterReading> findLatestInRange(Long roomId, String meterType,
                                                     OffsetDateTime from, OffsetDateTime to) {
        return Optional.ofNullable(
                dsl.selectFrom(METER_READING)
                        .where(METER_READING.ROOM_ID.eq(roomId))
                        .and(METER_READING.METER_TYPE.eq(meterType))
                        .and(METER_READING.READING_TIME.ge(from))
                        .and(METER_READING.READING_TIME.lt(to))
                        .orderBy(METER_READING.READING_TIME.desc())
                        .limit(1)
                        .fetchOneInto(MeterReading.class));
    }

    @Override
    public List<MeterReading> findByStayAndAnchorType(Long stayId, String anchorType) {
        return dsl.selectFrom(METER_READING)
                .where(METER_READING.STAY_ID.eq(stayId))
                .and(METER_READING.ANCHOR_TYPE.eq(anchorType))
                .fetchInto(MeterReading.class);
    }

    @Override
    public List<MeterReading> findAll(Long roomId, String meterType, String anchorType,
                                       String source, OffsetDateTime startTime,
                                       OffsetDateTime endTime, int offset, int limit) {
        Condition c = trueCondition();
        if (roomId     != null) c = c.and(METER_READING.ROOM_ID.eq(roomId));
        if (meterType  != null) c = c.and(METER_READING.METER_TYPE.eq(meterType));
        if (anchorType != null) c = c.and(METER_READING.ANCHOR_TYPE.eq(anchorType));
        if (source     != null) c = c.and(METER_READING.SOURCE.eq(source));
        if (startTime  != null) c = c.and(METER_READING.READING_TIME.ge(startTime));
        if (endTime    != null) c = c.and(METER_READING.READING_TIME.lt(endTime));
        return dsl.selectFrom(METER_READING).where(c)
                .orderBy(METER_READING.READING_TIME.desc())
                .limit(limit).offset(offset)
                .fetchInto(MeterReading.class);
    }

    @Override
    public int countAll(Long roomId, String meterType, String anchorType,
                        String source, OffsetDateTime startTime, OffsetDateTime endTime) {
        Condition c = trueCondition();
        if (roomId     != null) c = c.and(METER_READING.ROOM_ID.eq(roomId));
        if (meterType  != null) c = c.and(METER_READING.METER_TYPE.eq(meterType));
        if (anchorType != null) c = c.and(METER_READING.ANCHOR_TYPE.eq(anchorType));
        if (source     != null) c = c.and(METER_READING.SOURCE.eq(source));
        if (startTime  != null) c = c.and(METER_READING.READING_TIME.ge(startTime));
        if (endTime    != null) c = c.and(METER_READING.READING_TIME.lt(endTime));
        return dsl.fetchCount(METER_READING, c);
    }

    // ── MeterPriceConfig ────────────────────────────────────────────────────

    @Override
    public Optional<BigDecimal> findEffectivePrice(Long storeId, String meterType, LocalDate date) {
        // 优先查门店级别
        Optional<BigDecimal> storePrice = findPriceByScope(storeId, meterType, date);
        if (storePrice.isPresent()) return storePrice;
        // fallback 全局默认
        return findPriceByScope(null, meterType, date);
    }

    private Optional<BigDecimal> findPriceByScope(Long storeId, String meterType, LocalDate date) {
        Condition scopeCond = storeId != null
                ? METER_PRICE_CONFIG.STORE_ID.eq(storeId)
                : METER_PRICE_CONFIG.STORE_ID.isNull();
        return Optional.ofNullable(
                dsl.select(METER_PRICE_CONFIG.UNIT_PRICE)
                        .from(METER_PRICE_CONFIG)
                        .where(scopeCond)
                        .and(METER_PRICE_CONFIG.METER_TYPE.eq(meterType))
                        .and(METER_PRICE_CONFIG.EFFECTIVE_FROM.le(date))
                        .and(METER_PRICE_CONFIG.EFFECTIVE_TO.isNull()
                                .or(METER_PRICE_CONFIG.EFFECTIVE_TO.ge(date)))
                        .orderBy(METER_PRICE_CONFIG.EFFECTIVE_FROM.desc())
                        .limit(1)
                        .fetchOneInto(BigDecimal.class));
    }

    @Override
    public void closeCurrentPrice(Long storeId, String meterType, LocalDate effectiveTo) {
        Condition scopeCond = storeId != null
                ? METER_PRICE_CONFIG.STORE_ID.eq(storeId)
                : METER_PRICE_CONFIG.STORE_ID.isNull();
        dsl.update(METER_PRICE_CONFIG)
                .set(METER_PRICE_CONFIG.EFFECTIVE_TO, effectiveTo)
                .where(scopeCond)
                .and(METER_PRICE_CONFIG.METER_TYPE.eq(meterType))
                .and(METER_PRICE_CONFIG.EFFECTIVE_TO.isNull())
                .execute();
    }

    @Override
    public Long insertPrice(Long storeId, String meterType, BigDecimal unitPrice,
                            LocalDate effectiveFrom, Long createdBy) {
        return dsl.insertInto(METER_PRICE_CONFIG)
                .set(METER_PRICE_CONFIG.STORE_ID, storeId)
                .set(METER_PRICE_CONFIG.METER_TYPE, meterType)
                .set(METER_PRICE_CONFIG.UNIT_PRICE, unitPrice)
                .set(METER_PRICE_CONFIG.EFFECTIVE_FROM, effectiveFrom)
                .set(METER_PRICE_CONFIG.CREATED_BY, createdBy)
                .set(METER_PRICE_CONFIG.CREATED_AT, OffsetDateTime.now())
                .returning(METER_PRICE_CONFIG.ID)
                .fetchOne(METER_PRICE_CONFIG.ID);
    }

    @Override
    public List<MeterPriceConfig> findPrices(Long storeId, String meterType) {
        Condition c = trueCondition();
        if (storeId   != null) c = c.and(METER_PRICE_CONFIG.STORE_ID.eq(storeId));
        if (meterType != null) c = c.and(METER_PRICE_CONFIG.METER_TYPE.eq(meterType));
        return dsl.selectFrom(METER_PRICE_CONFIG).where(c)
                .orderBy(METER_PRICE_CONFIG.EFFECTIVE_FROM.desc())
                .fetchInto(MeterPriceConfig.class);
    }

    // ── SettlementAnchor ────────────────────────────────────────────────────

    @Override
    public Long insertAnchor(Long roomId, Long stayId, String anchorType,
                             String readingGroupId, OffsetDateTime anchorTime) {
        return dsl.insertInto(SETTLEMENT_ANCHOR)
                .set(SETTLEMENT_ANCHOR.ROOM_ID, roomId)
                .set(SETTLEMENT_ANCHOR.STAY_ID, stayId)
                .set(SETTLEMENT_ANCHOR.ANCHOR_TYPE, anchorType)
                .set(SETTLEMENT_ANCHOR.READING_GROUP_ID, readingGroupId)
                .set(SETTLEMENT_ANCHOR.ANCHOR_TIME, anchorTime)
                .set(SETTLEMENT_ANCHOR.CREATED_AT, OffsetDateTime.now())
                .returning(SETTLEMENT_ANCHOR.ID)
                .fetchOne(SETTLEMENT_ANCHOR.ID);
    }

    // ── RoomDailyCharge ─────────────────────────────────────────────────────

    @Override
    public boolean existsCharge(Long roomId, LocalDate date) {
        return dsl.fetchExists(dsl.selectOne().from(ROOM_DAILY_CHARGE)
                .where(ROOM_DAILY_CHARGE.ROOM_ID.eq(roomId))
                .and(ROOM_DAILY_CHARGE.SETTLEMENT_DATE.eq(date)));
    }

    @Override
    public Long insertCharge(Long roomId, LocalDate date, BigDecimal waterAmount,
                             BigDecimal electricAmount, BigDecimal hotWaterAmount,
                             BigDecimal totalAmount, String status, OffsetDateTime now) {
        return dsl.insertInto(ROOM_DAILY_CHARGE)
                .set(ROOM_DAILY_CHARGE.ROOM_ID, roomId)
                .set(ROOM_DAILY_CHARGE.SETTLEMENT_DATE, date)
                .set(ROOM_DAILY_CHARGE.WATER_AMOUNT, waterAmount)
                .set(ROOM_DAILY_CHARGE.ELECTRIC_AMOUNT, electricAmount)
                .set(ROOM_DAILY_CHARGE.HOT_WATER_AMOUNT, hotWaterAmount)
                .set(ROOM_DAILY_CHARGE.TOTAL_AMOUNT, totalAmount)
                .set(ROOM_DAILY_CHARGE.STATUS, status)
                .set(ROOM_DAILY_CHARGE.CREATED_AT, now)
                .set(ROOM_DAILY_CHARGE.UPDATED_AT, now)
                .returning(ROOM_DAILY_CHARGE.ID)
                .fetchOne(ROOM_DAILY_CHARGE.ID);
    }

    @Override
    public void insertFailedCharge(Long roomId, LocalDate date, String error, OffsetDateTime now) {
        dsl.insertInto(ROOM_DAILY_CHARGE)
                .set(ROOM_DAILY_CHARGE.ROOM_ID, roomId)
                .set(ROOM_DAILY_CHARGE.SETTLEMENT_DATE, date)
                .set(ROOM_DAILY_CHARGE.WATER_AMOUNT, BigDecimal.ZERO)
                .set(ROOM_DAILY_CHARGE.ELECTRIC_AMOUNT, BigDecimal.ZERO)
                .set(ROOM_DAILY_CHARGE.HOT_WATER_AMOUNT, BigDecimal.ZERO)
                .set(ROOM_DAILY_CHARGE.TOTAL_AMOUNT, BigDecimal.ZERO)
                .set(ROOM_DAILY_CHARGE.STATUS, "FAILED")
                .set(ROOM_DAILY_CHARGE.CREATED_AT, now)
                .set(ROOM_DAILY_CHARGE.UPDATED_AT, now)
                .onDuplicateKeyIgnore()
                .execute();
    }

    @Override
    public void updateChargeStatus(Long id, String status, OffsetDateTime now) {
        dsl.update(ROOM_DAILY_CHARGE)
                .set(ROOM_DAILY_CHARGE.STATUS, status)
                .set(ROOM_DAILY_CHARGE.UPDATED_AT, now)
                .where(ROOM_DAILY_CHARGE.ID.eq(id))
                .execute();
    }

    @Override
    public List<RoomDailyCharge> findCharges(Long roomId, String status,
                                              LocalDate startDate, LocalDate endDate,
                                              int offset, int limit) {
        Condition c = trueCondition();
        if (roomId    != null) c = c.and(ROOM_DAILY_CHARGE.ROOM_ID.eq(roomId));
        if (status    != null) c = c.and(ROOM_DAILY_CHARGE.STATUS.eq(status));
        if (startDate != null) c = c.and(ROOM_DAILY_CHARGE.SETTLEMENT_DATE.ge(startDate));
        if (endDate   != null) c = c.and(ROOM_DAILY_CHARGE.SETTLEMENT_DATE.le(endDate));
        return dsl.selectFrom(ROOM_DAILY_CHARGE).where(c)
                .orderBy(ROOM_DAILY_CHARGE.SETTLEMENT_DATE.desc())
                .limit(limit).offset(offset)
                .fetchInto(RoomDailyCharge.class);
    }

    @Override
    public int countCharges(Long roomId, String status, LocalDate startDate, LocalDate endDate) {
        Condition c = trueCondition();
        if (roomId    != null) c = c.and(ROOM_DAILY_CHARGE.ROOM_ID.eq(roomId));
        if (status    != null) c = c.and(ROOM_DAILY_CHARGE.STATUS.eq(status));
        if (startDate != null) c = c.and(ROOM_DAILY_CHARGE.SETTLEMENT_DATE.ge(startDate));
        if (endDate   != null) c = c.and(ROOM_DAILY_CHARGE.SETTLEMENT_DATE.le(endDate));
        return dsl.fetchCount(ROOM_DAILY_CHARGE, c);
    }

    @Override
    public Optional<RoomDailyCharge> findChargeById(Long id) {
        return Optional.ofNullable(dsl.selectFrom(ROOM_DAILY_CHARGE)
                .where(ROOM_DAILY_CHARGE.ID.eq(id))
                .fetchOneInto(RoomDailyCharge.class));
    }

    // ── TenantApportionment ─────────────────────────────────────────────────

    @Override
    public void insertApportionment(Long roomDailyChargeId, Long roomId, Long tenantId,
                                    Long stayId, LocalDate date, BigDecimal amount,
                                    OffsetDateTime now) {
        dsl.insertInto(TENANT_APPORTIONMENT)
                .set(TENANT_APPORTIONMENT.ROOM_DAILY_CHARGE_ID, roomDailyChargeId)
                .set(TENANT_APPORTIONMENT.ROOM_ID, roomId)
                .set(TENANT_APPORTIONMENT.TENANT_ID, tenantId)
                .set(TENANT_APPORTIONMENT.STAY_ID, stayId)
                .set(TENANT_APPORTIONMENT.SETTLEMENT_DATE, date)
                .set(TENANT_APPORTIONMENT.APPORTIONED_AMOUNT, amount)
                .set(TENANT_APPORTIONMENT.CREATED_AT, now)
                .execute();
    }

    @Override
    public List<TenantApportionment> findApportionmentsByChargeId(Long roomDailyChargeId) {
        return dsl.selectFrom(TENANT_APPORTIONMENT)
                .where(TENANT_APPORTIONMENT.ROOM_DAILY_CHARGE_ID.eq(roomDailyChargeId))
                .fetchInto(TenantApportionment.class);
    }
}
