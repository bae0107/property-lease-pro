package com.jugu.propertylease.main.iam.service;

import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.Record5;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户生命周期管理服务（软删除等）。
 */
@Service
public class UserLifecycleService {

  private static final String SYSTEM_ADMIN_ROLE_CODE = "SYSTEM_ADMIN";

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
    if (operatorUserId != null && operatorUserId.equals(userId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_DELETE_SELF_FORBIDDEN",
          "不允许删除当前登录用户");
    }

    Record5<String, String, String, String, String> row = dsl.select(IAM_USER.USER_NAME, IAM_USER.MOBILE,
            IAM_USER.EMAIL, IAM_USER.SOURCE_TYPE, IAM_USER.USER_TYPE)
        .from(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne();
    if (row == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在");
    }

    String oldUserName = row.value1();
    String oldMobile = row.value2();
    String oldEmail = row.value3();
    String sourceType = row.value4();
    String userType = row.value5();

    if ("BUILTIN".equals(sourceType) || "SYSTEM".equals(userType)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_DELETE_FORBIDDEN",
          "内置或系统用户不允许删除");
    }

    if (isLastSystemAdmin(userId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_USER_DELETE_LAST_ADMIN_FORBIDDEN",
          "不允许删除最后一个管理员");
    }

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

  private boolean isLastSystemAdmin(Long userId) {
    boolean targetIsAdmin = dsl.fetchExists(dsl.selectOne()
        .from(IAM_USER_ROLE)
        .join(IAM_ROLE).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE.ID))
        .where(IAM_USER_ROLE.USER_ID.eq(userId))
        .and(IAM_ROLE.CODE.eq(SYSTEM_ADMIN_ROLE_CODE)));
    if (!targetIsAdmin) {
      return false;
    }
    int activeAdminCount = dsl.fetchCount(dsl.selectDistinct(IAM_USER.ID)
        .from(IAM_USER)
        .join(IAM_USER_ROLE).on(IAM_USER.ID.eq(IAM_USER_ROLE.USER_ID))
        .join(IAM_ROLE).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE.ID))
        .where(IAM_USER.DELETED_AT.isNull())
        .and(IAM_USER.STATUS.eq("ACTIVE"))
        .and(IAM_ROLE.CODE.eq(SYSTEM_ADMIN_ROLE_CODE)));
    return activeAdminCount <= 1;
  }
}
