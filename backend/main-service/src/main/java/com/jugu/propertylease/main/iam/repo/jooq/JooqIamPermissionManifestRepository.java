package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION_SYNC_STATE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.iam.auth.IdentityProvider;
import com.jugu.propertylease.main.iam.repo.IamPermissionManifestRepository;
import com.jugu.propertylease.main.iam.repo.model.UserDataScopeSeed;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamPermissionManifestRepository implements IamPermissionManifestRepository {

  private final DSLContext dsl;

  public JooqIamPermissionManifestRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public String findCurrentManifestChecksum() {
    return dsl.select(IAM_PERMISSION_SYNC_STATE.MANIFEST_CHECKSUM)
        .from(IAM_PERMISSION_SYNC_STATE)
        .where(IAM_PERMISSION_SYNC_STATE.ID.eq(1L))
        .fetchOne(IAM_PERMISSION_SYNC_STATE.MANIFEST_CHECKSUM);
  }

  @Override
  public void upsertManifestSyncState(String manifestVersion, String manifestChecksum, OffsetDateTime syncedAt) {
    boolean exists = dsl.fetchExists(dsl.selectOne().from(IAM_PERMISSION_SYNC_STATE)
        .where(IAM_PERMISSION_SYNC_STATE.ID.eq(1L)));
    if (exists) {
      dsl.update(IAM_PERMISSION_SYNC_STATE)
          .set(IAM_PERMISSION_SYNC_STATE.MANIFEST_VERSION, manifestVersion)
          .set(IAM_PERMISSION_SYNC_STATE.MANIFEST_CHECKSUM, manifestChecksum)
          .set(IAM_PERMISSION_SYNC_STATE.SYNCED_AT, syncedAt)
          .where(IAM_PERMISSION_SYNC_STATE.ID.eq(1L))
          .execute();
      return;
    }
    dsl.insertInto(IAM_PERMISSION_SYNC_STATE)
        .set(IAM_PERMISSION_SYNC_STATE.ID, 1L)
        .set(IAM_PERMISSION_SYNC_STATE.MANIFEST_VERSION, manifestVersion)
        .set(IAM_PERMISSION_SYNC_STATE.MANIFEST_CHECKSUM, manifestChecksum)
        .set(IAM_PERMISSION_SYNC_STATE.SYNCED_AT, syncedAt)
        .execute();
  }

  @Override
  public Set<String> findAllPermissionCodes() {
    return new LinkedHashSet<>(dsl.select(IAM_PERMISSION.CODE)
        .from(IAM_PERMISSION)
        .fetch(IAM_PERMISSION.CODE));
  }

  @Override
  public void upsertPermission(String code, String name, String description, String resource, String action,
      OffsetDateTime now) {
    boolean exists = dsl.fetchExists(dsl.selectOne().from(IAM_PERMISSION)
        .where(IAM_PERMISSION.CODE.eq(code)));
    if (exists) {
      dsl.update(IAM_PERMISSION)
          .set(IAM_PERMISSION.NAME, name)
          .set(IAM_PERMISSION.DESCRIPTION, description)
          .set(IAM_PERMISSION.RESOURCE, resource)
          .set(IAM_PERMISSION.ACTION, action)
          .set(IAM_PERMISSION.DELETED_AT, (OffsetDateTime) null)
          .set(IAM_PERMISSION.UPDATED_AT, now)
          .where(IAM_PERMISSION.CODE.eq(code))
          .execute();
      return;
    }
    dsl.insertInto(IAM_PERMISSION)
        .set(IAM_PERMISSION.CODE, code)
        .set(IAM_PERMISSION.NAME, name)
        .set(IAM_PERMISSION.RESOURCE, resource)
        .set(IAM_PERMISSION.ACTION, action)
        .set(IAM_PERMISSION.DESCRIPTION, description)
        .set(IAM_PERMISSION.DELETED_AT, (OffsetDateTime) null)
        .set(IAM_PERMISSION.CREATED_AT, now)
        .set(IAM_PERMISSION.UPDATED_AT, now)
        .execute();
  }

  @Override
  public void markPermissionDeleted(String code, OffsetDateTime now) {
    dsl.update(IAM_PERMISSION)
        .set(IAM_PERMISSION.DELETED_AT, now)
        .set(IAM_PERMISSION.UPDATED_AT, now)
        .where(IAM_PERMISSION.CODE.eq(code))
        .execute();
  }

  @Override
  public Long findRoleIdByCode(String roleCode) {
    return dsl.select(IAM_ROLE.ID)
        .from(IAM_ROLE)
        .where(IAM_ROLE.CODE.eq(roleCode))
        .fetchOne(IAM_ROLE.ID);
  }

  @Override
  public Long insertBuiltinRole(String name, String code, String roleType, String requiredDataScopeDimension,
      String description, OffsetDateTime now) {
    return dsl.insertInto(IAM_ROLE)
        .set(IAM_ROLE.NAME, name)
        .set(IAM_ROLE.CODE, code)
        .set(IAM_ROLE.ROLE_TYPE, roleType)
        .set(IAM_ROLE.SOURCE_TYPE, SourceType.BUILTIN.getValue())
        .set(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION, requiredDataScopeDimension)
        .set(IAM_ROLE.DESCRIPTION, description)
        .set(IAM_ROLE.CREATED_AT, now)
        .set(IAM_ROLE.UPDATED_AT, now)
        .returning(IAM_ROLE.ID)
        .fetchOne(IAM_ROLE.ID);
  }

  @Override
  public void updateBuiltinRole(Long roleId, String name, String roleType, String requiredDataScopeDimension,
      String description, OffsetDateTime now) {
    dsl.update(IAM_ROLE)
        .set(IAM_ROLE.NAME, name)
        .set(IAM_ROLE.ROLE_TYPE, roleType)
        .set(IAM_ROLE.SOURCE_TYPE, SourceType.BUILTIN.getValue())
        .set(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION, requiredDataScopeDimension)
        .set(IAM_ROLE.DESCRIPTION, description)
        .set(IAM_ROLE.UPDATED_AT, now)
        .where(IAM_ROLE.ID.eq(roleId))
        .execute();
  }

  @Override
  public Long findActivePermissionIdByCode(String permissionCode) {
    return dsl.select(IAM_PERMISSION.ID)
        .from(IAM_PERMISSION)
        .where(IAM_PERMISSION.CODE.eq(permissionCode))
        .and(IAM_PERMISSION.DELETED_AT.isNull())
        .fetchOne(IAM_PERMISSION.ID);
  }

  @Override
  public void replaceRolePermissions(Long roleId, Set<Long> permissionIds) {
    dsl.deleteFrom(IAM_ROLE_PERMISSION).where(IAM_ROLE_PERMISSION.ROLE_ID.eq(roleId)).execute();
    for (Long permissionId : permissionIds) {
      dsl.insertInto(IAM_ROLE_PERMISSION)
          .set(IAM_ROLE_PERMISSION.ROLE_ID, roleId)
          .set(IAM_ROLE_PERMISSION.PERMISSION_ID, permissionId)
          .execute();
    }
  }

  @Override
  public Long findUserIdByUserName(String userName) {
    return dsl.select(IAM_USER.ID)
        .from(IAM_USER)
        .where(IAM_USER.USER_NAME.eq(userName))
        .fetchOne(IAM_USER.ID);
  }

  @Override
  public Long insertBuiltinUser(String userType, String status, String userName, String realName, String mobile,
      String email, String source, OffsetDateTime now) {
    return dsl.insertInto(IAM_USER)
        .set(IAM_USER.USER_TYPE, userType)
        .set(IAM_USER.SOURCE_TYPE, SourceType.BUILTIN.getValue())
        .set(IAM_USER.STATUS, status)
        .set(IAM_USER.AUTH_VERSION, 0)
        .set(IAM_USER.USER_NAME, userName)
        .set(IAM_USER.REAL_NAME, realName)
        .set(IAM_USER.MOBILE, mobile)
        .set(IAM_USER.EMAIL, email)
        .set(IAM_USER.SOURCE, source)
        .set(IAM_USER.CREATED_AT, now)
        .set(IAM_USER.UPDATED_AT, now)
        .returning(IAM_USER.ID)
        .fetchOne(IAM_USER.ID);
  }

  @Override
  public void updateBuiltinUserStatus(Long userId, String status, OffsetDateTime now) {
    dsl.update(IAM_USER)
        .set(IAM_USER.STATUS, status)
        .set(IAM_USER.UPDATED_AT, now)
        .where(IAM_USER.ID.eq(userId))
        .execute();
  }

  @Override
  public void replaceUserRoles(Long userId, List<Long> roleIds, OffsetDateTime now) {
    dsl.deleteFrom(IAM_USER_ROLE).where(IAM_USER_ROLE.USER_ID.eq(userId)).execute();
    for (Long roleId : roleIds) {
      dsl.insertInto(IAM_USER_ROLE)
          .set(IAM_USER_ROLE.USER_ID, userId)
          .set(IAM_USER_ROLE.ROLE_ID, roleId)
          .set(IAM_USER_ROLE.CREATED_AT, now)
          .execute();
    }
  }

  @Override
  public void replaceUserDataScopes(Long userId, List<UserDataScopeSeed> scopes, OffsetDateTime now) {
    dsl.deleteFrom(IAM_USER_DATA_SCOPE).where(IAM_USER_DATA_SCOPE.USER_ID.eq(userId)).execute();
    for (UserDataScopeSeed scope : scopes) {
      dsl.insertInto(IAM_USER_DATA_SCOPE)
          .set(IAM_USER_DATA_SCOPE.USER_ID, userId)
          .set(IAM_USER_DATA_SCOPE.SCOPE_DIMENSION, scope.dimension())
          .set(IAM_USER_DATA_SCOPE.SCOPE_TYPE, scope.scopeType())
          .set(IAM_USER_DATA_SCOPE.RESOURCE_ID, scope.resourceId())
          .set(IAM_USER_DATA_SCOPE.CREATED_AT, now)
          .execute();
    }
  }

  @Override
  public boolean credentialExists(Long userId) {
    return dsl.fetchExists(dsl.selectOne().from(IAM_CREDENTIAL).where(IAM_CREDENTIAL.USER_ID.eq(userId)));
  }

  @Override
  public void insertCredential(Long userId, String passwordHash, OffsetDateTime now) {
    dsl.insertInto(IAM_CREDENTIAL)
        .set(IAM_CREDENTIAL.USER_ID, userId)
        .set(IAM_CREDENTIAL.PASSWORD_HASH, passwordHash)
        .set(IAM_CREDENTIAL.CREATED_AT, now)
        .set(IAM_CREDENTIAL.UPDATED_AT, now)
        .execute();
  }

  @Override
  public void updateCredential(Long userId, String passwordHash, OffsetDateTime now) {
    dsl.update(IAM_CREDENTIAL)
        .set(IAM_CREDENTIAL.PASSWORD_HASH, passwordHash)
        .set(IAM_CREDENTIAL.UPDATED_AT, now)
        .where(IAM_CREDENTIAL.USER_ID.eq(userId))
        .execute();
  }

  @Override
  public boolean activePasswordIdentityExists(String userName) {
    return dsl.fetchExists(dsl.selectOne().from(IAM_IDENTITY)
        .where(IAM_IDENTITY.PROVIDER.eq(IdentityProvider.PASSWORD.value()))
        .and(IAM_IDENTITY.PROVIDER_USER_ID.eq(userName))
        .and(IAM_IDENTITY.DELETED_AT.isNull()));
  }

  @Override
  public void insertPasswordIdentity(Long userId, String userName, OffsetDateTime now) {
    dsl.insertInto(IAM_IDENTITY)
        .set(IAM_IDENTITY.USER_ID, userId)
        .set(IAM_IDENTITY.PROVIDER, IdentityProvider.PASSWORD.value())
        .set(IAM_IDENTITY.PROVIDER_USER_ID, userName)
        .set(IAM_IDENTITY.UNION_ID, (String) null)
        .set(IAM_IDENTITY.APP_ID, (String) null)
        .set(IAM_IDENTITY.CREATED_AT, now)
        .set(IAM_IDENTITY.DELETED_AT, (OffsetDateTime) null)
        .execute();
  }
}
