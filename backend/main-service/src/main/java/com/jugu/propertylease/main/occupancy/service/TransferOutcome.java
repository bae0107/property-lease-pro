package com.jugu.propertylease.main.occupancy.service;

import com.jugu.propertylease.main.jooq.tables.pojos.Stay;

/**
 * {@link TransferService#transferTenant} 返回结果。
 */
public record TransferOutcome(
        Stay fromStay,
        Stay toStay,
        Long transferRecordId
) {}
