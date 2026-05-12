package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.iam.repo.model.UserDeleteSnapshot;
import com.jugu.propertylease.main.iam.repo.model.UserSoftDeleteCommand;

/**
 * 用户生命周期数据操作契约（软删除流程专用）。
 *
 * <p>软删除操作涉及多字段原子更新（状态、墓碑化标识、删除时间、操作人等），
 * 与普通的 UserRepository.updateStatus 语义不同，因此单独维护此契约。
 *
 * <p>注：markIdentityDeleted 已移至 IdentityRepository.softDeleteAllByUserId，
 * 避免生命周期 Repository 越界操作 iam_identity 表。
 */
public interface IamUserLifecycleRepository {

    /**
     * 查询用于软删除校验的用户快照（userType / sourceType / 登录标识）。
     *
     * @return 用户存在且未删除时返回快照；否则返回 null
     */
    UserDeleteSnapshot findActiveUserSnapshot(Long userId);

    /** 执行用户软删除（置 INACTIVE + 墓碑化 + 标记 deleted_at）。 */
    void softDeleteUser(UserSoftDeleteCommand command);

    /** 检查用户是否持有指定 role code。 */
    boolean isUserAssignedRoleCode(Long userId, String roleCode);

    /** 统计持有指定 role code 的有效用户数量。 */
    int countActiveUsersByRoleCode(String roleCode);
}
