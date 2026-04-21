package com.jugu.propertylease.main.iam.service;

import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {

  private final DSLContext dsl;

  public RoleManagementService(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Transactional
  public Role createRole(CreateRoleRequest request) {
    if (request == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_CREATE_REQUEST_REQUIRED", "请求体不能为空");
    }
    if (request.getCode() == null || request.getCode().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_CODE_REQUIRED", "角色 code 不能为空");
    }
    if (dsl.fetchExists(dsl.selectOne().from(IAM_ROLE).where(IAM_ROLE.CODE.eq(request.getCode())))) {
      throw new BusinessException(HttpStatus.CONFLICT, "IAM_ROLE_CODE_DUPLICATE", "角色 code 已存在");
    }

    OffsetDateTime now = OffsetDateTime.now();
    Long id = dsl.insertInto(IAM_ROLE)
        .set(IAM_ROLE.NAME, request.getName())
        .set(IAM_ROLE.CODE, request.getCode())
        .set(IAM_ROLE.ROLE_TYPE, "STAFF")
        .set(IAM_ROLE.SOURCE_TYPE, "CUSTOM")
        .set(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION,
            request.getRequiredDataScopeDimension() == null ? null
                : request.getRequiredDataScopeDimension().getValue())
        .set(IAM_ROLE.DESCRIPTION, request.getDescription())
        .set(IAM_ROLE.CREATED_AT, now)
        .set(IAM_ROLE.UPDATED_AT, now)
        .returning(IAM_ROLE.ID)
        .fetchOne(IAM_ROLE.ID);

    return getRole(id);
  }

  public RoleDetail getRoleDetail(Long roleId) {
    Role role = getRole(roleId);
    List<Permission> permissions = dsl.select(IAM_PERMISSION.ID, IAM_PERMISSION.CODE, IAM_PERMISSION.NAME,
            IAM_PERMISSION.RESOURCE, IAM_PERMISSION.ACTION, IAM_PERMISSION.DESCRIPTION)
        .from(IAM_ROLE_PERMISSION)
        .join(IAM_PERMISSION).on(IAM_ROLE_PERMISSION.PERMISSION_ID.eq(IAM_PERMISSION.ID))
        .where(IAM_ROLE_PERMISSION.ROLE_ID.eq(roleId))
        .and(IAM_PERMISSION.DELETED_AT.isNull())
        .orderBy(IAM_PERMISSION.RESOURCE.asc(), IAM_PERMISSION.ACTION.asc(), IAM_PERMISSION.ID.asc())
        .fetch(r -> new Permission()
            .id(r.get(IAM_PERMISSION.ID))
            .code(r.get(IAM_PERMISSION.CODE))
            .name(r.get(IAM_PERMISSION.NAME))
            .resource(r.get(IAM_PERMISSION.RESOURCE))
            .action(r.get(IAM_PERMISSION.ACTION))
            .description(r.get(IAM_PERMISSION.DESCRIPTION)));

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

    Record row = dsl.selectFrom(IAM_ROLE).where(IAM_ROLE.ID.eq(roleId)).fetchOne();
    if (row == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "角色不存在");
    }
    if ("BUILTIN".equals(row.get(IAM_ROLE.SOURCE_TYPE))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_BUILTIN_MODIFY_FORBIDDEN",
          "BUILTIN 角色不可修改");
    }

    dsl.update(IAM_ROLE)
        .set(IAM_ROLE.NAME, request.getName() == null ? row.get(IAM_ROLE.NAME) : request.getName())
        .set(IAM_ROLE.DESCRIPTION,
            request.getDescription() == null ? row.get(IAM_ROLE.DESCRIPTION) : request.getDescription())
        .set(IAM_ROLE.UPDATED_AT, OffsetDateTime.now())
        .where(IAM_ROLE.ID.eq(roleId))
        .execute();

    return getRole(roleId);
  }

  @Transactional
  public RoleDetail updateRolePermissions(Long roleId, UpdateRolePermissionsRequest request) {
    if (request == null || request.getPermissionIds() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_PERMISSION_IDS_REQUIRED",
          "permissionIds 不能为空");
    }

    Record row = dsl.selectFrom(IAM_ROLE).where(IAM_ROLE.ID.eq(roleId)).fetchOne();
    if (row == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "角色不存在");
    }
    if ("BUILTIN".equals(row.get(IAM_ROLE.SOURCE_TYPE))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_BUILTIN_PERMISSION_UPDATE_FORBIDDEN",
          "BUILTIN 角色权限不可修改");
    }

    List<Long> permissionIds = new ArrayList<>(new LinkedHashSet<>(request.getPermissionIds()));
    if (!permissionIds.isEmpty()) {
      Set<Long> activePermissionIds = new LinkedHashSet<>(dsl.select(IAM_PERMISSION.ID)
          .from(IAM_PERMISSION)
          .where(IAM_PERMISSION.ID.in(permissionIds))
          .and(IAM_PERMISSION.DELETED_AT.isNull())
          .fetch(IAM_PERMISSION.ID));

      if (activePermissionIds.size() != permissionIds.size()) {
        Set<Long> missing = new LinkedHashSet<>(permissionIds);
        missing.removeAll(activePermissionIds);
        throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_PERMISSION_NOT_FOUND",
            "权限不存在或已删除: " + missing);
      }
    }

    dsl.deleteFrom(IAM_ROLE_PERMISSION).where(IAM_ROLE_PERMISSION.ROLE_ID.eq(roleId)).execute();
    for (Long permissionId : permissionIds) {
      dsl.insertInto(IAM_ROLE_PERMISSION)
          .set(IAM_ROLE_PERMISSION.ROLE_ID, roleId)
          .set(IAM_ROLE_PERMISSION.PERMISSION_ID, permissionId)
          .execute();
    }

    dsl.update(IAM_ROLE)
        .set(IAM_ROLE.UPDATED_AT, OffsetDateTime.now())
        .where(IAM_ROLE.ID.eq(roleId))
        .execute();

    return getRoleDetail(roleId);
  }

  @Transactional
  public void batchDeleteRoles(BatchRequest batchRequest) {
    if (batchRequest == null || batchRequest.getIds() == null || batchRequest.getIds().isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_DELETE_IDS_REQUIRED", "ids 不能为空");
    }

    List<Long> ids = new ArrayList<>(new LinkedHashSet<>(batchRequest.getIds()));
    List<Record> roles = dsl.selectFrom(IAM_ROLE).where(IAM_ROLE.ID.in(ids)).fetch();
    if (roles.size() != ids.size()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "存在角色不存在");
    }

    boolean containsBuiltin = roles.stream().anyMatch(r -> "BUILTIN".equals(r.get(IAM_ROLE.SOURCE_TYPE)));
    if (containsBuiltin) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_ROLE_DELETE_BUILTIN_FORBIDDEN",
          "包含 BUILTIN 角色，禁止删除");
    }

    boolean inUse = dsl.fetchExists(dsl.selectOne().from(IAM_USER_ROLE).where(IAM_USER_ROLE.ROLE_ID.in(ids)));
    if (inUse) {
      throw new BusinessException(HttpStatus.CONFLICT, "IAM_ROLE_DELETE_IN_USE", "包含已分配给用户的角色");
    }

    dsl.deleteFrom(IAM_ROLE_PERMISSION).where(IAM_ROLE_PERMISSION.ROLE_ID.in(ids)).execute();
    dsl.deleteFrom(IAM_ROLE).where(IAM_ROLE.ID.in(ids)).execute();
  }

  private Role getRole(Long roleId) {
    Record row = dsl.selectFrom(IAM_ROLE).where(IAM_ROLE.ID.eq(roleId)).fetchOne();
    if (row == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "角色不存在");
    }
    return new Role()
        .id(row.get(IAM_ROLE.ID))
        .name(row.get(IAM_ROLE.NAME))
        .code(row.get(IAM_ROLE.CODE))
        .roleType(RoleType.fromValue(row.get(IAM_ROLE.ROLE_TYPE)))
        .sourceType(SourceType.fromValue(row.get(IAM_ROLE.SOURCE_TYPE)))
        .requiredDataScopeDimension(row.get(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION) == null ? null
            : com.jugu.propertylease.main.api.model.DataScopeDimension
                .fromValue(row.get(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION)))
        .description(row.get(IAM_ROLE.DESCRIPTION))
        .createdAt(row.get(IAM_ROLE.CREATED_AT))
        .updatedAt(row.get(IAM_ROLE.UPDATED_AT));
  }
}
