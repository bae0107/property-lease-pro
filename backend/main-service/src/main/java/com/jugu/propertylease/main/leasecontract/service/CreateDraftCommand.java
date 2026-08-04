package com.jugu.propertylease.main.leasecontract.service;

import java.time.LocalDate;
import java.util.List;

/** {@link ContractLifecycleService#createDraft} 入参。*/
public record CreateDraftCommand(
        Long enterpriseId,
        LocalDate startDate,
        LocalDate endDate,
        String paymentMode,
        String remark,
        List<CreateContractRoomCommand> rooms,
        List<CreateChargeRuleCommand> chargeRules,
        Long operatorId
) {}
