package com.jugu.propertylease.security.filter.servlet;

import com.jugu.propertylease.security.constants.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从请求 Header 中提取 {@code X-Trace-Id}，写入 MDC（key="traceId"），请求结束后清理。
 *
 * <p>执行顺序（SecurityFilterChain 内）：
 * TraceIdFilter → ServletServiceJwtFilter → 业务处理
 *
 * <p>设计要点：
 * <ul>
 *   <li>Gateway 已为认证请求生成 X-Trace-Id；放行路径直接透传外部 Header（或外部未传则为空）。
 *   <li>若 Header 不存在或为空，本地生成一个 UUID 作为 traceId，保证 MDC 始终有值。
 *   <li>finally 块中必须调用 {@link MDC#remove}（而非 clear），仅清除本 Filter 写入的 key，
 *       防止内存泄漏且不影响其他 MDC 条目。
 * </ul>
 */
public class TraceIdFilter extends OncePerRequestFilter {

  static final String MDC_TRACE_KEY = "traceId";

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {

    String traceId = request.getHeader(SecurityConstants.HEADER_TRACE_ID);
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString().replace("-", "");
    }
    MDC.put(MDC_TRACE_KEY, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_TRACE_KEY);  // 必须用 remove，防内存泄漏
    }
  }
}
