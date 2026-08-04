package com.jugu.propertylease.main.leasecontract.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@link ContractLifecycleService#createDraft} 入参：单个房间的签约信息。*/
public record CreateContractRoomCommand(
        Long roomId,
        BigDecimal signedRent,
        LocalDate leaseStart,
        LocalDate leaseEnd
) {}
