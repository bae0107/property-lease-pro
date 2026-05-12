package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.common.model.BatchRequest;
import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.main.api.IamApiDelegate;
import com.jugu.propertylease.main.api.model.BatchUpdateUserStatusRequest;
import com.jugu.propertylease.main.api.model.CreateRoleRequest;
import com.jugu.propertylease.main.api.model.CreateUserRequest;
import com.jugu.propertylease.main.api.model.DeleteUserRequest;
import com.jugu.propertylease.main.api.model.PatchUserRequest;
import com.jugu.propertylease.main.api.model.PermissionPageResult;
import com.jugu.propertylease.main.api.model.ResetUserPasswordRequest;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RoleDetail;
import com.jugu.propertylease.main.api.model.RolePageResult;
import com.jugu.propertylease.main.api.model.UpdateRolePermissionsRequest;
import com.jugu.propertylease.main.api.model.UpdateRoleRequest;
import com.jugu.propertylease.main.api.model.UpdateUserDataScopeRequest;
import com.jugu.propertylease.main.api.model.UpdateUserRolesRequest;
import com.jugu.propertylease.main.api.model.UpdateUserStatusRequest;
import com.jugu.propertylease.main.api.model.UserCreateFormMeta;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.api.model.UserPageResult;
import com.jugu.propertylease.main.iam.page.IamPageService;
import com.jugu.propertylease.main.iam.service.RoleManagementService;
import com.jugu.propertylease.main.iam.service.UserFormMetaService;
import com.jugu.propertylease.main.iam.service.UserLifecycleService;
import com.jugu.propertylease.main.iam.service.UserMutationService;
import com.jugu.propertylease.main.iam.service.UserReadService;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.stereotype.Service;

/**
 * IAM 对外接口实现（薄转接层）。
 *
 * <p>不含任何业务逻辑，只做参数透传和 Service 路由。
 *
 * <p>注意：原 {@code patchUser} 已拆分为独立接口：
 * <ul>
 *   <li>角色更新 → {@link #updateUserRoles}</li>
 *   <li>数据权限更新 → {@link #updateUserDataScope}</li>
 * </ul>
 * 如果 OpenAPI yaml 中仍存在 {@code patchUser} 端点，应在下一次 yaml 迭代中移除。
 */
@Service
public class IamApiDelegateImpl implements IamApiDelegate {

    private final IamPageService iamPageService;
    private final UserFormMetaService userFormMetaService;
    private final UserLifecycleService userLifecycleService;
    private final UserMutationService userMutationService;
    private final UserReadService userReadService;
    private final RoleManagementService roleManagementService;

    public IamApiDelegateImpl(
            IamPageService iamPageService,
            UserFormMetaService userFormMetaService,
            UserLifecycleService userLifecycleService,
            UserMutationService userMutationService,
            UserReadService userReadService,
            RoleManagementService roleManagementService) {
        this.iamPageService = iamPageService;
        this.userFormMetaService = userFormMetaService;
        this.userLifecycleService = userLifecycleService;
        this.userMutationService = userMutationService;
        this.userReadService = userReadService;
        this.roleManagementService = roleManagementService;
    }

    // ─── 分页查询 ───────────────────────────────────

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

    // ─── 用户表单元数据 ─────────────────────────────

    @Override
    public UserCreateFormMeta getUserCreateFormMeta() {
        return userFormMetaService.getCreateFormMeta();
    }

    // ─── 用户 CRUD ──────────────────────────────────

  @Override
  public UserDetail patchUser(Long id, PatchUserRequest patchUserRequest) {
    return IamApiDelegate.super.patchUser(id, patchUserRequest);
  }

  @Override
    public UserDetail createUser(CreateUserRequest createUserRequest) {
        return userMutationService.createUser(createUserRequest);
    }

    @Override
    public UserDetail getUser(Long id) {
        return userReadService.getUserDetail(id);
    }

    @Override
    public void deleteUser(Long id, DeleteUserRequest deleteUserRequest) {
        userLifecycleService.softDeleteUser(
                id,
                CurrentUser.getCurrentUserId(),
                deleteUserRequest == null ? null : deleteUserRequest.getReason());
    }

    @Override
    public void updateUserStatus(Long id, UpdateUserStatusRequest updateUserStatusRequest) {
        userMutationService.updateUserStatus(id, updateUserStatusRequest);
    }

    @Override
    public void batchUpdateUserStatus(BatchUpdateUserStatusRequest batchUpdateUserStatusRequest) {
        userMutationService.batchUpdateUserStatus(batchUpdateUserStatusRequest);
    }

    @Override
    public void resetUserPassword(Long id, ResetUserPasswordRequest resetUserPasswordRequest) {
        userMutationService.resetUserPassword(id, resetUserPasswordRequest);
    }

    @Override
    public UserDetail updateUserRoles(Long id, UpdateUserRolesRequest updateUserRolesRequest) {
        return userMutationService.updateUserRoles(id, updateUserRolesRequest);
    }

    // ─── 数据权限 ───────────────────────────────────

    @Override
    public UserDataScope getUserDataScope(Long id) {
        // 原实现错误：路由到了 userMutationService，读操作应走 userReadService
        return userReadService.getUserDataScope(id);
    }

    @Override
    public UserDataScope updateUserDataScope(Long id,
            UpdateUserDataScopeRequest updateUserDataScopeRequest) {
        return userMutationService.updateUserDataScope(id, updateUserDataScopeRequest);
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
