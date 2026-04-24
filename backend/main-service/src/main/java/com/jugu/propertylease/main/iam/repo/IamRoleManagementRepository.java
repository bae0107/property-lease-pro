package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.IamPermission;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public interface IamRoleManagementRepository {

  boolean existsByCode(String code);

  Long insertRole(String name, String code, String requiredDataScopeDimension, String description,
      OffsetDateTime now);

  IamRole findRoleById(Long roleId);

  void updateRoleBasic(Long roleId, String name, String description, OffsetDateTime now);

  List<IamPermission> findActivePermissionsByRoleId(Long roleId);

  Set<Long> findActivePermissionIdsByIds(List<Long> permissionIds);

  void replaceRolePermissions(Long roleId, List<Long> permissionIds);

  void touchRoleUpdatedAt(Long roleId, OffsetDateTime now);

  List<IamRole> findRolesByIds(List<Long> roleIds);

  boolean existsUserRoleByRoleIds(List<Long> roleIds);

  void deleteRolePermissionsByRoleIds(List<Long> roleIds);

  void deleteRolesByIds(List<Long> roleIds);
}
