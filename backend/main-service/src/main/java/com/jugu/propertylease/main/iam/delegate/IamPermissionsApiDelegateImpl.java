package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.main.api.model.PermissionPageResult;
import com.jugu.propertylease.main.iam.page.IamPageService;
import org.springframework.stereotype.Service;

/**
 * IAM 对外接口实现（薄转接层）。
 *
 */
@Service
public class IamPermissionsApiDelegateImpl implements
    com.jugu.propertylease.main.api.IamPermissionsApiDelegate {

  private final IamPageService iamPageService;

  public IamPermissionsApiDelegateImpl(IamPageService iamPageService) {
    this.iamPageService = iamPageService;

  }

  // ─── 分页查询 ───────────────────────────────────


  @Override
  public ListViewMeta getPermissionsListViewMeta() {
    return iamPageService.getPermissionsListViewMeta();
  }

  @Override
  public PermissionPageResult queryPermissions(PageRequest pageRequest) {
    return iamPageService.queryPermissions(pageRequest);
  }


}
