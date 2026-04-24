package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IamUserMutationRepository {

  Optional<UserBaseInfo> findActiveUserBase(Long userId);

  boolean existsActiveUser(Long userId);

  void updateUserProfile(Long userId, String realName, String mobile, String email, OffsetDateTime now);

  void updateUserStatus(Long userId, String status, OffsetDateTime now);

  boolean credentialExists(Long userId);

  void updateCredential(Long userId, String passwordHash, OffsetDateTime now);

  void insertCredential(Long userId, String passwordHash, OffsetDateTime now);

  List<IamRole> findRolesByIds(List<Long> roleIds);

  void replaceUserRoles(Long userId, List<Long> roleIds, OffsetDateTime now);

  List<Long> findRoleIdsByUserId(Long userId);

  Set<String> findRequiredScopeDimensionsByRoleIds(List<Long> roleIds);

  void clearUserDataScopes(Long userId);

  void insertAllDataScope(Long userId, String scopeDimension, OffsetDateTime now);

  void insertSpecificDataScope(Long userId, String scopeDimension, Long resourceId, OffsetDateTime now);

  Long insertUser(String userType, String userName, String realName, String mobile, String email,
      OffsetDateTime now);

  void insertPasswordIdentity(Long userId, String username, OffsetDateTime now);

  class UserBaseInfo {

    private final String userType;
    private final String sourceType;

    public UserBaseInfo(String userType, String sourceType) {
      this.userType = userType;
      this.sourceType = sourceType;
    }

    public String getUserType() {
      return userType;
    }

    public String getSourceType() {
      return sourceType;
    }
  }
}
