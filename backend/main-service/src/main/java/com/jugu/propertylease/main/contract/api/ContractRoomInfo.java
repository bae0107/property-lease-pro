package com.jugu.propertylease.main.contract.api;

import java.math.BigDecimal;

/**
 * 合同房间信息（Port 层传输对象）。
 */
public record ContractRoomInfo(
        Long id,
        Long contractId,
        Long roomId,
        BigDecimal signedRent,
        String status
) {}
