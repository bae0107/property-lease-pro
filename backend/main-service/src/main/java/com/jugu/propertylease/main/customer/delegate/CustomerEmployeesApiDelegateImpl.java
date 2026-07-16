package com.jugu.propertylease.main.customer.delegate;

import com.jugu.propertylease.main.api.CustomerEmployeesApiDelegate;
import com.jugu.propertylease.main.api.model.CreateEmployeeRequest;
import com.jugu.propertylease.main.api.model.Employee;
import com.jugu.propertylease.main.api.model.EmployeePageResult;
import com.jugu.propertylease.main.api.model.EmployeeQueryRequest;
import com.jugu.propertylease.main.api.model.UpdateEmployeeRequest;
import com.jugu.propertylease.main.customer.api.model.CustomerEmployeeInfo;
import com.jugu.propertylease.main.customer.service.CustomerService;
import com.jugu.propertylease.security.service.AuthUserContext;
import org.springframework.stereotype.Service;

@Service
public class CustomerEmployeesApiDelegateImpl implements CustomerEmployeesApiDelegate {

    private final CustomerService customerService;

    public CustomerEmployeesApiDelegateImpl(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public Employee createEmployee(CreateEmployeeRequest request) {
        Long operatorId = AuthUserContext.currentUserId();
        Long id = customerService.createEmployee(
                request.getEnterpriseId(),
                request.getName(),
                request.getMobile(),
                operatorId
        );
        return toApiModel(customerService.getEmployee(id));
    }

    @Override
    public EmployeePageResult queryEmployees(EmployeeQueryRequest request) {
        String status = request.getStatus() != null ? request.getStatus().getValue() : null;
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 20;

        var result = customerService.queryEmployees(request.getEnterpriseId(), status, page, size);

        return new EmployeePageResult()
                .items(result.items().stream().map(this::toApiModel).toList())
                .total(result.total())
                .page(page)
                .size(size);
    }

    @Override
    public Employee getEmployee(Long id) {
        return toApiModel(customerService.getEmployee(id));
    }

    @Override
    public Employee updateEmployee(Long id, UpdateEmployeeRequest request) {
        customerService.updateEmployee(id, request.getName(), request.getMobile());
        return toApiModel(customerService.getEmployee(id));
    }

    private Employee toApiModel(CustomerEmployeeInfo info) {
        return new Employee()
                .id(info.id())
                .enterpriseId(info.enterpriseId())
                .name(info.name())
                .mobile(info.mobile())
                .status(Employee.StatusEnum.fromValue(info.status()));
    }
}
