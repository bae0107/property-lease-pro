package com.jugu.propertylease.main.leasecontract.api;

import java.time.LocalDate;

/**
 * 合同基础信息（Port 层传输对象）。
 */
public record ContractInfo(
        Long id,
        String contractNo,
        Long enterpriseId,
        String contractStatus,
        LocalDate startDate,
        LocalDate endDate,
        String paymentMode
) {}
