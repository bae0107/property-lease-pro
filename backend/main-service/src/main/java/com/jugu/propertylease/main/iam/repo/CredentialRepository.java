package com.jugu.propertylease.main.iam.repo;

import java.time.OffsetDateTime;

/**
 * iam_credential 表的数据操作契约。
 *
 * <p>密码凭证与用户是 1:1 关系（userId 即主键），
 * 统一通过 upsert 操作，调用方无需感知"是否已存在"的状态。
 */
public interface CredentialRepository {

    /**
     * 写入或更新密码哈希。
     *
     * <p>语义：有则 UPDATE，无则 INSERT。
     * 实现使用 INSERT ... ON DUPLICATE KEY UPDATE，保证原子性。
     *
     * @param userId       用户 ID（主键）
     * @param passwordHash BCrypt 哈希值（强度 10）
     * @param now          操作时间
     */
    void upsert(Long userId, String passwordHash, OffsetDateTime now);
}
