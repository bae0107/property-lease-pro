package com.jugu.propertylease.gateway.filter.ratelimit;

import java.net.InetSocketAddress;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * 基于来源 IP 的限流 key 提取实现（GW-2-B）。
 *
 * <p>IP 提取策略：
 * <ol>
 *   <li>优先读取 {@code X-Forwarded-For} 首个 IP（代理 / 负载均衡场景）</li>
 *   <li>兜底使用 TCP 连接 RemoteAddress</li>
 *   <li>均无法获取时返回 "unknown"（不阻断请求，归入同一桶）</li>
 * </ol>
 */
@Component
public class IpRateLimitKeyResolver implements RateLimitKeyResolver {

  private static final String X_FORWARDED_FOR = "X-Forwarded-For";

  @Override
  public String resolve(ServerWebExchange exchange) {
    // 优先读 X-Forwarded-For 首个 IP
    String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      // X-Forwarded-For 可能包含多个 IP（逗号分隔），取第一个
      String firstIp = forwardedFor.split(",")[0].strip();
      if (!firstIp.isEmpty()) {
        return firstIp;
      }
    }

    // 兜底使用 RemoteAddress
    InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
    if (remoteAddress != null && remoteAddress.getAddress() != null) {
      return remoteAddress.getAddress().getHostAddress();
    }

    return "unknown";
  }
}
