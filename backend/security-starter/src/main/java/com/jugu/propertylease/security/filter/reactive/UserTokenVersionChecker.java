package com.jugu.propertylease.security.filter.reactive;

/**
 * User token authVersion 一致性检查器。
 */
@FunctionalInterface
public interface UserTokenVersionChecker {

  /**
   * @return true 表示 token 中版本与当前版本一致。
   */
  boolean isCurrent(Long userId, Integer authVersion);
}
