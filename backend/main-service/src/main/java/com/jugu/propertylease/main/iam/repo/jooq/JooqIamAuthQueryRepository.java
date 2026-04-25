package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.iam.auth.IdentityProvider;
import com.jugu.propertylease.main.iam.repo.IamAuthQueryRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.IamCredential;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamAuthQueryRepository implements IamAuthQueryRepository {

  private final DSLContext dsl;

  public JooqIamAuthQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<Long> findActivePasswordIdentityUserId(String username) {
    return Optional.ofNullable(dsl.select(IAM_IDENTITY.USER_ID)
        .from(IAM_IDENTITY)
        .where(IAM_IDENTITY.PROVIDER.eq(IdentityProvider.PASSWORD.value()))
        .and(IAM_IDENTITY.PROVIDER_USER_ID.eq(username))
        .and(IAM_IDENTITY.DELETED_AT.isNull())
        .fetchOne(IAM_IDENTITY.USER_ID));
  }

  @Override
  public Optional<IamUser> findActiveUserById(Long userId) {
    return Optional.ofNullable(dsl.selectFrom(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOneInto(IamUser.class));
  }

  @Override
  public Optional<IamCredential> findCredentialByUserId(Long userId) {
    return Optional.ofNullable(dsl.selectFrom(IAM_CREDENTIAL)
        .where(IAM_CREDENTIAL.USER_ID.eq(userId))
        .fetchOneInto(IamCredential.class));
  }

  @Override
  public List<String> findPermissionCodesByUserId(Long userId) {
    return dsl.selectDistinct(IAM_PERMISSION.CODE)
        .from(IAM_USER_ROLE)
        .join(IAM_ROLE_PERMISSION).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE_PERMISSION.ROLE_ID))
        .join(IAM_PERMISSION).on(IAM_ROLE_PERMISSION.PERMISSION_ID.eq(IAM_PERMISSION.ID))
        .where(IAM_USER_ROLE.USER_ID.eq(userId))
        .and(IAM_PERMISSION.DELETED_AT.isNull())
        .fetch(IAM_PERMISSION.CODE);
  }
}
