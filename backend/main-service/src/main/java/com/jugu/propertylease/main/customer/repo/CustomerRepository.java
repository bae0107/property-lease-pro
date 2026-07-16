package com.jugu.propertylease.main.customer.repo;

import com.jugu.propertylease.main.customer.api.model.CustomerEmployeeInfo;
import com.jugu.propertylease.main.customer.api.model.CustomerEnterpriseInfo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository {

    // ── Enterprise ──────────────────────────────────────────────────────

    Long insertEnterprise(String name, String contactName, String contactMobile,
                          String remark, Long createdBy, OffsetDateTime now);

    Optional<CustomerEnterpriseInfo> findEnterpriseById(Long id);

    List<CustomerEnterpriseInfo> findEnterprisesByStatus(String status, int offset, int limit);

    int countEnterprisesByStatus(String status);

    void updateEnterprise(Long id, String name, String contactName, String contactMobile,
                          String remark, OffsetDateTime now);

    void updateEnterpriseStatus(Long id, String status, OffsetDateTime now);

    // ── Employee ─────────────────────────────────────────────────────────

    Long insertEmployee(Long enterpriseId, String name, String mobile,
                        Long createdBy, OffsetDateTime now);

    Optional<CustomerEmployeeInfo> findEmployeeById(Long id);

    List<CustomerEmployeeInfo> findEmployeesByEnterprise(Long enterpriseId, String status,
                                                          int offset, int limit);

    int countEmployeesByEnterprise(Long enterpriseId, String status);

    void updateEmployee(Long id, String name, String mobile, OffsetDateTime now);

    void updateEmployeeStatus(Long id, String status, OffsetDateTime now);

    /**
     * 校验员工是否属于该企业且状态 ACTIVE（Port 校验专用）。
     */
    boolean existsActiveEmployeeInEnterprise(Long employeeId, Long enterpriseId);
}
