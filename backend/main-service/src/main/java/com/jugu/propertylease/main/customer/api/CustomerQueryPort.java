package com.jugu.propertylease.main.customer.api;

import com.jugu.propertylease.main.customer.api.model.CustomerEmployeeInfo;
import com.jugu.propertylease.main.customer.api.model.CustomerEnterpriseInfo;

/**
 * 客户模块查询 Port（进程内，供 contract / occupancy 调用）。
 */
public interface CustomerQueryPort {

    /**
     * 查询企业信息。
     *
     * @throws com.jugu.propertylease.common.exception.BusinessException 404 若企业不存在
     */
    CustomerEnterpriseInfo getEnterprise(Long enterpriseId);

    /**
     * 查询员工信息。
     *
     * @throws com.jugu.propertylease.common.exception.BusinessException 404 若员工不存在
     */
    CustomerEmployeeInfo getEmployee(Long employeeId);

    /**
     * 校验员工是否属于该企业（且员工状态为 ACTIVE）。
     * 用于 occupancy.assignTenantToRoom 和 checkIn 的前置校验。
     */
    boolean isEmployeeOfEnterprise(Long employeeId, Long enterpriseId);
}
