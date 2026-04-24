package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_REFRESH_TOKEN;

import com.jugu.propertylease.main.iam.repo.IamRefreshTokenRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqIamRefreshTokenRepository implements IamRefreshTokenRepository {

  private final DSLContext dsl;

  public JooqIamRefreshTokenRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void insert(String tokenHash, Long userId, String usernameSnapshot, String userTypeSnapshot,
      OffsetDateTime issuedAt, OffsetDateTime expiresAt, OffsetDateTime revokedAt,
      String replacedByTokenHash, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    dsl.insertInto(IAM_REFRESH_TOKEN)
        .set(IAM_REFRESH_TOKEN.TOKEN_HASH, tokenHash)
        .set(IAM_REFRESH_TOKEN.USER_ID, userId)
        .set(IAM_REFRESH_TOKEN.USERNAME_SNAPSHOT, usernameSnapshot)
        .set(IAM_REFRESH_TOKEN.USER_TYPE_SNAPSHOT, userTypeSnapshot)
        .set(IAM_REFRESH_TOKEN.ISSUED_AT, issuedAt)
        .set(IAM_REFRESH_TOKEN.EXPIRES_AT, expiresAt)
        .set(IAM_REFRESH_TOKEN.REVOKED_AT, revokedAt)
        .set(IAM_REFRESH_TOKEN.REPLACED_BY_TOKEN_HASH, replacedByTokenHash)
        .set(IAM_REFRESH_TOKEN.CREATED_AT, createdAt)
        .set(IAM_REFRESH_TOKEN.UPDATED_AT, updatedAt)
        .execute();
  }

  @Override
  public Optional<IamRefreshToken> findByTokenHash(String tokenHash) {
    return Optional.ofNullable(dsl.selectFrom(IAM_REFRESH_TOKEN)
        .where(IAM_REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
        .fetchOneInto(IamRefreshToken.class));
  }

  @Override
  public int revokeAndReplace(String tokenHash, String replacedByTokenHash, OffsetDateTime now) {
    return dsl.update(IAM_REFRESH_TOKEN)
        .set(IAM_REFRESH_TOKEN.REVOKED_AT, now)
        .set(IAM_REFRESH_TOKEN.REPLACED_BY_TOKEN_HASH, replacedByTokenHash)
        .set(IAM_REFRESH_TOKEN.UPDATED_AT, now)
        .where(IAM_REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
        .and(IAM_REFRESH_TOKEN.REVOKED_AT.isNull())
        .and(IAM_REFRESH_TOKEN.REPLACED_BY_TOKEN_HASH.isNull())
        .execute();
  }

  @Override
  public void revoke(String tokenHash, OffsetDateTime now) {
    dsl.update(IAM_REFRESH_TOKEN)
        .set(IAM_REFRESH_TOKEN.REVOKED_AT, now)
        .set(IAM_REFRESH_TOKEN.UPDATED_AT, now)
        .where(IAM_REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
        .and(IAM_REFRESH_TOKEN.REVOKED_AT.isNull())
        .execute();
  }
}
