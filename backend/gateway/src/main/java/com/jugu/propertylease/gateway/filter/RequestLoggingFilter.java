package com.jugu.propertylease.gateway.filter;

import com.jugu.propertylease.security.constants.SecurityConstants;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求日志过滤器（GW-2-C）。
 *
 * <p>执行顺序：Order=-50，在认证过滤器（-100）之后执行，因此可以读到已注入的 X-Trace-Id（C-38）。
 *
 * <p>日志格式（结构化单行）：
 * <pre>
 *   [GATEWAY] traceId=abc123 method=POST path=/api/main-service/auth/login ip=1.2.3.4 status=200 duration=45ms
 * </pre>
 *
 * <p>日志记录失败不影响请求处理，整体 try-catch 保护。
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

  /**
   * 在 ReactiveUserJwtFilter(-100) 之后执行，确保能读到已注入的 X-Trace-Id
   */
  static final int ORDER = -50;
  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    long startMs = System.currentTimeMillis();

    // 请求进入时记录基础信息
    String traceId = request.getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID);
    String method = request.getMethod().name();
    String path = request.getPath().value();
    String ip = resolveIp(request);

    return chain.filter(exchange)
        .doFinally(signalType -> {
          // 响应完成后补充状态码和耗时
          try {
            long duration = System.currentTimeMillis() - startMs;
            int status = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 0;
            log.info("[GATEWAY] traceId={} method={} path={} ip={} status={} duration={}ms",
                traceId != null ? traceId : "-",
                method, path, ip, status, duration);
          } catch (Exception e) {
            // 日志记录失败不影响请求处理
            log.warn("[GATEWAY] 请求日志记录失败：{}", e.getMessage());
          }
        });
  }

  /**
   * 优先读 X-Forwarded-For 首个 IP，兜底使用 RemoteAddress
   */
  private String resolveIp(ServerHttpRequest request) {
    String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].strip();
    }
    InetSocketAddress remoteAddress = request.getRemoteAddress();
    if (remoteAddress != null && remoteAddress.getAddress() != null) {
      return remoteAddress.getAddress().getHostAddress();
    }
    return "unknown";
  }
}
