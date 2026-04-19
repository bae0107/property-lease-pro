package com.jugu.propertylease.main.iam.page;

import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.common.pagination.core.PageSlice;
import com.jugu.propertylease.common.pagination.jooq.JooqPageQueryExecutor;
import com.jugu.propertylease.main.api.model.Permission;
import com.jugu.propertylease.main.api.model.PermissionPageResult;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RolePageResult;
import com.jugu.propertylease.main.api.model.User;
import com.jugu.propertylease.main.api.model.UserPageResult;
import org.springframework.stereotype.Service;

/**
 * 统一处理iam模块的user|role|permission几个分页表查询
 */
@Service
public final class IamPageService {

  private final IamUsersPageResource users;

  private final IamRolesPageResource roles;

  private final IamPermissionsPageResource permissions;

  private final JooqPageQueryExecutor executor;

  public IamPageService(IamUsersPageResource users, IamRolesPageResource roles,
      IamPermissionsPageResource permissions, JooqPageQueryExecutor executor) {
    this.users = users;
    this.roles = roles;
    this.permissions = permissions;
    this.executor = executor;
  }


  public ListViewMeta getUsersListViewMeta() {
    return users.listViewMeta();
  }

  public UserPageResult queryUsers(PageRequest request) {
    return toUserPageResult(executor.executeTyped(users, request));
  }

  private UserPageResult toUserPageResult(PageSlice<User> slice) {
    return new UserPageResult().pageNo(slice.pageNo()).pageSize(slice.pageSize())
        .total(slice.total())
        .items(slice.items());
  }

  public ListViewMeta getRolesListViewMeta() {
    return roles.listViewMeta();
  }

  public RolePageResult queryRoles(PageRequest request) {
    return toRolePageResult(executor.executeTyped(roles, request));
  }

  private RolePageResult toRolePageResult(PageSlice<Role> slice) {
    return new RolePageResult().pageNo(slice.pageNo()).pageSize(slice.pageSize())
        .total(slice.total())
        .items(slice.items());
  }

  public ListViewMeta getPermissionsListViewMeta() {
    return permissions.listViewMeta();
  }

  public PermissionPageResult queryPermissions(PageRequest request) {
    return toPermissionPageResult(executor.executeTyped(permissions, request));
  }

  private PermissionPageResult toPermissionPageResult(PageSlice<Permission> slice) {
    return new PermissionPageResult().pageNo(slice.pageNo()).pageSize(slice.pageSize())
        .total(slice.total())
        .items(slice.items());
  }

}
