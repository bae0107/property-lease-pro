package com.jugu.propertylease.security.authentication;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Service JWT 认证成功后的 Authentication 载体。
 *
 * <p>继承 {@link AbstractAuthenticationToken}（Spring Security 推荐扩展方式），
 * 语义准确（JWT 认证，非用户名密码认证）。 不实现 {@code UserDetails}：无状态 JWT 场景无需密码/账号状态字段。
 *
 * <p>通过工厂方法创建，构造后不可变。
 */
public class ServiceJwtAuthenticationToken extends AbstractAuthenticationToken {

  /**
   * 来自 Service JWT，nullable（null = 系统调用，无用户上下文）
   */
  private final Long userId;

  /**
   * 来自 Service JWT sub（serviceName 或调用方标识）
   */
  private final String callerName;

  // ===== Private constructor — use factory methods =====

  private ServiceJwtAuthenticationToken(Long userId,
      String callerName,
      Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.userId = userId;
    this.callerName = callerName;
    super.setAuthenticated(true); // 已通过 JWT 验证
  }

  // ===== Factory methods =====

  /**
   * 用户请求场景（userId != null）：从 Service JWT 的 userId + permissions 构建。
   *
   * @param userId      用户 ID，非 null
   * @param callerName  调用方标识（serviceName 或占位值）
   * @param permissions 权限字符串列表，如 ["order:read", "order:write"]
   */
  public static ServiceJwtAuthenticationToken ofUser(Long userId,
      String callerName,
      List<String> permissions) {
    Set<GrantedAuthority> authorities = permissions == null
        ? new LinkedHashSet<>()
        : permissions.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return new ServiceJwtAuthenticationToken(userId, callerName, authorities);
  }

  /**
   * 系统调用场景（userId = null）：无用户上下文的纯服务间调用。
   *
   * @param serviceName 调用方服务名
   */
  public static ServiceJwtAuthenticationToken ofSystem(String serviceName) {
    return new ServiceJwtAuthenticationToken(null, serviceName, Collections.emptySet());
  }

  // ===== AbstractAuthenticationToken 抽象方法实现 =====

  @Override
  public Object getCredentials() {
    return null; // JWT 认证后无需保留原始凭证
  }

  @Override
  public Object getPrincipal() {
    return callerName; // 调用方标识
  }

  // ===== Business methods =====

  /**
   * @return 当前用户 ID，系统调用时为 null
   */
  public Long getUserId() {
    return userId;
  }

  /**
   * @return 调用方标识（non-null）
   */
  public String getCallerName() {
    return callerName;
  }

  /**
   * 禁止从外部修改认证状态。
   */
  @Override
  public void setAuthenticated(boolean authenticated) {
    if (authenticated) {
      throw new IllegalArgumentException(
          "Use factory methods to create authenticated ServiceJwtAuthenticationToken");
    }
    super.setAuthenticated(false);
  }
}
