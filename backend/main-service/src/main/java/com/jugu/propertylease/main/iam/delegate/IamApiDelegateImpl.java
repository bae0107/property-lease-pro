package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.main.api.IamApiDelegate;
import com.jugu.propertylease.main.api.model.DeleteUserRequest;
import com.jugu.propertylease.main.api.model.PermissionPageResult;
import com.jugu.propertylease.main.api.model.PatchUserRequest;
import com.jugu.propertylease.main.api.model.RolePageResult;
import com.jugu.propertylease.main.api.model.UserCreateFormMeta;
import com.jugu.propertylease.main.api.model.UserPageResult;
import com.jugu.propertylease.main.iam.page.IamPageService;
import com.jugu.propertylease.main.iam.service.UserFormMetaService;
import com.jugu.propertylease.main.iam.service.UserLifecycleService;
import org.springframework.stereotype.Service;

@Service
public class IamApiDelegateImpl implements IamApiDelegate {

  private final IamPageService iamPageService;
  private final UserFormMetaService userFormMetaService;
  private final UserLifecycleService userLifecycleService;

  public IamApiDelegateImpl(
      IamPageService iamPageService,
      UserFormMetaService userFormMetaService,
      UserLifecycleService userLifecycleService) {
    this.iamPageService = iamPageService;
    this.userFormMetaService = userFormMetaService;
    this.userLifecycleService = userLifecycleService;
  }

  @Override
  public ListViewMeta getUsersListViewMeta() {
    return iamPageService.getUsersListViewMeta();
  }

  @Override
  public UserPageResult queryUsers(PageRequest pageRequest) {
    return iamPageService.queryUsers(pageRequest);
  }

  @Override
  public ListViewMeta getRolesListViewMeta() {
    return iamPageService.getRolesListViewMeta();
  }

  @Override
  public RolePageResult queryRoles(PageRequest pageRequest) {
    return iamPageService.queryRoles(pageRequest);
  }

  @Override
  public ListViewMeta getPermissionsListViewMeta() {
    return iamPageService.getPermissionsListViewMeta();
  }

  @Override
  public PermissionPageResult queryPermissions(PageRequest pageRequest) {
    return iamPageService.queryPermissions(pageRequest);
  }

  public UserCreateFormMeta getUserCreateFormMeta() {
    return userFormMetaService.getCreateFormMeta();
  }

  public void deleteUser(Long id, DeleteUserRequest deleteUserRequest) {
    userLifecycleService.softDeleteUser(id, null,
        deleteUserRequest == null ? null : deleteUserRequest.getReason());
  }
}
