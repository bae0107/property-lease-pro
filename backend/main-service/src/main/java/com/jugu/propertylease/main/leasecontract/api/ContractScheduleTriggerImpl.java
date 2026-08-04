package com.jugu.propertylease.main.leasecontract.api;

import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.model.RentBillCommand;
import com.jugu.propertylease.main.leasecontract.repo.ContractRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import com.jugu.propertylease.main.jooq.tables.pojos.ContractRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * {@link ContractScheduleTrigger} 实现，由 schedule 模块每日 01:00 Cron 调用。
 *
 * <p>⚠️ Stub 说明：接口文档要求"发送到期提醒通知"，但整个代码库里没有出现过
 * NotificationPort / SmsPort 之类的外部通知接口（不像 billing-service /
 * device-service 那样有 StubBillingServicePort / StubDoorLockPort 占位）。
 * 本实现暂时只记录日志，等真正的通知渠道 Port 确定后，把日志那行换成
 * Port 调用即可，编排逻辑本身不用动。
 */
@Service
public class ContractScheduleTriggerImpl implements ContractScheduleTrigger {

    private static final Logger log = LoggerFactory.getLogger(ContractScheduleTriggerImpl.class);

    /** 到期提醒阈值（天）。*/
    private static final long REMINDER_THRESHOLD_DAYS = 7;

    private static final List<String> ACTIVE_STATUSES = List.of(
            ContractLifecycleServiceStatuses.READY_FOR_CHECK_IN,
            ContractLifecycleServiceStatuses.PARTIALLY_RETURNED
    );

    private final ContractRepository repo;
    private final AccountingCommandPort accountingCommandPort;

    public ContractScheduleTriggerImpl(ContractRepository repo,
                                       AccountingCommandPort accountingCommandPort) {
        this.repo = repo;
        this.accountingCommandPort = accountingCommandPort;
    }

    @Override
    public void checkContractExpiry(LocalDate date) {
        List<Contract> dueContracts = repo.findExpiringContracts(
                date.plusDays(REMINDER_THRESHOLD_DAYS), ACTIVE_STATUSES);

        for (Contract contract : dueContracts) {
            long daysUntilEnd = ChronoUnit.DAYS.between(date, contract.getEndDate());

            if (contract.getEndDate().isBefore(date)) {
                log.warn("合同已到期但仍处于活跃状态，需人工发起终止：contractId={}, contractNo={}, endDate={}",
                        contract.getId(), contract.getContractNo(), contract.getEndDate());
            } else if (!contract.getEndDate().isAfter(date.plusDays(REMINDER_THRESHOLD_DAYS))) {
                log.info("[STUB 通知] 合同即将到期提醒：contractId={}, contractNo={}, endDate={}, "
                                + "剩余天数≈{}",
                        contract.getId(), contract.getContractNo(), contract.getEndDate(),
                        daysUntilEnd);
            }
        }
    }

    @Override
    public void checkAndGenerateRentBills(LocalDate date) {
        if (date.getDayOfMonth() != 1) {
            return; // 仅每月 1 号出账
        }

        List<Contract> contracts = repo.findByStatuses(ACTIVE_STATUSES);
        for (Contract contract : contracts) {
            try {
                generateRentBill(contract, date);
            } catch (Exception e) {
                // 单合同失败不阻断其他合同；当月幂等，明日 Cron 自然重试
                log.error("[contract] 月租账单生成失败 contractId={} date={}",
                        contract.getId(), date, e);
            }
        }
    }

    private void generateRentBill(Contract contract, LocalDate date) {
        // QUARTERLY：仅在与起租月相隔 3 的倍数的月份出账，一次出 3 个月
        int months = 1;
        if ("QUARTERLY".equals(contract.getPaymentMode())) {
            long elapsed = ChronoUnit.MONTHS.between(
                    YearMonth.from(contract.getStartDate()), YearMonth.from(date));
            if (elapsed % 3 != 0) {
                return;
            }
            months = 3;
        }

        BigDecimal monthlyRent = repo.findRoomsByContract(contract.getId(), "ACTIVE").stream()
                .map(ContractRoom::getSignedRent)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (monthlyRent.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal amount = monthlyRent.multiply(BigDecimal.valueOf(months));
        var result = accountingCommandPort.createRentBill(new RentBillCommand(
                contract.getId(), contract.getEnterpriseId(), amount,
                YearMonth.from(date).toString()));
        if (result == null) {
            log.info("[contract] 月租账单已存在，跳过 contractId={} period={}",
                    contract.getId(), YearMonth.from(date));
        } else {
            log.info("[contract] 月租账单已生成 contractId={} billId={} amount={}",
                    contract.getId(), result.billId(), amount);
        }
    }

    /** 避免直接依赖 ContractLifecycleService 造成循环依赖，这里复制一份状态常量。*/
    private static final class ContractLifecycleServiceStatuses {
        static final String READY_FOR_CHECK_IN = "READY_FOR_CHECK_IN";
        static final String PARTIALLY_RETURNED = "PARTIALLY_RETURNED";
    }
}
