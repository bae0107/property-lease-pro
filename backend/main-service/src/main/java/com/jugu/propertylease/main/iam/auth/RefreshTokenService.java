package com.jugu.propertylease.main.iam.auth;

import static com.jugu.propertylease.main.jooq.Tables.IAM_REFRESH_TOKEN;

import com.jugu.propertylease.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 持久化服务（DB 版，token 明文仅返回给客户端，库内仅存 hash）。
 */
@Service
public class RefreshTokenService {

  private static final long REFRESH_TOKEN_TTL_SECONDS = 7L * 24 * 3600;

  private final DSLContext dsl;

  public RefreshTokenService(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Transactional
  public String create(Long userId, String username, String userType) {
    String rawToken = UUID.randomUUID().toString().replace("-", "");
    String hash = sha256(rawToken);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    dsl.insertInto(IAM_REFRESH_TOKEN)
        .set(IAM_REFRESH_TOKEN.TOKEN_HASH, hash)
        .set(IAM_REFRESH_TOKEN.USER_ID, userId)
        .set(IAM_REFRESH_TOKEN.USERNAME_SNAPSHOT, username)
        .set(IAM_REFRESH_TOKEN.USER_TYPE_SNAPSHOT, userType)
        .set(IAM_REFRESH_TOKEN.ISSUED_AT, now)
        .set(IAM_REFRESH_TOKEN.EXPIRES_AT, now.plusSeconds(REFRESH_TOKEN_TTL_SECONDS))
        .set(IAM_REFRESH_TOKEN.REVOKED_AT, (OffsetDateTime) null)
        .set(IAM_REFRESH_TOKEN.REPLACED_BY_TOKEN_HASH, (String) null)
        .set(IAM_REFRESH_TOKEN.CREATED_AT, now)
        .set(IAM_REFRESH_TOKEN.UPDATED_AT, now)
        .execute();

    return rawToken;
  }

  @Transactional(readOnly = true)
  public TokenPayload validate(String rawToken) {
    String hash = sha256(rawToken);
    Record record = dsl.selectFrom(IAM_REFRESH_TOKEN)
        .where(IAM_REFRESH_TOKEN.TOKEN_HASH.eq(hash))
        .fetchOne();

    if (record == null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_INVALID",
          "refresh token 无效");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (record.get(IAM_REFRESH_TOKEN.REVOKED_AT) != null
        || record.get(IAM_REFRESH_TOKEN.REPLACED_BY_TOKEN_HASH) != null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_REVOKED",
          "refresh token 已失效");
    }

    if (record.get(IAM_REFRESH_TOKEN.EXPIRES_AT).isBefore(now)) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_EXPIRED",
          "refresh token 已过期");
    }

    return new TokenPayload(
        record.get(IAM_REFRESH_TOKEN.USER_ID),
        record.get(IAM_REFRESH_TOKEN.USERNAME_SNAPSHOT),
        record.get(IAM_REFRESH_TOKEN.USER_TYPE_SNAPSHOT),
        hash);
  }

  @Transactional
  public String rotate(String oldRawToken, Long userId, String username, String userType) {
    String oldHash = sha256(oldRawToken);
    String newRawToken = UUID.randomUUID().toString().replace("-", "");
    String newHash = sha256(newRawToken);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    int updated = dsl.update(IAM_REFRESH_TOKEN)
        .set(IAM_REFRESH_TOKEN.REVOKED_AT, now)
        .set(IAM_REFRESH_TOKEN.REPLACED_BY_TOKEN_HASH, newHash)
        .set(IAM_REFRESH_TOKEN.UPDATED_AT, now)
        .where(IAM_REFRESH_TOKEN.TOKEN_HASH.eq(oldHash))
        .and(IAM_REFRESH_TOKEN.REVOKED_AT.isNull())
        .and(IAM_REFRESH_TOKEN.REPLACED_BY_TOKEN_HASH.isNull())
        .execute();

    if (updated == 0) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_INVALID",
          "refresh token 无效");
    }

    dsl.insertInto(IAM_REFRESH_TOKEN)
        .set(IAM_REFRESH_TOKEN.TOKEN_HASH, newHash)
        .set(IAM_REFRESH_TOKEN.USER_ID, userId)
        .set(IAM_REFRESH_TOKEN.USERNAME_SNAPSHOT, username)
        .set(IAM_REFRESH_TOKEN.USER_TYPE_SNAPSHOT, userType)
        .set(IAM_REFRESH_TOKEN.ISSUED_AT, now)
        .set(IAM_REFRESH_TOKEN.EXPIRES_AT, now.plusSeconds(REFRESH_TOKEN_TTL_SECONDS))
        .set(IAM_REFRESH_TOKEN.REVOKED_AT, (OffsetDateTime) null)
        .set(IAM_REFRESH_TOKEN.REPLACED_BY_TOKEN_HASH, (String) null)
        .set(IAM_REFRESH_TOKEN.CREATED_AT, now)
        .set(IAM_REFRESH_TOKEN.UPDATED_AT, now)
        .execute();

    return newRawToken;
  }

  @Transactional
  public void revoke(String rawToken) {
    String hash = sha256(rawToken);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    dsl.update(IAM_REFRESH_TOKEN)
        .set(IAM_REFRESH_TOKEN.REVOKED_AT, now)
        .set(IAM_REFRESH_TOKEN.UPDATED_AT, now)
        .where(IAM_REFRESH_TOKEN.TOKEN_HASH.eq(hash))
        .and(IAM_REFRESH_TOKEN.REVOKED_AT.isNull())
        .execute();
  }

  private String sha256(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot hash refresh token", e);
    }
  }

  public record TokenPayload(Long userId, String username, String userType, String tokenHash) {
  }
}
