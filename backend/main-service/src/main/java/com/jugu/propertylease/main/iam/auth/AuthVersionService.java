package com.jugu.propertylease.main.iam.auth;

/**
 * 用户认证版本服务。
 *
 * <p>用于在关键安全变更后递增 authVersion，使历史 token 失效。
 */
public interface AuthVersionService {

  /**
   * 对指定用户执行 authVersion 递增。
   *
   * @param userId 用户 ID
   * @param reason 触发原因（审计字段）
   */
  void bumpAuthVersion(Long userId, String reason);
}

