package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.jooq.tables.pojos.IamPermission;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 角色相关数据操作契约（iam_role / iam_role_permission）。
 *
 * <p>将原 IamRoleManagementRepository 中散落的方法收敛，
 * 参数改用枚举类型（DataScopeDimension / RoleType），消除 Service 层的字符串转换。
 */
public interface RoleRepository {

    /** 检查 code 是否已被使用。 */
    boolean existsByCode(String code);

    /**
     * 插入新角色，返回数据库生成的 ID。
     *
     * @param dataScopeDimension 数据权限维度，无需求时传 null
     */
    Long insert(String name, String code, RoleType roleType,
                DataScopeDimension dataScopeDimension, String description,
                OffsetDateTime now);

    /** 查询角色（不校验是否存在，由调用方决定如何处理 Optional.empty）。 */
    Optional<IamRole> findById(Long roleId);

    /** 批量查询角色（用于批量删除前的存在性校验）。 */
    List<IamRole> findByIds(List<Long> roleIds);

    /** 更新角色基本信息（name / description）。 */
    void updateBasic(Long roleId, String name, String description, OffsetDateTime now);

    /** 查询角色关联的有效权限（deleted_at IS NULL），按 resource+action 排序。 */
    List<IamPermission> findActivePermissionsByRoleId(Long roleId);

    /** 查询有效权限 ID 集合（用于校验 permissionIds 是否全部有效）。 */
    Set<Long> findActivePermissionIdsByIds(List<Long> permissionIds);

    /**
     * 全量替换角色权限（先 DELETE，再批量 INSERT）。
     *
     * <p>调用方需保证 permissionIds 已去重。传空列表表示清空所有权限。
     * 此方法同时更新 updated_at，无需额外调用 touchUpdatedAt。
     */
    void replacePermissions(Long roleId, List<Long> permissionIds, OffsetDateTime now);

    /** 检查是否有用户持有指定角色 ID（删除前校验）。 */
    boolean isAnyRoleAssignedToUser(List<Long> roleIds);

    /** 批量删除角色的所有权限关联（删除角色前清理）。 */
    void deletePermissionsByRoleIds(List<Long> roleIds);

    /** 批量删除角色。 */
    void deleteByIds(List<Long> roleIds);
}
