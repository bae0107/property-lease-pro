package com.jugu.propertylease.gateway.filter;

import com.jugu.propertylease.gateway.config.GatewayProperties;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Header 安全清洗过滤器（GW-2-A）。
 *
 * <p>在所有过滤器中最先执行（Order=-200），清洗外部请求中不可信任的 Header，防止外部伪造内部凭证（GW-C-05）。
 *
 * <p>清洗规则配置驱动：
 * <ul>
 *   <li>入站请求清洗：{@code gateway.security.strip-request-headers}，默认移除 X-Service-Token</li>
 *   <li>出站响应清洗：{@code gateway.security.strip-response-headers}，默认为空</li>
 * </ul>
 * 后续新增清洗规则只需修改配置，不改代码。
 */
@Component
public class SecurityHeaderCleanFilter implements GlobalFilter, Ordered {

  /**
   * 最先执行，确保在认证（Order=-100）和限流（Order=-150）之前清洗 Header
   */
  static final int ORDER = -200;

  private final List<String> stripRequestHeaders;
  private final List<String> stripResponseHeaders;

  public SecurityHeaderCleanFilter(GatewayProperties properties) {
    this.stripRequestHeaders = properties.getSecurity().getStripRequestHeaders();
    this.stripResponseHeaders = properties.getSecurity().getStripResponseHeaders();
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // 清洗入站请求 Header
    ServerHttpRequest request = exchange.getRequest();
    if (!stripRequestHeaders.isEmpty()) {
      ServerHttpRequest.Builder requestBuilder = request.mutate();
      for (String header : stripRequestHeaders) {
        requestBuilder.headers(headers -> headers.remove(header));
      }
      request = requestBuilder.build();
    }

    // 注册响应 Header 清洗（在响应回写时执行）
    ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();

    if (!stripResponseHeaders.isEmpty()) {
      ServerHttpResponse response = mutatedExchange.getResponse();
      response.beforeCommit(() -> {
        for (String header : stripResponseHeaders) {
          response.getHeaders().remove(header);
        }
        return Mono.empty();
      });
    }

    return chain.filter(mutatedExchange);
  }
}
