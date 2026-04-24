package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.common.model.BatchRequest;
import com.jugu.propertylease.main.api.model.CreateRoleRequest;
import com.jugu.propertylease.main.api.model.Permission;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RoleDetail;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UpdateRolePermissionsRequest;
import com.jugu.propertylease.main.api.model.UpdateRoleRequest;
import com.jugu.propertylease.main.iam.repo.IamRoleManagementRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {

  private final IamRoleManagementRepository roleRepository;

  public RoleManagementService(IamRoleManagementRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  @Transactional
  public Role createRole(CreateRoleRequest request) {
    if (request == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_CREATE_REQUEST_REQUIRED", "请求体不能为空");
    }
    if (request.getCode() == null || request.getCode().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_CODE_REQUIRED", "角色 code 不能为空");
    }
    if (roleRepository.existsByCode(request.getCode())) {
      throw new BusinessException(HttpStatus.CONFLICT, "IAM_ROLE_CODE_DUPLICATE", "角色 code 已存在");
    }

    OffsetDateTime now = OffsetDateTime.now();
    Long id = roleRepository.insertRole(request.getName(), request.getCode(),
        request.getRequiredDataScopeDimension() == null ? null : request.getRequiredDataScopeDimension().getValue(),
        request.getDescription(), now);

    return getRole(id);
  }

  public RoleDetail getRoleDetail(Long roleId) {
    Role role = getRole(roleId);
    List<Permission> permissions = roleRepository.findActivePermissionsByRoleId(roleId).stream()
        .map(p -> new Permission()
            .id(p.getId())
            .code(p.getCode())
            .name(p.getName())
            .resource(p.getResource())
            .action(p.getAction())
            .description(p.getDescription()))
        .toList();

    return new RoleDetail()
        .id(role.getId())
        .name(role.getName())
        .code(role.getCode())
        .roleType(role.getRoleType())
        .sourceType(role.getSourceType())
        .requiredDataScopeDimension(role.getRequiredDataScopeDimension())
        .description(role.getDescription())
        .createdAt(role.getCreatedAt())
        .updatedAt(role.getUpdatedAt())
        .permissions(permissions);
  }

  @Transactional
  public Role updateRole(Long roleId, UpdateRoleRequest request) {
    if (request == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_UPDATE_REQUEST_REQUIRED", "请求体不能为空");
    }

    IamRole row = roleRepository.findRoleById(roleId);
    if (row == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "角色不存在");
    }
    if ("BUILTIN".equals(row.getSourceType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_BUILTIN_MODIFY_FORBIDDEN",
          "BUILTIN 角色不可修改");
    }

    roleRepository.updateRoleBasic(roleId,
        request.getName() == null ? row.getName() : request.getName(),
        request.getDescription() == null ? row.getDescription() : request.getDescription(),
        OffsetDateTime.now());

    return getRole(roleId);
  }

  @Transactional
  public RoleDetail updateRolePermissions(Long roleId, UpdateRolePermissionsRequest request) {
    if (request == null || request.getPermissionIds() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_PERMISSION_IDS_REQUIRED",
          "permissionIds 不能为空");
    }

    IamRole row = roleRepository.findRoleById(roleId);
    if (row == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "角色不存在");
    }
    if ("BUILTIN".equals(row.getSourceType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_BUILTIN_PERMISSION_UPDATE_FORBIDDEN",
          "BUILTIN 角色权限不可修改");
    }

    List<Long> permissionIds = new ArrayList<>(new LinkedHashSet<>(request.getPermissionIds()));
    if (!permissionIds.isEmpty()) {
      Set<Long> activePermissionIds = roleRepository.findActivePermissionIdsByIds(permissionIds);

      if (activePermissionIds.size() != permissionIds.size()) {
        Set<Long> missing = new LinkedHashSet<>(permissionIds);
        missing.removeAll(activePermissionIds);
        throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_PERMISSION_NOT_FOUND",
            "权限不存在或已删除: " + missing);
      }
    }

    roleRepository.replaceRolePermissions(roleId, permissionIds);
    roleRepository.touchRoleUpdatedAt(roleId, OffsetDateTime.now());

    return getRoleDetail(roleId);
  }

  @Transactional
  public void batchDeleteRoles(BatchRequest batchRequest) {
    if (batchRequest == null || batchRequest.getIds() == null || batchRequest.getIds().isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_DELETE_IDS_REQUIRED", "ids 不能为空");
    }

    List<Long> ids = new ArrayList<>(new LinkedHashSet<>(batchRequest.getIds()));
    List<IamRole> roles = roleRepository.findRolesByIds(ids);
    if (roles.size() != ids.size()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "存在角色不存在");
    }

    boolean containsBuiltin = roles.stream().anyMatch(role -> "BUILTIN".equals(role.getSourceType()));
    if (containsBuiltin) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_DELETE_BUILTIN_FORBIDDEN",
          "包含 BUILTIN 角色，禁止删除");
    }

    if (roleRepository.existsUserRoleByRoleIds(ids)) {
      throw new BusinessException(HttpStatus.CONFLICT, "IAM_ROLE_DELETE_IN_USE", "包含已分配给用户的角色");
    }

    roleRepository.deleteRolePermissionsByRoleIds(ids);
    roleRepository.deleteRolesByIds(ids);
  }

  private Role getRole(Long roleId) {
    IamRole row = roleRepository.findRoleById(roleId);
    if (row == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "角色不存在");
    }
    return new Role()
        .id(row.getId())
        .name(row.getName())
        .code(row.getCode())
        .roleType(RoleType.fromValue(row.getRoleType()))
        .sourceType(SourceType.fromValue(row.getSourceType()))
        .requiredDataScopeDimension(EnumValueMapper.nullableFromValue(row.getRequiredDataScopeDimension(),
            com.jugu.propertylease.main.api.model.DataScopeDimension::fromValue))
        .description(row.getDescription())
        .createdAt(row.getCreatedAt())
        .updatedAt(row.getUpdatedAt());
  }
}
