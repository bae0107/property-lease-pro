package com.jugu.propertylease.main.contract.api;

import com.jugu.propertylease.main.contract.repo.ContractRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    public ContractScheduleTriggerImpl(ContractRepository repo) {
        this.repo = repo;
    }

    @Override
    public void checkContractExpiry(LocalDate date) {
        List<Contract> dueContracts = repo.findExpiringContracts(
                date.plusDays(REMINDER_THRESHOLD_DAYS), ACTIVE_STATUSES);

        for (Contract contract : dueContracts) {
            long daysUntilEnd = java.time.temporal.ChronoUnit.DAYS.between(date, contract.getEndDate());

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

    /** 避免直接依赖 ContractLifecycleService 造成循环依赖，这里复制一份状态常量。*/
    private static final class ContractLifecycleServiceStatuses {
        static final String READY_FOR_CHECK_IN = "READY_FOR_CHECK_IN";
        static final String PARTIALLY_RETURNED = "PARTIALLY_RETURNED";
    }
}
