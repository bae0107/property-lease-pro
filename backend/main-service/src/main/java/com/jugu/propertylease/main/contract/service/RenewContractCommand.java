package com.jugu.propertylease.main.contract.service;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link ContractLifecycleService#renewContract} 入参。
 * enterpriseId 从原合同带出，不在此命令中。
 */
public record RenewContractCommand(
        LocalDate startDate,
        LocalDate endDate,
        String paymentMode,
        String remark,
        List<CreateContractRoomCommand> rooms,
        List<CreateChargeRuleCommand> chargeRules,
        Long operatorId
) {}
