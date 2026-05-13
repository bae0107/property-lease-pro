package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.main.api.model.UpdateUserDataScopeRequest;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.iam.service.UserMutationService;
import com.jugu.propertylease.main.iam.service.UserReadService;
import org.springframework.stereotype.Service;

/**
 * IAM 对外接口实现（薄转接层）。
 *
 */
@Service
public class IamDataScopeApiDelegateImpl implements
    com.jugu.propertylease.main.api.IamDataScopeApiDelegate {

  private final UserMutationService userMutationService;
  private final UserReadService userReadService;

  public IamDataScopeApiDelegateImpl(

      UserMutationService userMutationService,
      UserReadService userReadService
  ) {
    this.userMutationService = userMutationService;
    this.userReadService = userReadService;
  }

  // ─── 数据权限 ───────────────────────────────────

  @Override
  public UserDataScope getUserDataScope(Long id) {
    return userReadService.getUserDataScope(id);
  }

  @Override
  public UserDataScope updateUserDataScope(Long id,
      UpdateUserDataScopeRequest updateUserDataScopeRequest) {
    return userMutationService.updateUserDataScope(id, updateUserDataScopeRequest);
  }


}
