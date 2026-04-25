package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;

import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.iam.repo.IamUserFormMetaRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamUserFormMetaRepository implements IamUserFormMetaRepository {

  private final DSLContext dsl;

  public JooqIamUserFormMetaRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<IamRole> findStaffRoles() {
    return dsl.selectFrom(IAM_ROLE)
        .where(IAM_ROLE.ROLE_TYPE.eq(RoleType.STAFF.getValue()))
        .fetchInto(IamRole.class);
  }
}
