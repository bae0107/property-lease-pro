package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.main.internal.api.InternalUsersApiDelegate;
import com.jugu.propertylease.main.internal.api.model.CreateTenantRequest;
import com.jugu.propertylease.main.internal.api.model.CreateTenantResult;
import com.jugu.propertylease.main.iam.service.InternalTenantUserService;
import org.springframework.stereotype.Service;

@Service
public class InternalUsersApiDelegateImpl implements InternalUsersApiDelegate {

  private final InternalTenantUserService internalTenantUserService;

  public InternalUsersApiDelegateImpl(InternalTenantUserService internalTenantUserService) {
    this.internalTenantUserService = internalTenantUserService;
  }

  @Override
  public CreateTenantResult createTenantUser(CreateTenantRequest createTenantRequest) {
    return internalTenantUserService.createTenantUser(createTenantRequest);
  }
}
