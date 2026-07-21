package com.jugu.propertylease.main.metering.service;

import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.AccountingQueryPort;
import com.jugu.propertylease.main.accounting.api.model.DailyDeductionCommand;
import com.jugu.propertylease.main.accounting.api.model.DailyDeductionResult;
import com.jugu.propertylease.main.accounting.api.model.TenantDeductItem;
import com.jugu.propertylease.main.contract.api.ContractQueryPort;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterDeviceBinding;
import com.jugu.propertylease.main.jooq.tables.pojos.MeterReading;
import com.jugu.propertylease.main.metering.api.*;
import com.jugu.propertylease.main.metering.repo.MeteringRepository;
import com.jugu.propertylease.main.occupancy.api.OccupancyQueryPort;
import com.jugu.propertylease.main.occupancy.api.StayInfo;
import com.jugu.propertylease.main.propertymgr.api.AssetQueryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Metering 核心业务逻辑，实现 {@link MeteringCommandPort} 和 {@link MeteringScheduleTrigger}。
 */
@Service
public class MeteringService implements MeteringCommandPort, MeteringScheduleTrigger {

    private static final Logger log = LoggerFactory.getLogger(MeteringService.class);

    private static final List<String> METER_TYPES = List.of("WATER", "ELECTRICITY", "HOT_WATER");

    private final MeteringRepository repo;
    private final OccupancyQueryPort occupancyQueryPort;
    private final AssetQueryPort assetQueryPort;
    private final AccountingCommandPort accountingCommandPort;
    private final AccountingQueryPort accountingQueryPort;
    private final ContractQueryPort contractQueryPort;

    public MeteringService(MeteringRepository repo,
                           OccupancyQueryPort occupancyQueryPort,
                           AssetQueryPort assetQueryPort,
                           AccountingCommandPort accountingCommandPort,
                           AccountingQueryPort accountingQueryPort,
                           ContractQueryPort contractQueryPort) {
        this.repo = repo;
        this.occupancyQueryPort = occupancyQueryPort;
        this.assetQueryPort = assetQueryPort;
        this.accountingCommandPort = accountingCommandPort;
        this.accountingQueryPort = accountingQueryPort;
        this.contractQueryPort = contractQueryPort;
    }

    // ══════════════════════════════════════════════════════════════════════
    // MeteringCommandPort 实现
    // ══════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public String collectCheckInReadings(CollectReadingsCommand cmd) {
        String groupId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        List<MeterDeviceBinding> bindings = repo.findActiveBindings(cmd.roomId());
        for (MeterDeviceBinding b : bindings) {
            BigDecimal readingVal = resolveReading(b.getMeterType(), b.getInitialReading(),
                    cmd.manualReadings());
            repo.insertReading(cmd.roomId(), b.getId(), b.getMeterType(),
                    readingVal, now, "MANUAL", "CHECK_IN",
                    cmd.stayId(), groupId, null, cmd.operatorId());
        }

