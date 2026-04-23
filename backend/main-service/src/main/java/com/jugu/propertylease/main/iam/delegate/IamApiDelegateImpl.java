package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.common.model.BatchRequest;
import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.main.api.IamApiDelegate;
import com.jugu.propertylease.main.api.model.DeleteUserRequest;
import com.jugu.propertylease.main.api.model.LogoutRequest;
import com.jugu.propertylease.main.api.model.LoginResult;
import com.jugu.propertylease.main.api.model.PasswordLoginRequest;
import com.jugu.propertylease.main.api.model.RefreshResult;
import com.jugu.propertylease.main.api.model.RefreshTokenRequest;
import com.jugu.propertylease.main.api.model.UpdateRoleRequest;
import com.jugu.propertylease.main.api.model.UpdateRolePermissionsRequest;
import com.jugu.propertylease.main.api.model.RoleDetail;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.CreateRoleRequest;
import com.jugu.propertylease.main.api.model.PermissionPageResult;
import com.jugu.propertylease.main.api.model.PatchUserRequest;
import com.jugu.propertylease.main.api.model.RolePageResult;
import com.jugu.propertylease.main.api.model.UserCreateFormMeta;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.api.model.UserPageResult;
import com.jugu.propertylease.main.iam.auth.AuthSessionService;
import com.jugu.propertylease.main.iam.auth.PasswordLoginService;
import com.jugu.propertylease.main.iam.page.IamPageService;
import com.jugu.propertylease.main.iam.service.RoleManagementService;
import com.jugu.propertylease.main.iam.service.UserFormMetaService;
import com.jugu.propertylease.main.iam.service.UserLifecycleService;
import com.jugu.propertylease.main.iam.service.UserMutationService;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class IamApiDelegateImpl implements IamApiDelegate {

  private final IamPageService iamPageService;
  private final PasswordLoginService passwordLoginService;
  private final AuthSessionService authSessionService;
  private final UserFormMetaService userFormMetaService;
  private final UserLifecycleService userLifecycleService;
  private final UserMutationService userMutationService;
  private final RoleManagementService roleManagementService;

  public IamApiDelegateImpl(
      IamPageService iamPageService,
      PasswordLoginService passwordLoginService,
      AuthSessionService authSessionService,
      UserFormMetaService userFormMetaService,
      UserLifecycleService userLifecycleService,
      UserMutationService userMutationService,
      RoleManagementService roleManagementService) {
    this.iamPageService = iamPageService;
    this.passwordLoginService = passwordLoginService;
    this.authSessionService = authSessionService;
    this.userFormMetaService = userFormMetaService;
    this.userLifecycleService = userLifecycleService;
    this.userMutationService = userMutationService;
    this.roleManagementService = roleManagementService;
  }

  public LoginResult passwordLogin(PasswordLoginRequest passwordLoginRequest) {
    return passwordLoginService.login(passwordLoginRequest);
  }

  public RefreshResult refreshToken(RefreshTokenRequest refreshTokenRequest) {
    return authSessionService.refresh(refreshTokenRequest);
  }

  public void logout(LogoutRequest logoutRequest) {
    authSessionService.logout(logoutRequest);
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

  @Override
  public UserCreateFormMeta getUserCreateFormMeta() {
    return userFormMetaService.getCreateFormMeta();
  }

  @Override
  public void deleteUser(Long id, DeleteUserRequest deleteUserRequest) {
    userLifecycleService.softDeleteUser(id, CurrentUser.getCurrentUserId(),
        deleteUserRequest == null ? null : deleteUserRequest.getReason());
  }

  @Override
  public UserDetail patchUser(Long id, PatchUserRequest patchUserRequest) {
    return userMutationService.patchUser(id, patchUserRequest);
  }

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
