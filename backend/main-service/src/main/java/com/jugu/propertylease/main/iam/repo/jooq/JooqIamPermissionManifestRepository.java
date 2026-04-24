package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION_SYNC_STATE;

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
}
