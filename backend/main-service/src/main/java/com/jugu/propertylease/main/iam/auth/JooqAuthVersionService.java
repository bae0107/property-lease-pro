package com.jugu.propertylease.main.iam.auth;

import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;

import java.time.LocalDateTime;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 基于 jOOQ 的 authVersion 递增实现。
 */
@Component
public class JooqAuthVersionService implements AuthVersionService {

  private static final Logger log = LoggerFactory.getLogger(JooqAuthVersionService.class);

  private final DSLContext dsl;

  public JooqAuthVersionService(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void bumpAuthVersion(Long userId, String reason) {
    int affected = dsl.update(IAM_USER)
        .set(IAM_USER.AUTH_VERSION, IAM_USER.AUTH_VERSION.plus(1))
        .set(IAM_USER.UPDATED_AT, LocalDateTime.now())
        .where(IAM_USER.ID.eq(userId))
        .execute();
    if (affected == 0) {
      log.warn("skip authVersion bump, user not found or already deleted: userId={} reason={}",
          userId, reason);
      return;
    }
    log.info("authVersion bumped for userId={} reason={}", userId, reason);
  }
}
