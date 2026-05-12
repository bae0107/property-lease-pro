package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.iam.repo.model.RoleTypeSnapshot;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 用户-角色关联的数据操作契约（iam_user_role + iam_role 只读查询）。
 *
 * <p>将"角色分配"和"角色元数据查询"收敛在一处，
 * 避免 Service 层在多个 Repository 之间来回跳转。
 */
public interface UserRoleRepository {

    /**
     * 查询指定角色 ID 列表的类型快照（id + roleType + name）。
     *
     * <p>用于校验：
     * <ul>
     *   <li>角色 ID 是否全部有效（返回数量 == 入参数量）</li>
     *   <li>角色 roleType 是否与用户类型匹配</li>
     * </ul>
     */
    List<RoleTypeSnapshot> findSnapshotsByIds(List<Long> roleIds);

    /**
     * 查询指定角色 ID 列表中，要求数据权限维度的集合。
     *
     * <p>用于校验：用户被分配的角色所要求的数据权限维度，
     * 必须与请求中提供的 DataScopeItem 维度完全一致。
     */
    Set<String> findRequiredScopeDimensionsByIds(List<Long> roleIds);

    /**
     * 查询用户当前持有的角色 ID 列表。
     */
    List<Long> findRoleIdsByUserId(Long userId);

    /**
     * 全量替换用户角色（先 DELETE，再批量 INSERT）。
     *
     * <p>调用方需保证 roleIds 非空且已去重（由 Service 层 normalizeRoleIds 处理）。
     */
    void replace(Long userId, List<Long> roleIds, OffsetDateTime now);
}