        repo.insertAnchor(cmd.roomId(), cmd.stayId(), "CHECK_IN", groupId, now);
        return groupId;
    }

    @Override
    @Transactional
    public UsageSummary collectCheckOutReadings(CollectReadingsCommand cmd) {
        String groupId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        List<MeterDeviceBinding> bindings = repo.findActiveBindings(cmd.roomId());
        for (MeterDeviceBinding b : bindings) {
            BigDecimal readingVal = resolveReading(b.getMeterType(), b.getInitialReading(),
                    cmd.manualReadings());
            repo.insertReading(cmd.roomId(), b.getId(), b.getMeterType(),
                    readingVal, now, "MANUAL", "CHECK_OUT",
                    cmd.stayId(), groupId, null, cmd.operatorId());
        }

        repo.insertAnchor(cmd.roomId(), cmd.stayId(), "CHECK_OUT", groupId, now);

        return buildUsageSummary(cmd.stayId(), cmd.roomId(), LocalDate.now());
    }

    @Override
    @Transactional
    public void submitPeriodicReading(RecordReadingCommand cmd) {
        // IoT 幂等检查
        if ("IOT".equals(cmd.source()) && cmd.deviceIdRaw() != null) {
            if (repo.existsByDeviceIdRawAndTime(cmd.deviceIdRaw(), cmd.readingTime())) {
                return; // 已处理，幂等返回
            }
        }
        // 查找对应绑定版本
        MeterDeviceBinding binding = repo.findActiveBinding(cmd.roomId(), cmd.meterType())
                .orElseThrow(() -> new IllegalStateException(
                        "未找到活跃设备绑定 roomId=" + cmd.roomId() + " meterType=" + cmd.meterType()));

        repo.insertReading(cmd.roomId(), binding.getId(), cmd.meterType(),
                cmd.readingValue(), cmd.readingTime(), cmd.source(),
                "PERIODIC", null, null, cmd.deviceIdRaw(), cmd.operatorId());
    }

    // ══════════════════════════════════════════════════════════════════════
    // MeteringScheduleTrigger 实现
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public DailySettlementResult runDailySettlement(LocalDate date) {
        List<Long> roomIds = occupancyQueryPort.findRoomIdsWithCheckedInStays(date);
        int settled = 0, partial = 0, failed = 0;
        BigDecimal totalAmount = BigDecimal.ZERO, totalShortfall = BigDecimal.ZERO;

        for (Long roomId : roomIds) {
            // 幂等：已存在则跳过
            if (repo.existsCharge(roomId, date)) {
                settled++;
                continue;
            }
            try {
                var result = settleOneRoom(roomId, date);
                totalAmount    = totalAmount.add(result.totalAmount());
                totalShortfall = totalShortfall.add(result.shortfall());
                if (result.shortfall().compareTo(BigDecimal.ZERO) > 0) partial++;
                else settled++;
            } catch (Exception e) {
                log.error("[metering] 日结失败 roomId={} date={}", roomId, date, e);
                repo.insertFailedCharge(roomId, date, e.getMessage(), OffsetDateTime.now());
                failed++;
            }
        }

        return new DailySettlementResult(date, roomIds.size(),
                settled, partial, failed, totalAmount, totalShortfall);
    }

    @Transactional
    protected RoomSettleResult settleOneRoom(Long roomId, LocalDate date) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dayStart = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime dayEnd   = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        BigDecimal waterAmt = BigDecimal.ZERO;
        BigDecimal elecAmt  = BigDecimal.ZERO;
        BigDecimal hwAmt    = BigDecimal.ZERO;

        Long storeId = assetQueryPort.getStoreIdByRoomId(roomId);

        for (String meterType : METER_TYPES) {
            MeterDeviceBinding binding = repo.findActiveBinding(roomId, meterType).orElse(null);
            if (binding == null) continue;

            // 截止读数：当日最新 PERIODIC
            MeterReading endReading = repo.findLatestInRange(roomId, meterType,
                    dayStart, dayEnd).orElse(null);
            if (endReading == null) continue; // 无读数，跳过该表类型

            // 起始读数：昨日最新，无则取 CHECK_IN 底数
            MeterReading startReading = repo.findLatestBefore(roomId, meterType, dayStart)
                    .orElse(null);
            if (startReading == null) continue;

            BigDecimal usage = endReading.getReadingValue()
                    .subtract(startReading.getReadingValue());
            if (usage.compareTo(BigDecimal.ZERO) < 0) usage = BigDecimal.ZERO;

            BigDecimal price = repo.findEffectivePrice(storeId, meterType, date)
                    .orElse(BigDecimal.ZERO);
            BigDecimal amount = usage.multiply(price).setScale(2, RoundingMode.HALF_UP);

            switch (meterType) {
                case "WATER"       -> waterAmt = amount;
                case "ELECTRICITY" -> elecAmt  = amount;
                case "HOT_WATER"   -> hwAmt    = amount;
            }
        }

        BigDecimal totalAmount = waterAmt.add(elecAmt).add(hwAmt);
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            // 无费用，写 SETTLED 记录
            repo.insertCharge(roomId, date, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, "SETTLED", now);
            return new RoomSettleResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // 查询企业余额，计算分摊
        List<StayInfo> stays = occupancyQueryPort.getCheckedInStaysByRoom(roomId);
        Long enterpriseId = resolveEnterpriseId(stays);

        BigDecimal enterpriseBalance = accountingQueryPort.getEnterpriseSubBalance(
                roomId, enterpriseId != null ? enterpriseId : -1L);
        BigDecimal enterpriseCover = totalAmount.min(enterpriseBalance);
        BigDecimal remainder = totalAmount.subtract(enterpriseCover);

        List<TenantDeductItem> tenantItems = new ArrayList<>();
        if (!stays.isEmpty() && remainder.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal perTenant = remainder.divide(
                    BigDecimal.valueOf(stays.size()), 2, RoundingMode.CEILING);
            for (int i = 0; i < stays.size(); i++) {
                StayInfo s = stays.get(i);
                // 最后一人承担尾差
                BigDecimal thisAmount = (i == stays.size() - 1)
                        ? remainder.subtract(perTenant.multiply(BigDecimal.valueOf(i)))
                        : perTenant;
                tenantItems.add(new TenantDeductItem(s.tenantId(), s.id(), thisAmount));
            }
        }

        DailyDeductionResult deductResult = accountingCommandPort.deductForDailySettlement(
                new DailyDeductionCommand(roomId, date, totalAmount, enterpriseCover, tenantItems));

        BigDecimal shortfall = deductResult.shortfall();
        String status = shortfall.compareTo(BigDecimal.ZERO) > 0 ? "PARTIAL" : "SETTLED";

        Long chargeId = repo.insertCharge(roomId, date, waterAmt, elecAmt, hwAmt,
                totalAmount, status, now);

        // 写分摊明细
        for (TenantDeductItem item : tenantItems) {
            repo.insertApportionment(chargeId, roomId, item.tenantId(),
                    item.stayId(), date, item.amount(), now);
        }

        return new RoomSettleResult(totalAmount, shortfall);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 内部辅助方法
    // ══════════════════════════════════════════════════════════════════════

    private BigDecimal resolveReading(String meterType, BigDecimal fallbackInitial,
                                       List<ManualReadingEntry> manuals) {
        if (manuals != null) {
            for (ManualReadingEntry m : manuals) {
                if (meterType.equals(m.meterType())) return m.readingValue();
            }
        }
        // IoT 设备离线且无人工录入，使用绑定初始读数（零增量）
        return fallbackInitial != null ? fallbackInitial : BigDecimal.ZERO;
    }

    private UsageSummary buildUsageSummary(Long stayId, Long roomId, LocalDate today) {
        // 计算入住期间总用量（简化：取 CHECK_IN 和 CHECK_OUT 读数差值）
        BigDecimal waterUsage = BigDecimal.ZERO, elecUsage = BigDecimal.ZERO,
                   hwUsage = BigDecimal.ZERO, totalAmount = BigDecimal.ZERO;

        Long storeId = assetQueryPort.getStoreIdByRoomId(roomId);
        for (String mt : METER_TYPES) {
            List<MeterReading> checkIns  = repo.findByStayAndAnchorType(stayId, "CHECK_IN");
            List<MeterReading> checkOuts = repo.findByStayAndAnchorType(stayId, "CHECK_OUT");

            BigDecimal startVal = checkIns.stream()
                    .filter(r -> mt.equals(r.getMeterType()))
                    .map(MeterReading::getReadingValue).findFirst().orElse(BigDecimal.ZERO);
            BigDecimal endVal = checkOuts.stream()
                    .filter(r -> mt.equals(r.getMeterType()))
                    .map(MeterReading::getReadingValue).findFirst().orElse(BigDecimal.ZERO);

            BigDecimal usage = endVal.subtract(startVal).max(BigDecimal.ZERO);
            BigDecimal price = repo.findEffectivePrice(storeId, mt, today).orElse(BigDecimal.ZERO);
            BigDecimal amt   = usage.multiply(price).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(amt);

            switch (mt) {
                case "WATER"       -> waterUsage = usage;
                case "ELECTRICITY" -> elecUsage  = usage;
                case "HOT_WATER"   -> hwUsage    = usage;
            }
        }

        return new UsageSummary(stayId, waterUsage, elecUsage, hwUsage,
                totalAmount, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private Long resolveEnterpriseId(List<StayInfo> stays) {
        // 同一房间的在住 Stay 必属同一合同（一个房间同时只有一个活跃合同），
        // 取任一 Stay 的 contractId 反查企业即可；无在住时无法解析，返回 null（费用由租客分摊承担）。
        if (stays.isEmpty()) {
            return null;
        }
        return contractQueryPort.getContract(stays.get(0).contractId()).enterpriseId();
    }

    record RoomSettleResult(BigDecimal totalAmount, BigDecimal shortfall) {}

    // ══════════════════════════════════════════════════════════════════════
    // 供外部 API Delegate 调用
    // ══════════════════════════════════════════════════════════════════════

    public void bindDevice(Long roomId, Long deviceId, String meterType,
                           BigDecimal initialReading, Long operatorId) {
        OffsetDateTime now = OffsetDateTime.now();
        // 关闭旧绑定
        repo.findActiveBinding(roomId, meterType)
                .ifPresent(old -> repo.closeBinding(old.getId(), now));
        // 新建绑定
        repo.insertBinding(roomId, deviceId, meterType, now, initialReading, operatorId);
    }

    public void unbindDevice(Long roomId, Long bindingId) {
        repo.closeBinding(bindingId, OffsetDateTime.now());
    }

    public void createMeterPrice(Long storeId, String meterType, BigDecimal unitPrice,
                                  LocalDate effectiveFrom, Long operatorId) {
        if (effectiveFrom.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("生效日期不能早于今日");
        }
        repo.closeCurrentPrice(storeId, meterType, effectiveFrom.minusDays(1));
        repo.insertPrice(storeId, meterType, unitPrice, effectiveFrom, operatorId);
    }
}
