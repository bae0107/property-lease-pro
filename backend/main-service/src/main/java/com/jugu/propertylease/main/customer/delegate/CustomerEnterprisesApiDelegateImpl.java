package com.jugu.propertylease.main.customer.delegate;

import com.jugu.propertylease.main.api.CustomerEnterprisesApiDelegate;
import com.jugu.propertylease.main.api.model.CreateEnterpriseRequest;
import com.jugu.propertylease.main.api.model.Enterprise;
import com.jugu.propertylease.main.api.model.EnterprisePageResult;
import com.jugu.propertylease.main.api.model.EnterpriseQueryRequest;
import com.jugu.propertylease.main.api.model.UpdateEnterpriseRequest;
import com.jugu.propertylease.main.customer.api.model.CustomerEnterpriseInfo;
import com.jugu.propertylease.main.customer.service.CustomerService;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerEnterprisesApiDelegateImpl implements CustomerEnterprisesApiDelegate {

    private final CustomerService customerService;

    public CustomerEnterprisesApiDelegateImpl(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public Enterprise createEnterprise(CreateEnterpriseRequest request) {
        Long operatorId = CurrentUser.getCurrentUserId();
        Long id = customerService.createEnterprise(
                request.getName(),
                request.getContactName(),
                request.getContactMobile(),
                request.getRemark(),
                operatorId
        );
        return toApiModel(customerService.getEnterprise(id));
    }

    @Override
    public EnterprisePageResult queryEnterprises(EnterpriseQueryRequest request) {
        String status = request.getStatus() != null ? request.getStatus().getValue() : null;
        int page = request.getPageNo() != null ? request.getPageNo() : 1;
        int size = request.getPageSize() != null ? request.getPageSize() : 20;

        var result = customerService.queryEnterprises(status, page, size);

        return new EnterprisePageResult()
                .items(result.items().stream().map(this::toApiModel).toList())
                .total((long) result.total())
                .pageNo(page)
                .pageSize(size);
    }

    @Override
    public Enterprise getEnterprise(Long id) {
        return toApiModel(customerService.getEnterprise(id));
    }

    @Override
    public Enterprise updateEnterprise(Long id, UpdateEnterpriseRequest request) {
        customerService.updateEnterprise(
                id,
                request.getName(),
                request.getContactName(),
                request.getContactMobile(),
                request.getRemark()
        );
        return toApiModel(customerService.getEnterprise(id));
    }

    private Enterprise toApiModel(CustomerEnterpriseInfo info) {
        return new Enterprise()
                .id(info.id())
                .name(info.name())
                .status(Enterprise.StatusEnum.fromValue(info.status()));
    }
}
