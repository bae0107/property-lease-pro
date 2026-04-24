package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.iam.repo.IamUserLifecycleRepository;
import com.jugu.propertylease.main.iam.repo.model.UserDeleteSnapshot;
import com.jugu.propertylease.main.iam.repo.model.UserSoftDeleteCommand;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamUserLifecycleRepository implements IamUserLifecycleRepository {

  private final DSLContext dsl;

  public JooqIamUserLifecycleRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public UserDeleteSnapshot findActiveUserSnapshot(Long userId) {
    IamUser user = dsl.selectFrom(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOneInto(IamUser.class);
    if (user == null) {
      return null;
    }
    return new UserDeleteSnapshot(user.getUserName(), user.getMobile(), user.getEmail(),
        user.getSourceType(), user.getUserType());
  }

  @Override
  public void softDeleteUser(UserSoftDeleteCommand command) {
    dsl.update(IAM_USER)
        .set(IAM_USER.STATUS, "INACTIVE")
        .set(IAM_USER.DELETED_AT, command.now())
        .set(IAM_USER.DELETED_BY, command.operatorUserId())
        .set(IAM_USER.DELETE_REASON, command.reason())
        .set(IAM_USER.DELETED_USER_NAME, command.oldUserName())
        .set(IAM_USER.DELETED_MOBILE, command.oldMobile())
        .set(IAM_USER.DELETED_EMAIL, command.oldEmail())
        .set(IAM_USER.USER_NAME, command.tombstoneUserName())
        .set(IAM_USER.MOBILE, command.tombstoneMobile())
        .set(IAM_USER.EMAIL, command.tombstoneEmail())
        .set(IAM_USER.UPDATED_AT, command.now())
        .where(IAM_USER.ID.eq(command.userId()))
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
