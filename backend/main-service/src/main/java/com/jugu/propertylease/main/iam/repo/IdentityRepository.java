package com.jugu.propertylease.main.iam.repo;

import java.time.OffsetDateTime;

/**
 * iam_identity 表的数据操作契约。
 *
 * <p>每种登录方式对应一条 Identity 记录（provider + providerUserId 唯一）。
 */
public interface IdentityRepository {

    /**
     * 插入密码登录 Identity（provider=password，providerUserId=username）。
     */
    void insertPasswordIdentity(Long userId, String username, OffsetDateTime now);

    /**
     * 软删除指定用户的所有 Identity（用户注销时调用）。
     */
    void softDeleteAllByUserId(Long userId, OffsetDateTime now);
}
