package com.jugu.propertylease.main.iam.service;

import static com.jugu.propertylease.main.jooq.Tables.IAM_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record2;
<<<<<<< codex/task-title-7hnlvk
import org.jooq.Record3;
=======
>>>>>>> master
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户修改聚合服务。
 */
@Service
public class UserMutationService {

  private final DSLContext dsl;
  private final AuthVersionService authVersionService;
  private final UserReadService userReadService;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public UserMutationService(DSLContext dsl, AuthVersionService authVersionService,
      UserReadService userReadService) {
    this.dsl = dsl;
    this.authVersionService = authVersionService;
    this.userReadService = userReadService;
  }

  @Transactional
  public UserDetail patchUser(Long userId, PatchUserRequest request) {
    if (request == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_PATCH_REQUEST_REQUIRED",
          "请求体不能为空");
    }
    Record2<String, String> userBase = dsl.select(IAM_USER.USER_TYPE, IAM_USER.SOURCE_TYPE)
        .from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne();
    if (userBase == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在");
    }
    if (!"STAFF".equals(userBase.value1()) && !"CONTRACTOR".equals(userBase.value1())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_PATCH_TYPE_UNSUPPORTED",
          "当前接口仅支持 STAFF / CONTRACTOR 用户");
    }
    if ("BUILTIN".equals(userBase.value2())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_PATCH_BUILTIN_FORBIDDEN",
          "BUILTIN 用户不允许修改");
    }

    if (dsl.fetchExists(dsl.selectOne().from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())) == false) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在");
    }

    OffsetDateTime now = OffsetDateTime.now();
    boolean shouldBumpAuthVersion = false;

    if (request.getProfile() != null) {
      dsl.update(IAM_USER)
          .set(IAM_USER.REAL_NAME, request.getProfile().getRealName())
          .set(IAM_USER.MOBILE, request.getProfile().getMobile())
          .set(IAM_USER.EMAIL, request.getProfile().getEmail())
          .set(IAM_USER.UPDATED_AT, now)
          .where(IAM_USER.ID.eq(userId))
          .and(IAM_USER.DELETED_AT.isNull())
          .execute();
    }

    if (request.getStatus() != null) {
      dsl.update(IAM_USER)
          .set(IAM_USER.STATUS, request.getStatus().getValue())
          .set(IAM_USER.UPDATED_AT, now)
          .where(IAM_USER.ID.eq(userId))
          .and(IAM_USER.DELETED_AT.isNull())
          .execute();
      shouldBumpAuthVersion = true;
    }

    if (request.getPassword() != null) {
      String hash = passwordEncoder.encode(request.getPassword());
      boolean credentialExists = dsl.fetchExists(
          dsl.selectOne().from(IAM_CREDENTIAL).where(IAM_CREDENTIAL.USER_ID.eq(userId)));
      if (credentialExists) {
        dsl.update(IAM_CREDENTIAL)
            .set(IAM_CREDENTIAL.PASSWORD_HASH, hash)
            .set(IAM_CREDENTIAL.UPDATED_AT, now)
            .where(IAM_CREDENTIAL.USER_ID.eq(userId))
            .execute();
      } else {
        dsl.insertInto(IAM_CREDENTIAL)
            .set(IAM_CREDENTIAL.USER_ID, userId)
            .set(IAM_CREDENTIAL.PASSWORD_HASH, hash)
            .set(IAM_CREDENTIAL.CREATED_AT, now)
            .set(IAM_CREDENTIAL.UPDATED_AT, now)
            .execute();
      }
      shouldBumpAuthVersion = true;
    }

    if (request.getRoleIds() != null) {
      List<Long> normalizedRoleIds = normalizeRoleIds(request.getRoleIds());
      List<Record3<Long, String, String>> roleRows = dsl.select(IAM_ROLE.ID, IAM_ROLE.ROLE_TYPE,
              IAM_ROLE.NAME)
          .from(IAM_ROLE)
          .where(IAM_ROLE.ID.in(normalizedRoleIds))
          .fetch();
      if (roleRows.size() != normalizedRoleIds.size()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_NOT_FOUND",
            "所选角色包含无效 ID");
      }
      boolean hasMismatchRole = roleRows.stream()
          .anyMatch(role -> !userBase.value1().equals(role.value2()));
      if (hasMismatchRole) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_TYPE_MISMATCH",
            "所选角色必须全部与用户类型一致");
      }
      dsl.deleteFrom(IAM_USER_ROLE).where(IAM_USER_ROLE.USER_ID.eq(userId)).execute();
      for (Long roleId : normalizedRoleIds) {
        dsl.insertInto(IAM_USER_ROLE)
            .set(IAM_USER_ROLE.USER_ID, userId)
            .set(IAM_USER_ROLE.ROLE_ID, roleId)
            .set(IAM_USER_ROLE.CREATED_AT, now)
            .execute();
      }
      shouldBumpAuthVersion = true;
    }

    if (request.getRoleIds() != null || request.getScopes() != null) {
      List<Long> effectiveRoleIds =
          request.getRoleIds() != null
              ? normalizeRoleIds(request.getRoleIds())
              : dsl.select(IAM_USER_ROLE.ROLE_ID)
                  .from(IAM_USER_ROLE)
                  .where(IAM_USER_ROLE.USER_ID.eq(userId))
                  .fetch(IAM_USER_ROLE.ROLE_ID);
      validateScopeDimensionsAgainstRoles(effectiveRoleIds, request.getScopes());
    }

    if (request.getScopes() != null) {
      dsl.deleteFrom(IAM_USER_DATA_SCOPE).where(IAM_USER_DATA_SCOPE.USER_ID.eq(userId)).execute();
      List<DataScopeItem> items = new ArrayList<>(request.getScopes());
      for (DataScopeItem item : items) {
        if (item.getScopeType() == com.jugu.propertylease.main.api.model.DataScopeType.ALL) {
          dsl.insertInto(IAM_USER_DATA_SCOPE)
              .set(IAM_USER_DATA_SCOPE.USER_ID, userId)
              .set(IAM_USER_DATA_SCOPE.SCOPE_DIMENSION, item.getDimension().getValue())
              .set(IAM_USER_DATA_SCOPE.SCOPE_TYPE, "ALL")
              .set(IAM_USER_DATA_SCOPE.RESOURCE_ID, (Long) null)
              .set(IAM_USER_DATA_SCOPE.CREATED_AT, now)
              .execute();
        } else {
          if (item.getResourceIds() == null || item.getResourceIds().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_SCOPE_RESOURCE_REQUIRED",
                "scopeType=SPECIFIC 时 resourceIds 必填");
          }
          for (Long resourceId : item.getResourceIds()) {
            dsl.insertInto(IAM_USER_DATA_SCOPE)
                .set(IAM_USER_DATA_SCOPE.USER_ID, userId)
                .set(IAM_USER_DATA_SCOPE.SCOPE_DIMENSION, item.getDimension().getValue())
                .set(IAM_USER_DATA_SCOPE.SCOPE_TYPE, "SPECIFIC")
                .set(IAM_USER_DATA_SCOPE.RESOURCE_ID, resourceId)
                .set(IAM_USER_DATA_SCOPE.CREATED_AT, now)
                .execute();
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
    Long userId = dsl.insertInto(IAM_USER)
        .set(IAM_USER.USER_TYPE, userType)
        .set(IAM_USER.SOURCE_TYPE, "CUSTOM")
        .set(IAM_USER.STATUS, "ACTIVE")
        .set(IAM_USER.AUTH_VERSION, 0)
        .set(IAM_USER.USER_NAME, request.getUsername())
        .set(IAM_USER.REAL_NAME, request.getRealName())
        .set(IAM_USER.MOBILE, request.getMobile())
        .set(IAM_USER.EMAIL, request.getEmail())
        .set(IAM_USER.SOURCE, "MANUAL")
        .set(IAM_USER.CREATED_BY, (Long) null)
        .set(IAM_USER.CREATED_AT, now)
        .set(IAM_USER.UPDATED_AT, now)
        .returning(IAM_USER.ID)
        .fetchOne(IAM_USER.ID);

    String hash = passwordEncoder.encode(request.getPassword());
    dsl.insertInto(IAM_CREDENTIAL)
        .set(IAM_CREDENTIAL.USER_ID, userId)
        .set(IAM_CREDENTIAL.PASSWORD_HASH, hash)
        .set(IAM_CREDENTIAL.CREATED_AT, now)
        .set(IAM_CREDENTIAL.UPDATED_AT, now)
        .execute();

    dsl.insertInto(IAM_IDENTITY)
        .set(IAM_IDENTITY.USER_ID, userId)
        .set(IAM_IDENTITY.PROVIDER, "password")
        .set(IAM_IDENTITY.PROVIDER_USER_ID, request.getUsername())
        .set(IAM_IDENTITY.UNION_ID, (String) null)
        .set(IAM_IDENTITY.APP_ID, (String) null)
        .set(IAM_IDENTITY.CREATED_AT, now)
        .set(IAM_IDENTITY.DELETED_AT, (OffsetDateTime) null)
        .execute();

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
    List<Long> roleIds = dsl.select(IAM_USER_ROLE.ROLE_ID)
        .from(IAM_USER_ROLE)
        .where(IAM_USER_ROLE.USER_ID.eq(userId))
        .fetch(IAM_USER_ROLE.ROLE_ID);
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
    Set<String> requiredDimensions = new LinkedHashSet<>(dsl.select(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION)
        .from(IAM_ROLE)
        .where(IAM_ROLE.ID.in(roleIds))
        .and(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION.isNotNull())
        .fetch(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION));

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
