package com.jugu.propertylease.main.iam.service;

import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;

import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户生命周期管理服务（软删除等）。
 */
@Service
public class UserLifecycleService {

  private final DSLContext dsl;
  private final AuthVersionService authVersionService;

  public UserLifecycleService(DSLContext dsl, AuthVersionService authVersionService) {
    this.dsl = dsl;
    this.authVersionService = authVersionService;
  }

  /**
   * 软删除用户（不可恢复）：置 INACTIVE + 墓碑化登录标识 + 标记 deleted_at。
   */
  @Transactional
  public void softDeleteUser(Long userId, Long operatorUserId, String reason) {
    Record3<String, String, String> row = dsl.select(IAM_USER.USER_NAME, IAM_USER.MOBILE, IAM_USER.EMAIL)
        .from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne();
    if (row == null) {
      throw new IllegalArgumentException("User not found");
    }

    String oldUserName = row.value1();
    String oldMobile = row.value2();
    String oldEmail = row.value3();
    String tombstonePrefix = "__deleted__" + userId + "__";
    String tombstoneUserName = truncate(tombstonePrefix + oldUserName, 100);
    String tombstoneMobile = truncate("D" + userId + "00000000000000000000", 20);
    String tombstoneEmail = oldEmail == null ? null : truncate(tombstonePrefix + oldEmail, 200);
    OffsetDateTime now = OffsetDateTime.now();

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

    dsl.update(IAM_IDENTITY)
        .set(IAM_IDENTITY.DELETED_AT, now)
        .where(IAM_IDENTITY.USER_ID.eq(userId))
        .and(IAM_IDENTITY.DELETED_AT.isNull())
        .execute();

    authVersionService.bumpAuthVersion(userId, "SOFT_DELETE");
  }

  private String truncate(String value, int maxLen) {
    if (value == null || value.length() <= maxLen) {
      return value;
    }
    return value.substring(0, maxLen);
  }
}
