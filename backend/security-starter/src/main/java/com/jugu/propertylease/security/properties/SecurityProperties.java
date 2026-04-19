package com.jugu.propertylease.security.properties;

import com.jugu.propertylease.security.constants.SecurityConstants;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security Starter 顶层配置类。
 *
 * <pre>
 * # 微服务（service 模式）
 * security:
 *   mode: service
 *   service-name: billing-service
 *   jwt:
 *     service:
 *       secret: ${JWT_SERVICE_SECRET}
 *       expiration: 300
 *
 * # Gateway（gateway 模式）
 * security:
 *   mode: gateway
 *   service-name: gateway
 *   jwt:
 *     user:
 *       secret: ${JWT_USER_SECRET}
 *       expiration: 1800
 *     service:
 *       secret: ${JWT_SERVICE_SECRET}
 *       expiration: 300
 *
 * # 本地开发（mock 模式）— 不需要任何 JWT 配置
 * security:
 *   mode: mock
 *   service-name: billing-service
 *   mock-user:
 *     user-id: 1
 *     permissions: []   # 默认空，方法安全未激活，填写后 CurrentUser.getPermissions() 可读到
 * </pre>
 *
 * <p>JWT 配置的存在性校验由条件 Bean 负责（{@code ServiceJwtValidator} /
 * {@code UserJwtValidator}），而非 JSR-303 注解，以便 mock 模式跳过 JWT 配置。
 */
@ConfigurationProperties(prefix = "security")
@Validated
public class SecurityProperties {

  /**
   * 运行模式：
   * <ul>
   *   <li>{@code service}：Servlet 微服务，验证 Service JWT</li>
   *   <li>{@code gateway}：WebFlux Gateway，验证 User JWT，签发 Service JWT</li>
   *   <li>{@code mock}：模拟认证模式，跳过所有 Token 验证，注入固定 mock 用户，
   *       供本地开发使用。与部署环境 Profile（local/dev/prod）无关。</li>
   * </ul>
   */
  @NotBlank(message = "security.mode must not be blank (service | gateway | mock)")
  private String mode;

  /**
   * 当前服务名，写入 Service JWT 的 sub 字段，如 "gateway"、"billing-service"。
   */
  @NotBlank(message = "security.service-name must not be blank")
  private String serviceName;

  /**
   * 服务特有放行路径（Ant 风格），不含 DEFAULT_PERMIT_PATHS 已覆盖的路径。 通过 {@link #getEffectivePermitPaths()}
   * 与内置路径合并后使用。
   */
  private List<String> permitPaths = new ArrayList<>();

  /**
   * JWT 配置容器，包含 user / service 两个嵌套对象。
   *
   * <p>mock 模式下此字段可完全省略；service / gateway 模式下对应 secret 的
   * 存在性由启动时创建的条件 Bean（{@code ServiceJwtValidator} / {@code UserJwtValidator}）负责校验，缺失则启动失败。
   */
  private JwtConfig jwt = new JwtConfig();

  /**
   * Mock 认证模式的固定用户配置，仅在 {@code security.mode=mock} 时生效。
   *
   * <p>默认值：userId=1，permissions=[]（空列表）。
   * mock 模式下方法安全未激活（{@code @PreAuthorize} 静默不生效）， permissions 仅影响
   * {@code CurrentUser.getPermissions()} 的返回值。
   */
  private MockUser mockUser = new MockUser();

  // ===== 辅助方法 =====

  /**
   * 获取合并后的有效放行路径列表：内置路径 + 配置的服务特有路径。
   */
  public List<String> getEffectivePermitPaths() {
    List<String> merged = new ArrayList<>(SecurityConstants.DEFAULT_PERMIT_PATHS);
    if (permitPaths != null) {
      merged.addAll(permitPaths);
    }
    return Collections.unmodifiableList(merged);
  }

  // ===== Getters / Setters =====

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public List<String> getPermitPaths() {
    return permitPaths;
  }

  public void setPermitPaths(List<String> permitPaths) {
    this.permitPaths = permitPaths != null ? permitPaths : new ArrayList<>();
  }

  public JwtConfig getJwt() {
    return jwt;
  }

  public void setJwt(JwtConfig jwt) {
    this.jwt = jwt;
  }

  public MockUser getMockUser() {
    return mockUser;
  }

  public void setMockUser(MockUser mockUser) {
    this.mockUser = mockUser != null ? mockUser : new MockUser();
  }

  // ===== 嵌套配置类 =====

  /**
   * 包含 user / service 两个 JwtProperties 的容器类。 字段均不带 JSR-303 注解，存在性校验由条件 Bean 负责。
   */
  public static class JwtConfig {

    private JwtProperties user;
    private JwtProperties service;

    public JwtProperties getUser() {
      return user;
    }

    public void setUser(JwtProperties user) {
      this.user = user;
    }

    public JwtProperties getService() {
      return service;
    }

    public void setService(JwtProperties service) {
      this.service = service;
    }
  }

  /**
   * Mock 认证模式的用户配置。
   *
   * <p>userId 默认 1，permissions 默认空列表。
   * 开发者可在 application-local.yml 中覆盖，以测试不同用户的业务逻辑。
   */
  public static class MockUser {

    /**
     * mock 用户 ID，默认为 1L
     */
    private Long userId = 1L;

    /**
     * mock 用户权限列表，默认为空列表
     */
    private List<String> permissions = new ArrayList<>();

    public Long getUserId() {
      return userId;
    }

    public void setUserId(Long userId) {
      this.userId = userId;
    }

    public List<String> getPermissions() {
      return permissions;
    }

    public void setPermissions(List<String> permissions) {
      this.permissions = permissions != null ? permissions : new ArrayList<>();
    }
  }
}
