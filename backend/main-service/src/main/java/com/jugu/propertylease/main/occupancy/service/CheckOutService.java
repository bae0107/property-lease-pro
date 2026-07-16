package com.jugu.propertylease.main.occupancy.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.accounting.api.AccountingCommandPort;
import com.jugu.propertylease.main.accounting.api.model.DepositSettlementResult;
import com.jugu.propertylease.main.iam.api.IamTenantPort;
import com.jugu.propertylease.main.jooq.tables.pojos.Stay;
import com.jugu.propertylease.main.metering.api.CollectReadingsCommand;
import com.jugu.propertylease.main.metering.api.MeteringCommandPort;
import com.jugu.propertylease.main.occupancy.outer.DoorLockPort;
import com.jugu.propertylease.main.occupancy.repo.OccupancyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 退宿办理编排服务（单一 @Transactional）。
 *
 * <p>编排步骤：
 * <ol>
 *   <li>校验 stay.stayStatus == CHECKED_IN</li>
 *   <li>stay → CHECKED_OUT，记录 checkOutAt</li>
 *   <li>MeteringCommandPort.collectCheckOutReadings</li>
 *   <li>DoorLockPort.revokeCredential</li>
 *   <li>AccountingCommandPort.settlePersonalDepositOnCheckout</li>
 *   <li>IamTenantPort.disableTenantUser（若 stay 已绑定 iam_user_id）</li>
 * </ol>
 */
@Service
public class CheckOutService {

    private final OccupancyRepository repo;
    private final MeteringCommandPort meteringCommandPort;
    private final AccountingCommandPort accountingCommandPort;
    private final DoorLockPort doorLockPort;
    private final IamTenantPort iamTenantPort;

    public CheckOutService(OccupancyRepository repo,
                            MeteringCommandPort meteringCommandPort,
                            AccountingCommandPort accountingCommandPort,
                            DoorLockPort doorLockPort,
                            IamTenantPort iamTenantPort) {
        this.repo = repo;
        this.meteringCommandPort = meteringCommandPort;
        this.accountingCommandPort = accountingCommandPort;
        this.doorLockPort = doorLockPort;
        this.iamTenantPort = iamTenantPort;
    }

    @Transactional
    public CheckOutResult checkOut(Long stayId, Long operatorId) {
        // 1. 校验 stay
        Stay stay = repo.findStayById(stayId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "STAY_NOT_FOUND", "入住记录不存在：" + stayId));

        if (!"CHECKED_IN".equals(stay.getStayStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "STAY_STATUS_INVALID",
                    "仅 CHECKED_IN 状态可办理退宿，当前：" + stay.getStayStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 2. stay → CHECKED_OUT
        repo.updateStayStatus(stayId, "CHECKED_OUT", now, now);

        // 3. metering 采集退宿锚点读数
        meteringCommandPort.collectCheckOutReadings(new CollectReadingsCommand(
                stayId, stay.getRoomId(), "CHECK_OUT", List.of(), operatorId));

        // 4. 门锁回收凭证
        doorLockPort.revokeCredential(stay.getTenantId(), stay.getRoomId(), stayId);

        // 5. 个人押金结算（退款单据异步处理，此处只发起）
        DepositSettlementResult settlement =
                accountingCommandPort.settlePersonalDepositOnCheckout(stay.getTenantId(), stayId);

        // 6. 禁用 IAM 账号（若已绑定）
        if (stay.getIamUserId() != null) {
            iamTenantPort.disableTenantUser(stay.getIamUserId());
        }

        Stay updated = repo.findStayById(stayId).orElseThrow();
        return new CheckOutResult(updated, settlement.refundBillId(), settlement.refundAmount());
    }
}
