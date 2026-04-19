package com.jugu.propertylease.security.constants;

import java.util.List;

/**
 * 全局安全常量。 DEFAULT_PERMIT_PATHS 由所有 Filter 自动包含，无需在 yml 中重复配置。 修改此类中的常量会影响所有服务，需谨慎。
 */
public final class SecurityConstants {

  /**
   * 系统级内置放行路径，所有 Filter 自动包含，无需在 yml 中重复配置，Ant 风格匹配。
   *
   * <ul>
   *   <li>{@code /actuator/**}：运维端点，内网访问</li>
   *   <li>{@code /error}：Spring Boot error dispatch 路径，必须放行。
   *       业务异常（如 405）触发 error dispatch 后若不放行，Security 会拦截并返回
   *       UNAUTHORIZED，掩盖真实错误码。与 Spring Boot 官方 SecurityAutoConfiguration 行为对齐。</li>
   * </ul>
   */
  public static final List<String> DEFAULT_PERMIT_PATHS = List.of(
      "/actuator/**",
      "/error"
  );

  // ===== 内置放行路径 =====
  /**
   * Service JWT Token Header — 统一服务间认证凭证
   */
  public static final String HEADER_SERVICE_TOKEN = "X-Service-Token";

  // ===== HTTP Header 名称 =====
  /**
   * 全链路追踪 ID Header
   */
  public static final String HEADER_TRACE_ID = "X-Trace-Id";
  /**
   * Service Token 中 userId 的 Claims key
   */
  public static final String CLAIM_USER_ID = "userId";

  // ===== JWT Claims Key =====
  /**
   * Service Token 中 permissions 的 Claims key
   */
  public static final String CLAIM_PERMISSIONS = "permissions";

  private SecurityConstants() {
  }
}
