package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.iam.repo.model.UserDeleteSnapshot;
import java.time.OffsetDateTime;

public interface IamUserLifecycleRepository {

  UserDeleteSnapshot findActiveUserSnapshot(Long userId);

  void softDeleteUser(Long userId, Long operatorUserId, String reason, String tombstoneUserName,
      String tombstoneMobile, String tombstoneEmail, String oldUserName, String oldMobile, String oldEmail,
      OffsetDateTime now);

  void markIdentityDeleted(Long userId, OffsetDateTime now);

  boolean isUserAssignedRoleCode(Long userId, String roleCode);

  int countActiveUsersByRoleCode(String roleCode);
}
