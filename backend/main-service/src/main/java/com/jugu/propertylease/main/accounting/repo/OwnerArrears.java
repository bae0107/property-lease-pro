package com.jugu.propertylease.main.accounting.repo;

import java.math.BigDecimal;

/**
 * 按欠费方（owner）汇总的未清偿欠费金额（正数）。
 */
public record OwnerArrears(String ownerType, Long ownerId, BigDecimal amount) {}
