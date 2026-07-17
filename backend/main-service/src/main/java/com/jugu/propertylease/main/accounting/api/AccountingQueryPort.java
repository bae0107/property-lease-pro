package com.jugu.propertylease.main.accounting.api;

import com.jugu.propertylease.main.accounting.api.model.*;
import java.math.BigDecimal;

/**
 * Accounting 模块查询 Port（进程内，供 metering / contract 查询账务数据）。
 */
public interface AccountingQueryPort {

    /**
     * 查询房间账户中企业子余额（metering 日结计划时使用）。
     *
     * @param roomId       room_info.RoomId
     * @param enterpriseId enterprise.id
     * @return 可用余额，不存在返回 ZERO
     */
    BigDecimal getEnterpriseSubBalance(Long roomId, Long enterpriseId);

    /**
     * 查询个人押金台账状态（occupancy.transfer 前置校验：押金需 ACTIVE）。
     */
    DepositLedgerInfo getPersonalDepositLedger(Long tenantId, Long stayId);
}
