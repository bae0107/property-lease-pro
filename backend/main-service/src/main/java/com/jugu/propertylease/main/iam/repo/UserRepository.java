package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.repo.model.UserBaseInfo;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * iam_user 表的数据操作契约。
 *
 * <p>只负责 iam_user 单表的 CRUD，不包含任何关联表操作。
 */
public interface UserRepository {

    /**
     * 插入新用户，返回数据库生成的 ID。
     */
    Long insert(UserType userType, String userName, String realName,
                String mobile, String email, OffsetDateTime now);

    /**
     * 查询未删除的用户完整 POJO。
     */
    Optional<IamUser> findActiveById(Long userId);

    /**
     * 查询未删除用户的轻量快照（userType + sourceType），用于业务前置校验。
     * 避免为校验而加载整个 IamUser POJO。
     */
    Optional<UserBaseInfo> findActiveBaseById(Long userId);

    /**
     * 更新用户基本资料（realName / mobile / email）。
     */
    void updateProfile(Long userId, String realName, String mobile,
                       String email, OffsetDateTime now);

    /**
     * 更新单个用户状态。
     */
    void updateStatus(Long userId, UserStatus status, OffsetDateTime now);

    /**
     * 批量更新用户状态，一条 SQL 完成，避免 N+1。
     *
     * <p>调用方需保证 userIds 非空（框架层 @NotEmpty 已拦截空列表）。
     */
    void batchUpdateStatus(List<Long> userIds, UserStatus status, OffsetDateTime now);
}
