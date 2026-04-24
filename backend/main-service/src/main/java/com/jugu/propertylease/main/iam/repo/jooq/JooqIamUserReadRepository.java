package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.iam.repo.IamUserReadRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUserDataScope;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamUserReadRepository implements IamUserReadRepository {

  private final DSLContext dsl;

  public JooqIamUserReadRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<IamUser> findActiveUserById(Long userId) {
    return Optional.ofNullable(
        dsl.selectFrom(IAM_USER)
            .where(IAM_USER.ID.eq(userId))
            .and(IAM_USER.DELETED_AT.isNull())
            .fetchOneInto(IamUser.class)
    );
  }

  @Override
  public List<IamRole> findRolesByUserId(Long userId) {
    return dsl.select(IAM_ROLE.fields())
        .from(IAM_USER_ROLE)
        .join(IAM_ROLE).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE.ID))
        .where(IAM_USER_ROLE.USER_ID.eq(userId))
        .fetchInto(IamRole.class);
  }

  @Override
  public List<IamUserDataScope> findDataScopesByUserId(Long userId) {
    return dsl.selectFrom(IAM_USER_DATA_SCOPE)
        .where(IAM_USER_DATA_SCOPE.USER_ID.eq(userId))
        .fetchInto(IamUserDataScope.class);
  }
}
