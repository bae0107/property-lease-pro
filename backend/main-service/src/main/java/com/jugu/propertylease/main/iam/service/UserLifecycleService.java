package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import com.jugu.propertylease.main.iam.repo.IamUserLifecycleRepository;
import com.jugu.propertylease.main.iam.repo.IdentityRepository;
import com.jugu.propertylease.main.iam.repo.model.UserDeleteSnapshot;
import com.jugu.propertylease.main.iam.repo.model.UserSoftDeleteCommand;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 用户生命周期管理服务（软删除）。
 *
 * <p>软删除流程：
 * <ol>
 *   <li>前置校验（不允许删自己、不允许删 BUILTIN/SYSTEM 用户、不允许删最后一个管理员）</li>
 *   <li>墓碑化用户主体（iam_user 字段覆写 + deleted_at）</li>
 *   <li>软删除登录 Identity（委托 IdentityRepository，职责分离）</li>
 *   <li>递增 authVersion，使所有存量 token 立即失效</li>
 * </ol>
 */
@Service
public class UserLifecycleService {

    private static final String SYSTEM_ADMIN_ROLE_CODE = "ROLE_IAM_ADMIN";

    private final IamUserLifecycleRepository lifecycleRepo;
    private final IdentityRepository identityRepo;
    private final AuthVersionService authVersionService;

    public UserLifecycleService(
            IamUserLifecycleRepository lifecycleRepo,
            IdentityRepository identityRepo,
            AuthVersionService authVersionService) {
        this.lifecycleRepo = lifecycleRepo;
        this.identityRepo = identityRepo;
        this.authVersionService = authVersionService;
    }

    /**
     * 软删除用户（不可恢复）。
     *
     * @param userId         被删除用户 ID
     * @param operatorUserId 操作者用户 ID（null 表示系统操作）
     * @param reason         删除原因（审计字段）
     */
    @Transactional
    public void softDeleteUser(Long userId, Long operatorUserId, String reason) {
        // 不允许删除当前操作者自身
        if (operatorUserId != null && operatorUserId.equals(userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_DELETE_SELF_FORBIDDEN", "不允许删除当前登录用户");
        }

        // 查询快照（存在性 + 类型校验）
        UserDeleteSnapshot snapshot = lifecycleRepo.findActiveUserSnapshot(userId);
        if (snapshot == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "IAM_USER_NOT_FOUND", "用户不存在");
        }

        // 利用 snapshot 的语义方法，不再写字符串比对
        if (snapshot.isDeletionForbidden()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_DELETE_FORBIDDEN", "内置或系统用户不允许删除");
        }

        // 不允许删除最后一个系统管理员
        if (isLastSystemAdmin(userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_DELETE_LAST_ADMIN_FORBIDDEN", "不允许删除最后一个管理员");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String tombstonePrefix = "__deleted__" + userId + "__";

        lifecycleRepo.softDeleteUser(new UserSoftDeleteCommand(
                userId,
                operatorUserId,
                reason,
                truncate(tombstonePrefix + snapshot.userName(), 100),
                "D" + userId,
                snapshot.email() == null ? null : truncate(tombstonePrefix + snapshot.email(), 200),
                snapshot.userName(),
                snapshot.mobile(),
                snapshot.email(),
                now
        ));

        // Identity 软删除委托给 IdentityRepository，不在 LifecycleRepository 中越界操作
        identityRepo.softDeleteAllByUserId(userId, now);

        authVersionService.bumpAuthVersion(userId, "SOFT_DELETE");
    }

    // ─────────────────────────────────────────────
    // 私有辅助方法
    // ─────────────────────────────────────────────

    private boolean isLastSystemAdmin(Long userId) {
        if (!lifecycleRepo.isUserAssignedRoleCode(userId, SYSTEM_ADMIN_ROLE_CODE)) {
            return false;
        }
        return lifecycleRepo.countActiveUsersByRoleCode(SYSTEM_ADMIN_ROLE_CODE) <= 1;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
