package com.jugu.propertylease.main.iam.repo;

import java.time.OffsetDateTime;
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

}
