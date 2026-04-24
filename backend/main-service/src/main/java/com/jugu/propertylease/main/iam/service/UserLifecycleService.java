package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import com.jugu.propertylease.main.iam.repo.IamUserLifecycleRepository;
import com.jugu.propertylease.main.iam.repo.model.UserDeleteSnapshot;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户生命周期管理服务（软删除等）。
 */
@Service
public class UserLifecycleService {

  private static final String SYSTEM_ADMIN_ROLE_CODE = "ROLE_IAM_ADMIN";

  private final IamUserLifecycleRepository userLifecycleRepository;
  private final AuthVersionService authVersionService;

  public UserLifecycleService(IamUserLifecycleRepository userLifecycleRepository,
      AuthVersionService authVersionService) {
    this.userLifecycleRepository = userLifecycleRepository;
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

    UserDeleteSnapshot snapshot = userLifecycleRepository.findActiveUserSnapshot(userId);
    if (snapshot == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在");
    }

    String oldUserName = snapshot.userName();
    String oldMobile = snapshot.mobile();
    String oldEmail = snapshot.email();
    String sourceType = snapshot.sourceType();
    String userType = snapshot.userType();

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
    String tombstoneMobile = "D" + userId;
    String tombstoneEmail = oldEmail == null ? null : truncate(tombstonePrefix + oldEmail, 200);
    OffsetDateTime now = OffsetDateTime.now();

    userLifecycleRepository.softDeleteUser(userId, operatorUserId, reason, tombstoneUserName, tombstoneMobile,
        tombstoneEmail, oldUserName, oldMobile, oldEmail, now);

    userLifecycleRepository.markIdentityDeleted(userId, now);

    authVersionService.bumpAuthVersion(userId, "SOFT_DELETE");
  }

  private String truncate(String value, int maxLen) {
    if (value == null || value.length() <= maxLen) {
      return value;
    }
    return value.substring(0, maxLen);
  }

  private boolean isLastSystemAdmin(Long userId) {
    boolean targetIsAdmin = userLifecycleRepository.isUserAssignedRoleCode(userId, SYSTEM_ADMIN_ROLE_CODE);
    if (!targetIsAdmin) {
      return false;
    }
    int activeAdminCount = userLifecycleRepository.countActiveUsersByRoleCode(SYSTEM_ADMIN_ROLE_CODE);
    return activeAdminCount <= 1;
  }
}
