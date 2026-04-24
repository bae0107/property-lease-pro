package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;

import com.jugu.propertylease.main.iam.repo.IamAuthVersionRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamAuthVersionRepository implements IamAuthVersionRepository {

  private final DSLContext dsl;

  public JooqIamAuthVersionRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public int bumpAuthVersion(Long userId, OffsetDateTime now) {
    return dsl.update(IAM_USER)
        .set(IAM_USER.AUTH_VERSION, IAM_USER.AUTH_VERSION.plus(1))
        .set(IAM_USER.UPDATED_AT, now)
        .where(IAM_USER.ID.eq(userId))
        .execute();
  }

  @Override
  public Optional<Integer> findCurrentAuthVersion(Long userId) {
    Integer currentVersion = dsl.select(IAM_USER.AUTH_VERSION)
        .from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne(IAM_USER.AUTH_VERSION);
    return Optional.ofNullable(currentVersion);
  }
}
