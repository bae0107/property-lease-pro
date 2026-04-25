package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.iam.repo.model.UserDataScopeSeed;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public interface IamPermissionManifestRepository {

  String findCurrentManifestChecksum();

  void upsertManifestSyncState(String manifestVersion, String manifestChecksum, OffsetDateTime syncedAt);

  Set<String> findAllPermissionCodes();

  void upsertPermission(String code, String name, String description, String resource, String action,
      OffsetDateTime now);

  void markPermissionDeleted(String code, OffsetDateTime now);

  Long findRoleIdByCode(String roleCode);

  Long insertBuiltinRole(String name, String code, String roleType, String requiredDataScopeDimension,
      String description, OffsetDateTime now);

  void updateBuiltinRole(Long roleId, String name, String roleType, String requiredDataScopeDimension,
      String description, OffsetDateTime now);

  Long findActivePermissionIdByCode(String permissionCode);

  void replaceRolePermissions(Long roleId, Set<Long> permissionIds);

  Long findUserIdByUserName(String userName);

  Long insertBuiltinUser(String userType, String status, String userName, String realName, String mobile,
      String email, String source, OffsetDateTime now);

  void updateBuiltinUserStatus(Long userId, String status, OffsetDateTime now);

  void replaceUserRoles(Long userId, List<Long> roleIds, OffsetDateTime now);

  void replaceUserDataScopes(Long userId, List<UserDataScopeSeed> scopes, OffsetDateTime now);

  boolean credentialExists(Long userId);

  void insertCredential(Long userId, String passwordHash, OffsetDateTime now);

  void updateCredential(Long userId, String passwordHash, OffsetDateTime now);

  boolean activePasswordIdentityExists(String userName);

  void insertPasswordIdentity(Long userId, String userName, OffsetDateTime now);
}
