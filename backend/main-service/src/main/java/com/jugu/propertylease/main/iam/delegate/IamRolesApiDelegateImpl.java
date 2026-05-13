package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.common.model.BatchRequest;
import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.main.api.model.CreateRoleRequest;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RoleDetail;
import com.jugu.propertylease.main.api.model.RolePageResult;
import com.jugu.propertylease.main.api.model.UpdateRolePermissionsRequest;
import com.jugu.propertylease.main.api.model.UpdateRoleRequest;
import com.jugu.propertylease.main.iam.page.IamPageService;
import com.jugu.propertylease.main.iam.service.RoleManagementService;
import org.springframework.stereotype.Service;

/**
 * IAM 对外接口实现（薄转接层）。
 *
 */
@Service
public class IamRolesApiDelegateImpl implements
    com.jugu.propertylease.main.api.IamRolesApiDelegate {

  private final IamPageService iamPageService;
  private final RoleManagementService roleManagementService;

  public IamRolesApiDelegateImpl(
      IamPageService iamPageService,
      RoleManagementService roleManagementService) {
    this.iamPageService = iamPageService;
    this.roleManagementService = roleManagementService;
  }

  // ─── 分页查询 ───────────────────────────────────


  @Override
  public ListViewMeta getRolesListViewMeta() {
    return iamPageService.getRolesListViewMeta();
  }

  @Override
  public RolePageResult queryRoles(PageRequest pageRequest) {
    return iamPageService.queryRoles(pageRequest);
  }

  // ─── 角色管理 ───────────────────────────────────

  @Override
  public Role createRole(CreateRoleRequest createRoleRequest) {
    return roleManagementService.createRole(createRoleRequest);
  }

  @Override
  public RoleDetail getRole(Long id) {
    return roleManagementService.getRoleDetail(id);
  }

  @Override
  public Role updateRole(Long id, UpdateRoleRequest updateRoleRequest) {
    return roleManagementService.updateRole(id, updateRoleRequest);
  }

  @Override
  public void batchDeleteRoles(BatchRequest batchRequest) {
    roleManagementService.batchDeleteRoles(batchRequest);
  }

  @Override
  public RoleDetail updateRolePermissions(Long id,
      UpdateRolePermissionsRequest updateRolePermissionsRequest) {
    return roleManagementService.updateRolePermissions(id, updateRolePermissionsRequest);
  }
}
