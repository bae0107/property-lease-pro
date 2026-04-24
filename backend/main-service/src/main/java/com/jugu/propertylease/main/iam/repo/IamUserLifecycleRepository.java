package com.jugu.propertylease.main.iam.repo;

import java.time.OffsetDateTime;

public interface IamUserLifecycleRepository {

  UserDeleteSnapshot findActiveUserSnapshot(Long userId);

  void softDeleteUser(Long userId, Long operatorUserId, String reason, String tombstoneUserName,
      String tombstoneMobile, String tombstoneEmail, String oldUserName, String oldMobile, String oldEmail,
      OffsetDateTime now);

  void markIdentityDeleted(Long userId, OffsetDateTime now);

  boolean isUserAssignedRoleCode(Long userId, String roleCode);

  int countActiveUsersByRoleCode(String roleCode);

  class UserDeleteSnapshot {

    private final String userName;
    private final String mobile;
    private final String email;
    private final String sourceType;
    private final String userType;

    public UserDeleteSnapshot(String userName, String mobile, String email, String sourceType,
        String userType) {
      this.userName = userName;
      this.mobile = mobile;
      this.email = email;
      this.sourceType = sourceType;
      this.userType = userType;
    }

    public String getUserName() {
      return userName;
    }

    public String getMobile() {
      return mobile;
    }

    public String getEmail() {
      return email;
    }

    public String getSourceType() {
      return sourceType;
    }

    public String getUserType() {
      return userType;
    }
  }
}
