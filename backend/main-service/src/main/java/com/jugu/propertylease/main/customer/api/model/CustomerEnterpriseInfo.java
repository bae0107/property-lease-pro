package com.jugu.propertylease.main.customer.api.model;

/**
 * 企业基础信息（Port 层传输对象）。
 */
public record CustomerEnterpriseInfo(
        Long id,
        String name,
        String status
) {}
