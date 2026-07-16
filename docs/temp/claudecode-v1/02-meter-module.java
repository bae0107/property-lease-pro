// ─────────────────────────────────────────────────────────────────────────────
// MeterCommandPort.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface MeterCommandPort {

    /**
     * 记录入住底数（contract 编排入住时调用）。
     * 必须在 tenancy 持久化之前调用，作为用量计算起点。
     */
    void recordCheckInReading(RecordReadingCommand cmd);

    /**
     * 记录退宿读数（contract 编排退宿时调用）。
     * 返回该租户入住期间完整用量摘要，供 settlement 使用。
     */
    TenancyUsageSummary recordCheckOutReading(RecordReadingCommand cmd);

    /**
     * 提交周期读数（人工录入或 IoT 推送）。
     * 接口幂等：IOT 来源以 (deviceId + readAt) 为幂等键。
     */
    void submitPeriodicReading(RecordReadingCommand cmd);
}

// ─────────────────────────────────────────────────────────────────────────────
// MeterQueryPort.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.api;

import java.util.Optional;

public interface MeterQueryPort {

    /** 获取某入住期间全量用量汇总，供 settlement 结算时使用 */
    TenancyUsageSummary getUsageSummaryForTenancy(Long tenancyId);

    /** 获取房间指定表计的最新读数 */
    Optional<MeterReadingInfo> getLatestReading(Long roomId, String meterType);
}

// ─────────────────────────────────────────────────────────────────────────────
// MeterScheduleTrigger.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.api;

import java.time.LocalDate;

public interface MeterScheduleTrigger {
    /**
     * 由 schedule 每日调用，执行全量房间水电日结。
     * 幂等：已存在 meter_daily_settlement 记录的房间跳过。
     */
    DailySettlementResult triggerDailySettlement(LocalDate date);
}

// ─────────────────────────────────────────────────────────────────────────────
// Meter Port Records
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record RecordReadingCommand(
    Long          roomId,
    String        meterType,      // WATER | ELECTRICITY
    BigDecimal    readingValue,   // 表盘累计读数（非增量）
    OffsetDateTime readAt,
    String        source,         // IOT | MANUAL
    String        category,       // CHECK_IN | CHECK_OUT | PERIODIC
    Long          tenancyId,      // CHECK_IN / CHECK_OUT 时必填
    String        deviceId,       // IOT 来源时填，用于幂等
    Long          operatorId      // MANUAL 来源时填
) {}

public record TenancyUsageSummary(
    Long       tenancyId,
    BigDecimal waterUsage,
    BigDecimal electricityUsage,
    BigDecimal totalAmount,       // 按当期单价计算的应付总金额
    BigDecimal alreadyDeducted,   // 日结期间已成功扣款总额
    BigDecimal outstanding        // 未结清欠费（alreadyDeducted 不足时累计）
) {}

public record MeterReadingInfo(
    Long          id,
    Long          roomId,
    String        meterType,
    BigDecimal    readingValue,
    OffsetDateTime readAt,
    String        source,
    String        category,
    Long          tenancyId
) {}

public record DailySettlementResult(
    LocalDate  date,
    int        totalRooms,
    int        settledRooms,
    int        partialRooms,   // 有欠费的房间数
    int        failedRooms,
    BigDecimal totalAmount,
    BigDecimal totalShortfall
) {}

