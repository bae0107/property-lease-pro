package com.jugu.propertylease.security.autoconfigure.servlet;

import com.jugu.propertylease.security.autoconfigure.SecurityResponseUtils;
import com.jugu.propertylease.security.exception.InvalidTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 处理认证失败（401）：从 InvalidTokenException 提取具体 errorCode， 输出统一 JSON 响应。
 */
class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    String traceId = MDC.get("traceId");
    String code;
    String message;
    if (authException instanceof InvalidTokenException ite) {
      code = ite.getErrorCode();
      message = ite.getMessage();
    } else {
      code = "UNAUTHORIZED";
      message = "Authentication required";
    }
    writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
        SecurityResponseUtils.buildErrorJson(code, message, traceId));
  }

  private void writeJson(HttpServletResponse response, int status, String body)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(body);
  }
}

/**
 * 处理权限不足（403）。
 */
class SecurityAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException {
    String traceId = MDC.get("traceId");
    String body = SecurityResponseUtils.buildErrorJson("FORBIDDEN", "Access denied", traceId);
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(body);
  }
}
