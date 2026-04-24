package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.iam.repo.IamUserLifecycleRepository;
import com.jugu.propertylease.main.iam.repo.model.UserDeleteSnapshot;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.Record5;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamUserLifecycleRepository implements IamUserLifecycleRepository {

  private final DSLContext dsl;

  public JooqIamUserLifecycleRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public UserDeleteSnapshot findActiveUserSnapshot(Long userId) {
    Record5<String, String, String, String, String> row = dsl.select(IAM_USER.USER_NAME, IAM_USER.MOBILE,
            IAM_USER.EMAIL, IAM_USER.SOURCE_TYPE, IAM_USER.USER_TYPE)
        .from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne();
    if (row == null) {
      return null;
    }
    return new UserDeleteSnapshot(row.value1(), row.value2(), row.value3(), row.value4(), row.value5());
  }

  @Override
  public void softDeleteUser(Long userId, Long operatorUserId, String reason, String tombstoneUserName,
      String tombstoneMobile, String tombstoneEmail, String oldUserName, String oldMobile, String oldEmail,
      OffsetDateTime now) {
    dsl.update(IAM_USER)
        .set(IAM_USER.STATUS, "INACTIVE")
        .set(IAM_USER.DELETED_AT, now)
        .set(IAM_USER.DELETED_BY, operatorUserId)
        .set(IAM_USER.DELETE_REASON, reason)
        .set(IAM_USER.DELETED_USER_NAME, oldUserName)
        .set(IAM_USER.DELETED_MOBILE, oldMobile)
        .set(IAM_USER.DELETED_EMAIL, oldEmail)
        .set(IAM_USER.USER_NAME, tombstoneUserName)
        .set(IAM_USER.MOBILE, tombstoneMobile)
        .set(IAM_USER.EMAIL, tombstoneEmail)
        .set(IAM_USER.UPDATED_AT, now)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .execute();
  }

  @Override
  public void markIdentityDeleted(Long userId, OffsetDateTime now) {
    dsl.update(IAM_IDENTITY)
        .set(IAM_IDENTITY.DELETED_AT, now)
        .where(IAM_IDENTITY.USER_ID.eq(userId))
        .and(IAM_IDENTITY.DELETED_AT.isNull())
        .execute();
  }

  @Override
  public boolean isUserAssignedRoleCode(Long userId, String roleCode) {
    return dsl.fetchExists(dsl.selectOne()
        .from(IAM_USER_ROLE)
        .join(IAM_ROLE).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE.ID))
        .where(IAM_USER_ROLE.USER_ID.eq(userId))
        .and(IAM_ROLE.CODE.eq(roleCode)));
  }

  @Override
  public int countActiveUsersByRoleCode(String roleCode) {
    return dsl.fetchCount(dsl.selectDistinct(IAM_USER.ID)
        .from(IAM_USER)
        .join(IAM_USER_ROLE).on(IAM_USER.ID.eq(IAM_USER_ROLE.USER_ID))
        .join(IAM_ROLE).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE.ID))
        .where(IAM_USER.DELETED_AT.isNull())
        .and(IAM_USER.STATUS.eq("ACTIVE"))
        .and(IAM_ROLE.CODE.eq(roleCode)));
  }
}