// ─────────────────────────────────────────────────────────────────────────────
// MeterReadingService.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.meter.api.*;
import com.jugu.propertylease.main.meter.repo.MeterReadingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MeterReadingService {

    private final MeterReadingRepository readingRepo;

    public MeterReadingService(MeterReadingRepository readingRepo) {
        this.readingRepo = readingRepo;
    }

    public void recordCheckInReading(RecordReadingCommand cmd) {
        validateReadingValue(cmd);
        // 直接插入，category=CHECK_IN，tenancyId 必填
        readingRepo.insert(toEntity(cmd));
    }

    public TenancyUsageSummary recordCheckOutReading(RecordReadingCommand cmd) {
        validateReadingValue(cmd);
        readingRepo.insert(toEntity(cmd));
        // 查询该 tenancy 的 CHECK_IN 底数 → 计算入住期间用量
        // 汇总 meter_daily_settlement 的 deducted_from_tenants（属于该 tenancy 的部分）
        // 计算 outstanding = totalAmount - alreadyDeducted
        throw new UnsupportedOperationException("TODO: implement");
    }

    public void submitPeriodicReading(RecordReadingCommand cmd) {
        // IOT 幂等：deviceId + readAt 唯一索引，重复插入捕获 DuplicateKeyException 直接返回
        if (cmd.source().equals("IOT") && cmd.deviceId() != null) {
            if (readingRepo.existsByDeviceIdAndReadAt(cmd.deviceId(), cmd.readAt())) {
                return; // 幂等，已处理
            }
        }
        // 校验读数不能小于同房间同类型的上一次读数
        validateReadingValue(cmd);
        readingRepo.insert(toEntity(cmd));
    }

    private void validateReadingValue(RecordReadingCommand cmd) {
        readingRepo.findLatestReading(cmd.roomId(), cmd.meterType())
            .ifPresent(latest -> {
                if (cmd.readingValue().compareTo(latest.readingValue()) < 0) {
                    throw new BusinessException("METER_READING_INVALID",
                        "读数不能小于历史读数：当前=" + cmd.readingValue()
                            + "，历史=" + latest.readingValue());
                }
            });
    }

    private Object toEntity(RecordReadingCommand cmd) {
        // 转换为 jOOQ 生成的 Record 对象
        throw new UnsupportedOperationException("TODO: implement after jOOQ codegen");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MeterDailySettlementService.java  ← 日结核心逻辑
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.service;

import com.jugu.propertylease.main.account.api.AccountCommandPort;
import com.jugu.propertylease.main.account.api.AccountQueryPort;
import com.jugu.propertylease.main.account.api.DeductCommand;
import com.jugu.propertylease.main.account.api.DeductResult;
import com.jugu.propertylease.main.contract.api.ContractQueryPort;
import com.jugu.propertylease.main.contract.api.TenancyInfo;
import com.jugu.propertylease.main.meter.api.*;
import com.jugu.propertylease.main.meter.repo.MeterDailySettlementRepository;
import com.jugu.propertylease.main.meter.repo.MeterPriceConfigRepository;
import com.jugu.propertylease.main.meter.repo.MeterReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class MeterDailySettlementService implements MeterScheduleTrigger {

    private static final Logger log = LoggerFactory.getLogger(MeterDailySettlementService.class);

    private final MeterReadingRepository readingRepo;
    private final MeterDailySettlementRepository settlementRepo;
    private final MeterPriceConfigRepository priceRepo;
    private final ContractQueryPort contractQueryPort;   // 查询活跃 tenancy
    private final AccountCommandPort accountCommandPort;
    private final AccountQueryPort accountQueryPort;

    public MeterDailySettlementService(
        MeterReadingRepository readingRepo,
        MeterDailySettlementRepository settlementRepo,
        MeterPriceConfigRepository priceRepo,
        ContractQueryPort contractQueryPort,
        AccountCommandPort accountCommandPort,
        AccountQueryPort accountQueryPort
    ) {
        this.readingRepo = readingRepo;
        this.settlementRepo = settlementRepo;
        this.priceRepo = priceRepo;
        this.contractQueryPort = contractQueryPort;
        this.accountCommandPort = accountCommandPort;
        this.accountQueryPort = accountQueryPort;
    }

    @Override
    public DailySettlementResult triggerDailySettlement(LocalDate date) {
        // 1. 查询所有"有活跃 tenancy（CHECKED_IN）"的 room_id 列表
        //    通过 contractQueryPort 查询
        List<Long> activeRoomIds = contractQueryPort.findRoomsWithActiveTenancies();

        int total = activeRoomIds.size(), settled = 0, partial = 0, failed = 0;
        BigDecimal totalAmount = BigDecimal.ZERO, totalShortfall = BigDecimal.ZERO;

        for (Long roomId : activeRoomIds) {
            for (String meterType : List.of("WATER", "ELECTRICITY")) {
                // 幂等跳过：该 (room, date, type) 已有记录则跳过
                if (settlementRepo.exists(roomId, date, meterType)) {
                    settled++;
                    continue;
                }
                try {
                    RoomSettlementResult r = settleRoomMeter(roomId, meterType, date);
                    totalAmount = totalAmount.add(r.totalAmount());
                    totalShortfall = totalShortfall.add(r.shortfall());
                    if (r.shortfall().compareTo(BigDecimal.ZERO) > 0) partial++;
                    else settled++;
                } catch (Exception e) {
                    log.error("日结失败 roomId={} meterType={} date={}", roomId, meterType, date, e);
                    settlementRepo.insertFailed(roomId, meterType, date, e.getMessage());
                    failed++;
                }
            }
        }
        return new DailySettlementResult(date, total, settled, partial, failed, totalAmount, totalShortfall);
    }

    /**
     * 单房间单表计日结（独立事务，失败不影响其他房间）。
     */
    @Transactional
    protected RoomSettlementResult settleRoomMeter(Long roomId, String meterType, LocalDate date) {
        // 1. 取昨日（或 CHECK_IN 底数）start_reading，今日 end_reading
        BigDecimal startReading = readingRepo.findStartReading(roomId, meterType, date)
            .orElseThrow(() -> new IllegalStateException("缺少起始读数 roomId=" + roomId));
        BigDecimal endReading = readingRepo.findEndReading(roomId, meterType, date)
            .orElseThrow(() -> new IllegalStateException("缺少截止读数 roomId=" + roomId));

        BigDecimal usage = endReading.subtract(startReading);

        // 2. 查单价（优先 store 级别，fallback 全局默认）
        Long storeId = contractQueryPort.getStoreIdByRoom(roomId);
        BigDecimal unitPrice = priceRepo.findEffectivePrice(storeId, meterType, date)
            .orElseThrow(() -> new IllegalStateException("未配置计费单价 meterType=" + meterType));

        BigDecimal totalAmount = usage.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        // 3. 先从 ROOM_SHARED 账户扣款
        Long sharedAccountId = contractQueryPort.getSharedAccountId(roomId);
        DeductResult sharedResult = accountCommandPort.deduct(new DeductCommand(
            sharedAccountId, totalAmount, "METER_SETTLEMENT", null,
            date + " " + meterType + " 日结"
        ));
        BigDecimal remainder = sharedResult.shortfall();

        BigDecimal deductedFromTenants = BigDecimal.ZERO;
        BigDecimal shortfall = BigDecimal.ZERO;

        // 4. ROOM_SHARED 不足时，按在住租客人数平摊
        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            List<TenancyInfo> tenancies = contractQueryPort.getActiveTenanciesByRoom(roomId);
            if (!tenancies.isEmpty()) {
                // 向上取整，最后一个人承担尾差
                BigDecimal perTenant = remainder.divide(
                    BigDecimal.valueOf(tenancies.size()), 2, RoundingMode.CEILING);

                for (int i = 0; i < tenancies.size(); i++) {
                    TenancyInfo t = tenancies.get(i);
                    // 最后一个人可能金额略有不同（尾差处理）
                    BigDecimal amount = (i == tenancies.size() - 1)
                        ? remainder.subtract(perTenant.multiply(BigDecimal.valueOf(i)))
                        : perTenant;

                    DeductResult r = accountCommandPort.deduct(new DeductCommand(
                        t.tenantAccountId(), amount, "METER_SETTLEMENT", null,
                        date + " " + meterType + " 日结平摊"
                    ));
                    deductedFromTenants = deductedFromTenants.add(r.deducted());
                    shortfall = shortfall.add(r.shortfall());
                }
            } else {
                // 无在住租客（罕见），全部计入欠费
                shortfall = remainder;
            }
        }

        // 5. 写日结记录
        String status = shortfall.compareTo(BigDecimal.ZERO) > 0 ? "PARTIAL" : "SETTLED";
        settlementRepo.insert(roomId, date, meterType, startReading, endReading,
            usage, unitPrice, totalAmount, sharedResult.deducted(), deductedFromTenants,
            shortfall, status);

        return new RoomSettlementResult(totalAmount, shortfall);
    }

    record RoomSettlementResult(BigDecimal totalAmount, BigDecimal shortfall) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// MeterPriceService.java
// ─────────────────────────────────────────────────────────────────────────────
package com.jugu.propertylease.main.meter.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.meter.repo.MeterPriceConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class MeterPriceService {

    private final MeterPriceConfigRepository priceRepo;

    public MeterPriceService(MeterPriceConfigRepository priceRepo) {
        this.priceRepo = priceRepo;
    }

    public Object createPrice(Long storeId, String meterType, BigDecimal unitPrice, LocalDate effectiveFrom) {
        // 1. 校验 effectiveFrom >= today
        if (effectiveFrom.isBefore(LocalDate.now())) {
            throw new BusinessException("METER_PRICE_DATE_INVALID", "生效日期不能早于今日");
        }
        // 2. 关闭同 storeId + meterType 的当前有效版本（effective_to = effectiveFrom - 1）
        priceRepo.closeCurrentVersion(storeId, meterType, effectiveFrom.minusDays(1));
        // 3. 插入新版本（effective_to = NULL）
        return priceRepo.insert(storeId, meterType, unitPrice, effectiveFrom);
    }

    public List<Object> listPrices(Long storeId, String meterType) {
        return priceRepo.findAll(storeId, meterType);
    }
}
