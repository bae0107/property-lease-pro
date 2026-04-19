package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.main.api.IamApiDelegate;
import com.jugu.propertylease.main.api.model.PermissionPageResult;
import com.jugu.propertylease.main.api.model.RolePageResult;
import com.jugu.propertylease.main.api.model.UserPageResult;
import com.jugu.propertylease.main.iam.page.IamPageService;
import org.springframework.stereotype.Service;

@Service
public class IamApiDelegateImpl implements IamApiDelegate {

  private final IamPageService iamPageService;

  public IamApiDelegateImpl(
      IamPageService iamPageService) {
    this.iamPageService = iamPageService;
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
}
