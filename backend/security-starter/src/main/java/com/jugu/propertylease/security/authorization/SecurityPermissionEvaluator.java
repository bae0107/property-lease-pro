package com.jugu.propertylease.security.authorization;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import java.io.Serializable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 自定义 PermissionEvaluator，支撑 {@code @PreAuthorize("hasPermission(null, 'order:read')")}。
 *
 * <p>权限数据来源链（运行时不查 IAM 数据库）：
 * <pre>
 * DB（IAM 登录时）→ User JWT claims["permissions"]
 *   → Gateway 解析 → Service JWT claims["permissions"]
 *   → ServiceJwtAuthenticationToken.authorities
 *   → PermissionEvaluator.hasPermission()
 * </pre>
 */
public class SecurityPermissionEvaluator implements PermissionEvaluator {

  @Override
  public boolean hasPermission(Authentication authentication,
      Object targetDomainObject,
      Object permission) {
    if (authentication == null || permission == null) {
      return false;
    }
    if (!(authentication instanceof ServiceJwtAuthenticationToken token)) {
      return false;
    }
    return token.getAuthorities().contains(
        new SimpleGrantedAuthority(permission.toString()));
  }

  @Override
  public boolean hasPermission(Authentication authentication,
      Serializable targetId,
      String targetType,
      Object permission) {
    // targetId / targetType 不参与判断，直接委托到权限字符串检查
    return hasPermission(authentication, null, permission);
  }
}
