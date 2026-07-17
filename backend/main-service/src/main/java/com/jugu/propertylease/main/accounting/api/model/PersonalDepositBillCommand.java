package com.jugu.propertylease.main.accounting.api.model;

/**
 * 创建个人押金账单命令。
 */
public record PersonalDepositBillCommand(
        Long tenantId,
        Long stayId,
        Long contractId,
        Long roomId
) {}
