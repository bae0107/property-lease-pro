package com.jugu.propertylease.security.context;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前请求用户信息的静态访问工具。
 *
 * <p>所有方法对"无认证"场景安全返回（null / 空集合 / false），不抛 NullPointerException。
 * 此工具类禁止修改 SecurityContext，只做读取。
 */
public final class CurrentUser {

  private CurrentUser() {
  }

  /**
   * 当前用户 ID。系统调用时为 null（Service JWT 认证通过但无 userId）。
   */
  public static Long getCurrentUserId() {
    return getToken().map(ServiceJwtAuthenticationToken::getUserId).orElse(null);
  }

  /**
   * 调用方标识（serviceName 或占位值）。未认证时返回 null。
   */
  public static String getCallerName() {
    return getToken().map(ServiceJwtAuthenticationToken::getCallerName).orElse(null);
  }

  /**
   * 当前 Authentication 携带的权限字符串集合。未认证时返回空集合。
   */
  public static Set<String> getPermissions() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return Collections.emptySet();
    }
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * 当前请求是否已通过认证。
   */
  public static boolean isAuthenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated();
  }

  /**
   * 是否为系统调用（Service JWT 校验通过，但 Token 中无 userId）。 含义：纯服务间任务调用，无用户上下文。 注意：isSystemCall() 与
   * isAuthenticated() 不互斥。
   */
  public static boolean isSystemCall() {
    return isAuthenticated() && getCurrentUserId() == null;
  }

  /**
   * 获取完整的 {@link ServiceJwtAuthenticationToken}，供需要完整信息的场景使用。
   */
  public static Optional<ServiceJwtAuthenticationToken> getAuthentication() {
    return getToken();
  }

  // ===== Private =====

  private static Optional<ServiceJwtAuthenticationToken> getToken() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof ServiceJwtAuthenticationToken token) {
      return Optional.of(token);
    }
    return Optional.empty();
  }
}
