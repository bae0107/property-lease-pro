package com.jugu.propertylease.security.filter.servlet;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import com.jugu.propertylease.security.autoconfigure.SecurityResponseUtils;
import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.exception.InvalidTokenException;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.JwtTokenParser;
import com.jugu.propertylease.security.token.ServiceTokenPayload;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet 侧唯一 JWT 过滤器：验证所有入站请求的 Service JWT， 构建 {@link ServiceJwtAuthenticationToken} 写入
 * SecurityContext。
 *
 * <p><b>放行路径对齐原则（与 ReactiveUserJwtFilter 完全镜像）：</b><br>
 * 放行判断在 Filter 层完成（{@link #shouldNotFilter}），读取
 * {@code SecurityProperties.getEffectivePermitPaths()}，与 Gateway 侧
 * {@code ReactiveUserJwtFilter.isPermittedPath()} 消费同一来源、同一时机。 {@code SecurityFilterChain} 中的
 * {@code requestMatchers().permitAll()} 作为 Authorization 层兜底保留，但不再是放行的第一道门。
 *
 * <p>Token 缺失或无效时直接写 401 JSON 响应并终止过滤器链，不抛异常。
 * 原因：本 Filter 位于 ExceptionTranslationFilter 之前，抛出异常无法被其捕获， 会穿透整个 SecurityFilterChain 导致 EntryPoint
 * 收到通用 AuthenticationException， 丢失具体 errorCode。直接写响应可精准返回 errorCode 并携带 traceId（来自 MDC）。
 */
public class ServletServiceJwtFilter extends OncePerRequestFilter {

  private final JwtTokenParser jwtTokenParser;
  private final SecurityProperties securityProperties;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public ServletServiceJwtFilter(JwtTokenParser jwtTokenParser,
      SecurityProperties securityProperties) {
    this.jwtTokenParser = jwtTokenParser;
    this.securityProperties = securityProperties;
  }

  /**
   * 对齐 {@code ReactiveUserJwtFilter.isPermittedPath()}： permit paths 在 Filter 层提前跳过，不进行 Service JWT
   * 校验。 读取 {@code SecurityProperties.getEffectivePermitPaths()} （内置路径 + yml 配置路径合并，与 Gateway
   * 侧完全相同的数据来源）。
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return securityProperties.getEffectivePermitPaths().stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {

    String token = request.getHeader(SecurityConstants.HEADER_SERVICE_TOKEN);

    if (token == null || token.isBlank()) {
      write401(response, InvalidTokenException.TOKEN_MISSING, "Service token is required");
      return;
    }

    try {
      ServiceTokenPayload payload = jwtTokenParser.parseServiceToken(
          token,
          securityProperties.getJwt().getService().getSecret());

      ServiceJwtAuthenticationToken authToken = payload.userId() != null
          ? ServiceJwtAuthenticationToken.ofUser(
          payload.userId(), payload.serviceName(), payload.permissions())
          : ServiceJwtAuthenticationToken.ofSystem(payload.serviceName());

      SecurityContextHolder.getContext().setAuthentication(authToken);
      filterChain.doFilter(request, response);

    } catch (InvalidTokenException e) {
      write401(response, e.getErrorCode(), e.getMessage());
    }
  }

  private void write401(HttpServletResponse response, String code, String message)
      throws IOException {
    String traceId = MDC.get("traceId");
    String body = SecurityResponseUtils.buildErrorJson(code, message, traceId);
    // 这里使用了403:SC_FORBIDDEN，为了规避问题
    // “Spring Cloud OpenFeign 的 issue 里有人明确反馈：401 时 response.body() 为空，即使用了 ApacheHttpClient 和 OkHttp 也一样。”
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(body);
  }
}
