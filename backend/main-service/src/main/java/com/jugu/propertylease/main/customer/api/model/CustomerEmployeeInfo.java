package com.jugu.propertylease.main.customer.api.model;

/**
 * 员工基础信息（Port 层传输对象）。
 *
 * <p>在 occupancy / contract / accounting 等模块中，
 * 该员工在"入住"语境下被称为 tenant，其 {@code id} 即为 {@code tenant_id}。
 */
public record CustomerEmployeeInfo(
        Long id,
        Long enterpriseId,
        String name,
        String mobile,
        String status
) {}
