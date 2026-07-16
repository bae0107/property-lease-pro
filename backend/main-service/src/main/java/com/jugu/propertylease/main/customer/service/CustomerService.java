package com.jugu.propertylease.main.customer.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.customer.api.CustomerQueryPort;
import com.jugu.propertylease.main.customer.api.model.CustomerEmployeeInfo;
import com.jugu.propertylease.main.customer.api.model.CustomerEnterpriseInfo;
import com.jugu.propertylease.main.customer.repo.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * customer 模块业务逻辑服务，同时实现 {@link CustomerQueryPort}（Port Interface）。
 */
@Service
public class CustomerService implements CustomerQueryPort {

    private final CustomerRepository repo;

    public CustomerService(CustomerRepository repo) {
        this.repo = repo;
    }

    // ── Port Interface 实现 ────────────────────────────────────────────

    @Override
    public CustomerEnterpriseInfo getEnterprise(Long enterpriseId) {
        return repo.findEnterpriseById(enterpriseId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ENTERPRISE_NOT_FOUND",
                        "企业不存在：" + enterpriseId));
    }

    @Override
    public CustomerEmployeeInfo getEmployee(Long employeeId) {
        return repo.findEmployeeById(employeeId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND",
                        "员工不存在：" + employeeId));
    }

    @Override
    public boolean isEmployeeOfEnterprise(Long employeeId, Long enterpriseId) {
        return repo.existsActiveEmployeeInEnterprise(employeeId, enterpriseId);
    }

    // ── 对外 API 支撑（由 Delegate 调用）─────────────────────────────────

    @Transactional
    public Long createEnterprise(String name, String contactName, String contactMobile,
                                  String remark, Long operatorId) {
        return repo.insertEnterprise(name, contactName, contactMobile,
                remark, operatorId, OffsetDateTime.now());
    }

    public record EnterprisePageResult(List<CustomerEnterpriseInfo> items, int total) {}

    public EnterprisePageResult queryEnterprises(String status, int page, int size) {
        int offset = (page - 1) * size;
        List<CustomerEnterpriseInfo> items = repo.findEnterprisesByStatus(status, offset, size);
        int total = repo.countEnterprisesByStatus(status);
        return new EnterprisePageResult(items, total);
    }

    @Transactional
    public void updateEnterprise(Long id, String name, String contactName,
                                  String contactMobile, String remark) {
        getEnterprise(id); // 先校验存在
        repo.updateEnterprise(id, name, contactName, contactMobile,
                remark, OffsetDateTime.now());
    }

    @Transactional
    public Long createEmployee(Long enterpriseId, String name, String mobile, Long operatorId) {
        getEnterprise(enterpriseId); // 先校验企业存在
        return repo.insertEmployee(enterpriseId, name, mobile, operatorId, OffsetDateTime.now());
    }

    public record EmployeePageResult(List<CustomerEmployeeInfo> items, int total) {}

    public EmployeePageResult queryEmployees(Long enterpriseId, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<CustomerEmployeeInfo> items = repo.findEmployeesByEnterprise(
                enterpriseId, status, offset, size);
        int total = repo.countEmployeesByEnterprise(enterpriseId, status);
        return new EmployeePageResult(items, total);
    }

    @Transactional
    public void updateEmployee(Long id, String name, String mobile) {
        getEmployee(id); // 先校验存在
        repo.updateEmployee(id, name, mobile, OffsetDateTime.now());
    }
}
