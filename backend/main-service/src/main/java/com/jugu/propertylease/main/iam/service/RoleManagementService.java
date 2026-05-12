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
import com.jugu.propertylease.main.iam.repo.RoleRepository;
import com.jugu.propertylease.main.iam.service.mapper.RoleDtoMapper;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色管理服务。
 *
 * <p>设计说明：
 * <ul>
 *   <li>格式校验（request 非空、ids 非空等）已由 OpenAPI yaml + @Valid 在框架层完成，
 *       Service 层不再重复写 null 判断。</li>
 *   <li>SourceType 枚举比对直接用 == 而非 .getValue().equals(...)，
 *       依赖 RoleRepository.findById 返回已映射枚举的 IamRole POJO。</li>
 * </ul>
 */
@Service
public class RoleManagementService {

    private final RoleRepository roleRepo;
    private final RoleDtoMapper roleDtoMapper;

    public RoleManagementService(RoleRepository roleRepo, RoleDtoMapper roleDtoMapper) {
        this.roleRepo = roleRepo;
        this.roleDtoMapper = roleDtoMapper;
    }

    // ─────────────────────────────────────────────
    // 创建角色
    // ─────────────────────────────────────────────

    @Transactional
    public Role createRole(CreateRoleRequest request) {
        // 格式校验（code 非空、minLength:1）由 yaml @Valid 完成，此处只做业务校验
        if (roleRepo.existsByCode(request.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "IAM_ROLE_CODE_DUPLICATE", "角色 code 已存在");
        }

        OffsetDateTime now = OffsetDateTime.now();

        // RoleType RoleType.STAFF
//        RoleType roleType = request.getRoleType() != null ? request.getRoleType() : RoleType.STAFF;

        Long id = roleRepo.insert(
            request.getName(),
            request.getCode(),
            RoleType.STAFF,
            request.getRequiredDataScopeDimension(),   // 直接传枚举，Repo 层处理 null
            request.getDescription(),
            now
        );

        return requireRole(id);
    }

    // ─────────────────────────────────────────────
    // 查询角色详情（含权限列表）
    // ─────────────────────────────────────────────

    public RoleDetail getRoleDetail(Long roleId) {
        Role role = requireRole(roleId);
        List<Permission> permissions = roleRepo.findActivePermissionsByRoleId(roleId)
                .stream()
                .map(roleDtoMapper::toPermission)
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

    // ─────────────────────────────────────────────
    // 更新角色基本信息
    // ─────────────────────────────────────────────

    @Transactional
    public Role updateRole(Long roleId, UpdateRoleRequest request) {
        IamRole row = requireActiveRole(roleId);
        requireCustomRole(row, "修改");

        OffsetDateTime now = OffsetDateTime.now();
        // name / description 为 null 时保留原值，非 null 时覆写
        roleRepo.updateBasic(
                roleId,
                request.getName() != null ? request.getName() : row.getName(),
                request.getDescription() != null ? request.getDescription() : row.getDescription(),
                now
        );

        return requireRole(roleId);
    }

    // ─────────────────────────────────────────────
    // 更新角色权限
    // ─────────────────────────────────────────────

    @Transactional
    public RoleDetail updateRolePermissions(Long roleId, UpdateRolePermissionsRequest request) {
        IamRole row = requireActiveRole(roleId);
        requireCustomRole(row, "修改权限");

        // 去重，保留顺序
        List<Long> permissionIds = new ArrayList<>(new LinkedHashSet<>(request.getPermissionIds()));

        if (!permissionIds.isEmpty()) {
            Set<Long> activeIds = roleRepo.findActivePermissionIdsByIds(permissionIds);
            if (activeIds.size() != permissionIds.size()) {
                Set<Long> missing = new LinkedHashSet<>(permissionIds);
                missing.removeAll(activeIds);
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "IAM_PERMISSION_NOT_FOUND", "权限不存在或已删除：" + missing);
            }
        }

        // replacePermissions 内部已更新 updated_at，无需额外 touchUpdatedAt
        roleRepo.replacePermissions(roleId, permissionIds, OffsetDateTime.now());

        return getRoleDetail(roleId);
    }

    // ─────────────────────────────────────────────
    // 批量删除角色
    // ─────────────────────────────────────────────

    @Transactional
    public void batchDeleteRoles(BatchRequest batchRequest) {
        // ids 非空由 BatchRequest.ids @NotEmpty 在框架层保证，Service 层不重复校验
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(batchRequest.getIds()));

        List<IamRole> roles = roleRepo.findByIds(ids);
        if (roles.size() != ids.size()) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "IAM_ROLE_NOT_FOUND", "存在不存在的角色");
        }

        boolean containsBuiltin = roles.stream()
                .anyMatch(r -> SourceType.BUILTIN.getValue().equals(r.getSourceType()));
        if (containsBuiltin) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_ROLE_DELETE_BUILTIN_FORBIDDEN", "包含 BUILTIN 角色，禁止删除");
        }

        if (roleRepo.isAnyRoleAssignedToUser(ids)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "IAM_ROLE_DELETE_IN_USE", "包含已分配给用户的角色");
        }

        roleRepo.deletePermissionsByRoleIds(ids);
        roleRepo.deleteByIds(ids);
    }

    // ─────────────────────────────────────────────
    // 私有辅助方法
    // ─────────────────────────────────────────────

    /** 查询角色，不存在则抛 404。 */
    private Role requireRole(Long roleId) {
        return roleRepo.findById(roleId)
                .map(roleDtoMapper::toRole)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "角色不存在"));
    }

    /** 查询角色 POJO（需要校验 sourceType 时使用），不存在则抛 404。 */
    private IamRole requireActiveRole(Long roleId) {
        return roleRepo.findById(roleId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "IAM_ROLE_NOT_FOUND", "角色不存在"));
    }

    /** 校验角色为 CUSTOM 类型（BUILTIN 不允许修改或删除）。 */
    private void requireCustomRole(IamRole row, String action) {
        if (SourceType.BUILTIN.getValue().equals(row.getSourceType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_ROLE_BUILTIN_FORBIDDEN", "BUILTIN 角色不允许" + action);
        }
    }
}
