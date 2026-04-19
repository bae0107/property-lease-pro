package com.jugu.propertylease.gateway.filter.ratelimit;

import org.springframework.web.server.ServerWebExchange;

/**
 * 限流 key 提取接口（GW-2-B）。
 *
 * <p>当前唯一实现：{@link IpRateLimitKeyResolver}（per IP）。
 * 后续扩展 per-userId、per-API-key 等维度时，新增实现类并通过 {@code gateway.rate-limit.key-resolver} 配置切换，不改动
 * {@code RateLimitFilter} 主体逻辑。
 */
public interface RateLimitKeyResolver {

  /**
   * 从当前请求中提取限流维度 key。
   *
   * @param exchange 当前 WebFlux 请求交换对象
   * @return 限流 key，不可为 null
   */
  String resolve(ServerWebExchange exchange);
}
