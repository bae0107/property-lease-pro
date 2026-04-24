package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.BatchUpdateUserStatusRequest;
import com.jugu.propertylease.main.api.model.CreateUserRequest;
import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.PatchUserRequest;
import com.jugu.propertylease.main.api.model.ResetUserPasswordRequest;
import com.jugu.propertylease.main.api.model.UpdateUserDataScopeRequest;
import com.jugu.propertylease.main.api.model.UpdateUserRolesRequest;
import com.jugu.propertylease.main.api.model.UpdateUserStatusRequest;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import com.jugu.propertylease.main.iam.repo.IamUserMutationRepository;
import com.jugu.propertylease.main.iam.repo.model.UserBaseInfo;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户修改聚合服务。
 */
@Service
public class UserMutationService {

  private final IamUserMutationRepository userMutationRepository;
  private final AuthVersionService authVersionService;
  private final UserReadService userReadService;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public UserMutationService(IamUserMutationRepository userMutationRepository,
      AuthVersionService authVersionService, UserReadService userReadService) {
    this.userMutationRepository = userMutationRepository;
    this.authVersionService = authVersionService;
    this.userReadService = userReadService;
  }

  @Transactional
  public UserDetail patchUser(Long userId, PatchUserRequest request) {
    if (request == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_PATCH_REQUEST_REQUIRED",
          "请求体不能为空");
    }
    UserBaseInfo userBase = userMutationRepository.findActiveUserBase(userId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在"));

    if (!"STAFF".equals(userBase.userType()) && !"CONTRACTOR".equals(userBase.userType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_PATCH_TYPE_UNSUPPORTED",
          "当前接口仅支持 STAFF / CONTRACTOR 用户");
    }
    if ("BUILTIN".equals(userBase.sourceType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_PATCH_BUILTIN_FORBIDDEN",
          "BUILTIN 用户不允许修改");
    }

    if (!userMutationRepository.existsActiveUser(userId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在");
    }

    OffsetDateTime now = OffsetDateTime.now();
    boolean shouldBumpAuthVersion = false;

    if (request.getProfile() != null) {
      userMutationRepository.updateUserProfile(userId, request.getProfile().getRealName(),
          request.getProfile().getMobile(), request.getProfile().getEmail(), now);
    }

    if (request.getStatus() != null) {
      userMutationRepository.updateUserStatus(userId, request.getStatus().getValue(), now);
      shouldBumpAuthVersion = true;
    }

    if (request.getPassword() != null) {
      String hash = passwordEncoder.encode(request.getPassword());
      if (userMutationRepository.credentialExists(userId)) {
        userMutationRepository.updateCredential(userId, hash, now);
      } else {
        userMutationRepository.insertCredential(userId, hash, now);
      }
      shouldBumpAuthVersion = true;
    }

    if (request.getRoleIds() != null) {
      List<Long> normalizedRoleIds = normalizeRoleIds(request.getRoleIds());
      List<com.jugu.propertylease.main.jooq.tables.pojos.IamRole> roleRows =
          userMutationRepository.findRolesByIds(normalizedRoleIds);
      if (roleRows.size() != normalizedRoleIds.size()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_NOT_FOUND",
            "所选角色包含无效 ID");
      }
      boolean hasMismatchRole = roleRows.stream()
          .anyMatch(role -> !userBase.userType().equals(role.getRoleType()));
      if (hasMismatchRole) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_TYPE_MISMATCH",
            "所选角色必须全部与用户类型一致");
      }
      userMutationRepository.replaceUserRoles(userId, normalizedRoleIds, now);
      shouldBumpAuthVersion = true;
    }

    if (request.getRoleIds() != null || request.getScopes() != null) {
      List<Long> effectiveRoleIds =
          request.getRoleIds() != null
              ? normalizeRoleIds(request.getRoleIds())
              : userMutationRepository.findRoleIdsByUserId(userId);
      validateScopeDimensionsAgainstRoles(effectiveRoleIds, request.getScopes());
    }

    if (request.getScopes() != null) {
      userMutationRepository.clearUserDataScopes(userId);
      List<DataScopeItem> items = new ArrayList<>(request.getScopes());
      for (DataScopeItem item : items) {
        if (item.getScopeType() == com.jugu.propertylease.main.api.model.DataScopeType.ALL) {
          userMutationRepository.insertAllDataScope(userId, item.getDimension().getValue(), now);
        } else {
          if (item.getResourceIds() == null || item.getResourceIds().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_SCOPE_RESOURCE_REQUIRED",
                "scopeType=SPECIFIC 时 resourceIds 必填");
          }
          for (Long resourceId : item.getResourceIds()) {
            userMutationRepository.insertSpecificDataScope(userId, item.getDimension().getValue(),
                resourceId, now);
          }
        }
      }
      shouldBumpAuthVersion = true;
    }

    if (shouldBumpAuthVersion) {
      authVersionService.bumpAuthVersion(userId, "PATCH_USER");
    }
    return userReadService.getUserDetail(userId);
  }

  @Transactional
  public UserDetail createUser(CreateUserRequest request) {
    if (request == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_CREATE_REQUEST_REQUIRED", "请求体不能为空");
    }
    String userType = request.getUserType() == null ? null : request.getUserType().getValue();
    if (!"STAFF".equals(userType) && !"CONTRACTOR".equals(userType)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_CREATE_TYPE_UNSUPPORTED",
          "仅支持创建 STAFF / CONTRACTOR 用户");
    }
    if (request.getRoleIds() == null || request.getRoleIds().isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_IDS_EMPTY", "角色列表不能为空");
    }

    OffsetDateTime now = OffsetDateTime.now();
    Long userId = userMutationRepository.insertUser(userType, request.getUsername(), request.getRealName(),
        request.getMobile(), request.getEmail(), now);

    String hash = passwordEncoder.encode(request.getPassword());
    userMutationRepository.insertCredential(userId, hash, now);

    userMutationRepository.insertPasswordIdentity(userId, request.getUsername(), now);

    PatchUserRequest patch = new PatchUserRequest()
        .roleIds(new ArrayList<>(request.getRoleIds()))
        .scopes(request.getScopes() == null ? null : new ArrayList<>(request.getScopes()));
    return patchUser(userId, patch);
  }

  @Transactional
  public UserDetail updateUserStatus(Long userId, UpdateUserStatusRequest request) {
    PatchUserRequest patch = new PatchUserRequest().status(request == null ? null : request.getStatus());
    return patchUser(userId, patch);
  }

  @Transactional
  public UserDetail resetUserPassword(Long userId, ResetUserPasswordRequest request) {
    PatchUserRequest patch = new PatchUserRequest().password(request == null ? null : request.getPassword());
    return patchUser(userId, patch);
  }

  @Transactional
  public void batchUpdateUserStatus(BatchUpdateUserStatusRequest request) {
    if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_STATUS_IDS_REQUIRED", "ids 不能为空");
    }
    for (Long userId : request.getIds()) {
      updateUserStatus(userId, new UpdateUserStatusRequest().status(request.getStatus()));
    }
  }

  @Transactional
  public UserDetail updateUserRoles(Long userId, UpdateUserRolesRequest request) {
    PatchUserRequest patch = new PatchUserRequest().roleIds(request == null ? null : request.getRoleIds());
    return patchUser(userId, patch);
  }

  @Transactional(readOnly = true)
  public UserDataScope getUserDataScope(Long userId) {
    UserDetail userDetail = userReadService.getUserDetail(userId);
    return userDetail.getDataScope() == null ? new UserDataScope().scopes(List.of()) : userDetail.getDataScope();
  }

  @Transactional
  public UserDataScope updateUserDataScope(Long userId, UpdateUserDataScopeRequest request) {
    List<Long> roleIds = userMutationRepository.findRoleIdsByUserId(userId);
    PatchUserRequest patch = new PatchUserRequest()
        .roleIds(roleIds)
        .scopes(request == null ? null : request.getScopes());
    UserDetail userDetail = patchUser(userId, patch);
    return userDetail.getDataScope() == null ? new UserDataScope().scopes(List.of()) : userDetail.getDataScope();
  }

  private List<Long> normalizeRoleIds(List<Long> roleIds) {
    if (roleIds == null || roleIds.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_IDS_EMPTY",
          "角色列表不能为空");
    }
    List<Long> normalized = new ArrayList<>(new LinkedHashSet<>(roleIds));
    if (normalized.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_ID_INVALID",
          "角色 ID 必须为正整数");
    }
    return normalized;
  }

  private void validateScopeDimensionsAgainstRoles(List<Long> roleIds, List<DataScopeItem> scopes) {
    if (roleIds == null || roleIds.isEmpty()) {
      return;
    }
    Set<String> requiredDimensions = userMutationRepository.findRequiredScopeDimensionsByRoleIds(roleIds);

    if (requiredDimensions.isEmpty()) {
      return;
    }
    if (scopes == null || scopes.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_SCOPE_REQUIRED",
          "所选角色要求配置数据权限");
    }
    Set<String> payloadDimensions = new LinkedHashSet<>();
    for (DataScopeItem item : scopes) {
      payloadDimensions.add(item.getDimension().getValue());
    }
    if (!payloadDimensions.equals(requiredDimensions)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_SCOPE_DIMENSION_MISMATCH",
          "数据权限维度必须与角色要求一致");
    }
  }
}
