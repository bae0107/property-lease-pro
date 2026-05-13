package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.main.api.model.BatchUpdateUserStatusRequest;
import com.jugu.propertylease.main.api.model.CreateUserRequest;
import com.jugu.propertylease.main.api.model.DeleteUserRequest;
import com.jugu.propertylease.main.api.model.PatchUserRequest;
import com.jugu.propertylease.main.api.model.ResetUserPasswordRequest;
import com.jugu.propertylease.main.api.model.UpdateUserRolesRequest;
import com.jugu.propertylease.main.api.model.UpdateUserStatusRequest;
import com.jugu.propertylease.main.api.model.UserCreateFormMeta;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.api.model.UserPageResult;
import com.jugu.propertylease.main.iam.page.IamPageService;
import com.jugu.propertylease.main.iam.service.UserFormMetaService;
import com.jugu.propertylease.main.iam.service.UserLifecycleService;
import com.jugu.propertylease.main.iam.service.UserMutationService;
import com.jugu.propertylease.main.iam.service.UserReadService;
import com.jugu.propertylease.security.context.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * IAM 对外接口实现（薄转接层）。
 *
 * <p>不含任何业务逻辑，只做参数透传和 Service 路由。
 *
 * <p>注意：原 {@code patchUser} 已拆分为独立接口：
 * <p>
 * 如果 OpenAPI yaml 中仍存在 {@code patchUser} 端点，应在下一次 yaml 迭代中移除。
 */
@Service
public class IamUsersApiDelegateImpl implements
    com.jugu.propertylease.main.api.IamUsersApiDelegate {

  private final IamPageService iamPageService;
  private final UserFormMetaService userFormMetaService;
  private final UserLifecycleService userLifecycleService;
  private final UserMutationService userMutationService;
  private final UserReadService userReadService;

  public IamUsersApiDelegateImpl(
      IamPageService iamPageService,
      UserFormMetaService userFormMetaService,
      UserLifecycleService userLifecycleService,
      UserMutationService userMutationService,
      UserReadService userReadService
  ) {
    this.iamPageService = iamPageService;
    this.userFormMetaService = userFormMetaService;
    this.userLifecycleService = userLifecycleService;
    this.userMutationService = userMutationService;
    this.userReadService = userReadService;
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

  // ─── 用户表单元数据 ─────────────────────────────

  @Override
  public UserCreateFormMeta getUserCreateFormMeta() {
    return userFormMetaService.getCreateFormMeta();
  }

  // ─── 用户 CRUD ──────────────────────────────────

  @Override
  public UserDetail patchUser(Long id, PatchUserRequest patchUserRequest) {
    throw new BusinessException(HttpStatus.NOT_IMPLEMENTED, "Not_impl_why_you_need", "");
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


}
