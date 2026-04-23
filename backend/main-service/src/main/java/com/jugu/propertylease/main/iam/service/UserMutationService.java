package com.jugu.propertylease.main.iam.service;

import static com.jugu.propertylease.main.jooq.Tables.IAM_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.PatchUserRequest;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record2;
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
    if (!"STAFF".equals(userBase.value1())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_PATCH_TYPE_UNSUPPORTED",
          "当前接口仅支持 STAFF 用户");
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
      if (request.getRoleIds().isEmpty()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_IDS_EMPTY",
            "角色列表不能为空");
      }
      boolean hasNonStaffRole = dsl.fetchExists(dsl.selectOne().from(IAM_ROLE)
          .where(IAM_ROLE.ID.in(request.getRoleIds()))
          .and(IAM_ROLE.ROLE_TYPE.ne("STAFF")));
      if (hasNonStaffRole) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_ROLE_TYPE_MISMATCH",
            "所选角色必须全部为 STAFF 类型");
      }
      dsl.deleteFrom(IAM_USER_ROLE).where(IAM_USER_ROLE.USER_ID.eq(userId)).execute();
      for (Long roleId : request.getRoleIds()) {
        dsl.insertInto(IAM_USER_ROLE)
            .set(IAM_USER_ROLE.USER_ID, userId)
            .set(IAM_USER_ROLE.ROLE_ID, roleId)
            .set(IAM_USER_ROLE.CREATED_AT, now)
            .execute();
      }
      shouldBumpAuthVersion = true;
    }

    if (request.getRoleIds() != null) {
      validateScopeDimensionsAgainstRoles(request);
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

  private void validateScopeDimensionsAgainstRoles(PatchUserRequest request) {
    List<Long> roleIds = request.getRoleIds();
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
    if (request.getScopes() == null || request.getScopes().isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_SCOPE_REQUIRED",
          "所选角色要求配置数据权限");
    }
    Set<String> payloadDimensions = new LinkedHashSet<>();
    for (DataScopeItem item : request.getScopes()) {
      payloadDimensions.add(item.getDimension().getValue());
    }
    if (!payloadDimensions.equals(requiredDimensions)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_SCOPE_DIMENSION_MISMATCH",
          "数据权限维度必须与角色要求一致");
    }
  }
}
