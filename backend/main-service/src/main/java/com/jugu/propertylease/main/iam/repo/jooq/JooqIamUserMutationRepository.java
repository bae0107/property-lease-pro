package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.iam.repo.IamUserMutationRepository;
import com.jugu.propertylease.main.iam.repo.model.UserBaseInfo;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamUserMutationRepository implements IamUserMutationRepository {

  private final DSLContext dsl;

  public JooqIamUserMutationRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<UserBaseInfo> findActiveUserBase(Long userId) {
    Record2<String, String> row = dsl.select(IAM_USER.USER_TYPE, IAM_USER.SOURCE_TYPE)
        .from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne();
    if (row == null) {
      return Optional.empty();
    }
    return Optional.of(new UserBaseInfo(row.value1(), row.value2()));
  }

  @Override
  public boolean existsActiveUser(Long userId) {
    return dsl.fetchExists(dsl.selectOne().from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull()));
  }

  @Override
  public void updateUserProfile(Long userId, String realName, String mobile, String email, OffsetDateTime now) {
    dsl.update(IAM_USER)
        .set(IAM_USER.REAL_NAME, realName)
        .set(IAM_USER.MOBILE, mobile)
        .set(IAM_USER.EMAIL, email)
        .set(IAM_USER.UPDATED_AT, now)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .execute();
  }

  @Override
  public void updateUserStatus(Long userId, String status, OffsetDateTime now) {
    dsl.update(IAM_USER)
        .set(IAM_USER.STATUS, status)
        .set(IAM_USER.UPDATED_AT, now)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .execute();
  }

  @Override
  public boolean credentialExists(Long userId) {
    return dsl.fetchExists(dsl.selectOne().from(IAM_CREDENTIAL).where(IAM_CREDENTIAL.USER_ID.eq(userId)));
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
  public void insertCredential(Long userId, String passwordHash, OffsetDateTime now) {
    dsl.insertInto(IAM_CREDENTIAL)
        .set(IAM_CREDENTIAL.USER_ID, userId)
        .set(IAM_CREDENTIAL.PASSWORD_HASH, passwordHash)
        .set(IAM_CREDENTIAL.CREATED_AT, now)
        .set(IAM_CREDENTIAL.UPDATED_AT, now)
        .execute();
  }

  @Override
  public List<IamRole> findRolesByIds(List<Long> roleIds) {
    return dsl.selectFrom(IAM_ROLE)
        .where(IAM_ROLE.ID.in(roleIds))
        .fetchInto(IamRole.class);
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
  public List<Long> findRoleIdsByUserId(Long userId) {
    return dsl.select(IAM_USER_ROLE.ROLE_ID)
        .from(IAM_USER_ROLE)
        .where(IAM_USER_ROLE.USER_ID.eq(userId))
        .fetch(IAM_USER_ROLE.ROLE_ID);
  }

  @Override
  public Set<String> findRequiredScopeDimensionsByRoleIds(List<Long> roleIds) {
    return new LinkedHashSet<>(dsl.select(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION)
        .from(IAM_ROLE)
        .where(IAM_ROLE.ID.in(roleIds))
        .and(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION.isNotNull())
        .fetch(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION));
  }

  @Override
  public void clearUserDataScopes(Long userId) {
    dsl.deleteFrom(IAM_USER_DATA_SCOPE).where(IAM_USER_DATA_SCOPE.USER_ID.eq(userId)).execute();
  }

  @Override
  public void insertAllDataScope(Long userId, String scopeDimension, OffsetDateTime now) {
    dsl.insertInto(IAM_USER_DATA_SCOPE)
        .set(IAM_USER_DATA_SCOPE.USER_ID, userId)
        .set(IAM_USER_DATA_SCOPE.SCOPE_DIMENSION, scopeDimension)
        .set(IAM_USER_DATA_SCOPE.SCOPE_TYPE, "ALL")
        .set(IAM_USER_DATA_SCOPE.RESOURCE_ID, (Long) null)
        .set(IAM_USER_DATA_SCOPE.CREATED_AT, now)
        .execute();
  }

  @Override
  public void insertSpecificDataScope(Long userId, String scopeDimension, Long resourceId, OffsetDateTime now) {
    dsl.insertInto(IAM_USER_DATA_SCOPE)
        .set(IAM_USER_DATA_SCOPE.USER_ID, userId)
        .set(IAM_USER_DATA_SCOPE.SCOPE_DIMENSION, scopeDimension)
        .set(IAM_USER_DATA_SCOPE.SCOPE_TYPE, "SPECIFIC")
        .set(IAM_USER_DATA_SCOPE.RESOURCE_ID, resourceId)
        .set(IAM_USER_DATA_SCOPE.CREATED_AT, now)
        .execute();
  }

  @Override
  public Long insertUser(String userType, String userName, String realName, String mobile, String email,
      OffsetDateTime now) {
    return dsl.insertInto(IAM_USER)
        .set(IAM_USER.USER_TYPE, userType)
        .set(IAM_USER.SOURCE_TYPE, "CUSTOM")
        .set(IAM_USER.STATUS, "ACTIVE")
        .set(IAM_USER.AUTH_VERSION, 0)
        .set(IAM_USER.USER_NAME, userName)
        .set(IAM_USER.REAL_NAME, realName)
        .set(IAM_USER.MOBILE, mobile)
        .set(IAM_USER.EMAIL, email)
        .set(IAM_USER.SOURCE, "MANUAL")
        .set(IAM_USER.CREATED_BY, (Long) null)
        .set(IAM_USER.CREATED_AT, now)
        .set(IAM_USER.UPDATED_AT, now)
        .returning(IAM_USER.ID)
        .fetchOne(IAM_USER.ID);
  }

  @Override
  public void insertPasswordIdentity(Long userId, String username, OffsetDateTime now) {
    dsl.insertInto(IAM_IDENTITY)
        .set(IAM_IDENTITY.USER_ID, userId)
        .set(IAM_IDENTITY.PROVIDER, "password")
        .set(IAM_IDENTITY.PROVIDER_USER_ID, username)
        .set(IAM_IDENTITY.UNION_ID, (String) null)
        .set(IAM_IDENTITY.APP_ID, (String) null)
        .set(IAM_IDENTITY.CREATED_AT, now)
        .set(IAM_IDENTITY.DELETED_AT, (OffsetDateTime) null)
        .execute();
  }
}
