package com.jugu.propertylease.security.filter.servlet;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import com.jugu.propertylease.security.properties.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mock 认证模式专用 Filter（{@code security.mode=mock}）： 将配置文件中的固定 mock 用户注入 SecurityContext。
 *
 * <p>仅由 {@code MockServletSecurityAutoConfiguration} 注册，其他模式不会加载。
 *
 * <p>保证以下能力在 mock 模式下正常工作：
 * <ul>
 *   <li>{@code CurrentUser.getCurrentUserId()} 返回配置的 userId</li>
 *   <li>{@code CurrentUser.getPermissions()} 返回配置的 permissions</li>
 *   <li>{@code AuditLogAspect} 能正确记录操作用户</li>
 *   <li>{@code DataPermissionInterceptor} 能读取到当前用户信息</li>
 * </ul>
 *
 * <p>mock 用户通过 {@code security.mock-user} 配置，默认 userId=1、permissions=[]。
 */
public class MockUserFilter extends OncePerRequestFilter {

  private static final String MOCK_CALLER_NAME = "mock";

  private final SecurityProperties.MockUser mockUser;

  public MockUserFilter(SecurityProperties.MockUser mockUser) {
    this.mockUser = mockUser;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    ServiceJwtAuthenticationToken authentication = ServiceJwtAuthenticationToken.ofUser(
        mockUser.getUserId(),
        MOCK_CALLER_NAME,
        mockUser.getPermissions());

    SecurityContextHolder.getContext().setAuthentication(authentication);

    try {
      filterChain.doFilter(request, response);
    } finally {
      // 确保线程复用时不污染下一次请求的 SecurityContext
      SecurityContextHolder.clearContext();
    }
  }
}
