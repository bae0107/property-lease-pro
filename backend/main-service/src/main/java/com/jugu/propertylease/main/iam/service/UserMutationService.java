package com.jugu.propertylease.main.iam.service;

import static com.jugu.propertylease.main.jooq.Tables.IAM_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.PatchUserRequest;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
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
      throw new IllegalArgumentException("Patch request is required");
    }
    if (dsl.fetchExists(dsl.selectOne().from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())) == false) {
      throw new IllegalArgumentException("User not found");
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
        throw new IllegalArgumentException("roleIds cannot be empty");
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
            throw new IllegalArgumentException("resourceIds required when scopeType=SPECIFIC");
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
}

