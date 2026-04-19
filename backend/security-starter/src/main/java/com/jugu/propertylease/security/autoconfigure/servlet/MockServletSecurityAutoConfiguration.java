package com.jugu.propertylease.security.autoconfigure.servlet;

import com.jugu.propertylease.security.authorization.SecurityPermissionEvaluator;
import com.jugu.propertylease.security.autoconfigure.SecurityStarterAutoConfiguration;
import com.jugu.propertylease.security.datapermission.DataPermissionInterceptor;
import com.jugu.propertylease.security.filter.servlet.MockUserFilter;
import com.jugu.propertylease.security.filter.servlet.TraceIdFilter;
import com.jugu.propertylease.security.properties.SecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mock 认证模式的 Servlet 安全自动装配（{@code security.mode=mock}）。
 *
 * <p>与 {@link ServletSecurityAutoConfiguration}（service 模式）的唯一差异：
 * <ul>
 *   <li>不注册 {@code ServletServiceJwtFilter}，无需携带 Service JWT 即可接收请求
 *       （即"入站 401 路径"不生效，由 security-starter 自身单测覆盖）</li>
 *   <li>注册 {@link MockUserFilter}，将配置文件中的固定 mock 用户注入 SecurityContext，
 *       替代真实 JWT 解析</li>
 *   <li>SecurityFilterChain 对所有路径 {@code permitAll}，但 SecurityContext 中有真实用户</li>
 * </ul>
 *
 * <p>其余安全能力与 service 模式完全一致，均正常工作：
 * <ul>
 *   <li>{@code @PreAuthorize} / {@code @PostAuthorize} 方法鉴权
 *       （配置不同 permissions 可测试 200 / 403 场景）</li>
 *   <li>{@code CurrentUser} 系列 API（getCurrentUserId、getPermissions 等）</li>
 *   <li>{@code AuditLogAspect}（能正确记录操作用户的 userId）</li>
 *   <li>{@code DataPermissionInterceptor}（能读取到当前用户信息）</li>
 *   <li>{@code ServiceTokenClientInterceptor}（SecurityContext 有用户，出站上下文正确；
 *       jwt.service 未配置时跳过 Token 签发）</li>
 * </ul>
 *
 * <p>application-local.yml 配置示例：
 * <pre>{@code
 * security:
 *   mode: mock
 *   service-name: billing-service
 *   mock-user:
 *     user-id: 1
 *     permissions:        # 留空则 @PreAuthorize 会返回 403（用于测试无权限场景）
 *       - "lease:read"
 *       - "lease:write"
 * }</pre>
 *
 * <p>注意：{@code security.mode} 是安全运行模式，与部署环境 Profile
 * （local / dev / prod）是两个独立维度，不要混淆。
 */
@AutoConfiguration(after = SecurityStarterAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "security.mode", havingValue = "mock")
@Import(MethodSecurityConfiguration.class)
public class MockServletSecurityAutoConfiguration {

  @Bean
  public TraceIdFilter traceIdFilter() {
    return new TraceIdFilter();
  }

  @Bean
  public MockUserFilter mockUserFilter(SecurityProperties properties) {
    return new MockUserFilter(properties.getMockUser());
  }

  @Bean
  public SecurityPermissionEvaluator securityPermissionEvaluator() {
    return new SecurityPermissionEvaluator();
  }

  @Bean
  public DataPermissionInterceptor dataPermissionInterceptor() {
    return new DataPermissionInterceptor();
  }

  @Bean
  public WebMvcConfigurer mockDataPermissionMvcConfigurer(DataPermissionInterceptor interceptor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
      }
    };
  }

  /**
   * Mock 模式 SecurityFilterChain：所有请求直接放行（认证由 MockUserFilter 注入）。
   *
   * <p>过滤器执行顺序：
   * <ol>
   *   <li>SecurityContextHolderFilter（≈300）- 初始化 SecurityContext</li>
   *   <li>TraceIdFilter - TraceId 注入 MDC，全链路追踪</li>
   *   <li>MockUserFilter - 注入 mock 用户，使 @PreAuthorize / CurrentUser / AuditLog 正常工作</li>
   * </ol>
   */
  @Bean
  public SecurityFilterChain mockSecurityFilterChain(HttpSecurity http,
      TraceIdFilter traceIdFilter,
      MockUserFilter mockUserFilter)
      throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .anyRequest().permitAll())
        .addFilterAfter(traceIdFilter, SecurityContextHolderFilter.class)
        .addFilterBefore(mockUserFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
