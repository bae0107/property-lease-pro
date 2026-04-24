package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION_SYNC_STATE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE_PERMISSION;

import com.jugu.propertylease.main.iam.repo.IamPermissionManifestRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
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
        .set(IAM_ROLE.SOURCE_TYPE, "BUILTIN")
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
        .set(IAM_ROLE.SOURCE_TYPE, "BUILTIN")
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
}
