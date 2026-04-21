package com.jugu.propertylease.gateway.filter;

import com.jugu.propertylease.gateway.config.GatewayProperties;
import com.jugu.propertylease.gateway.filter.ratelimit.RateLimitKeyResolver;
import com.jugu.propertylease.security.autoconfigure.SecurityResponseUtils;
import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.filter.reactive.ReactiveUserJwtFilter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 基于令牌桶（Token Bucket）的内存限流过滤器（GW-2-B）。
 *
 * <p>执行顺序：Order=-150，在 Header 清洗（-200）之后、认证（-100）之前。
 * 先限流可以避免无效请求消耗认证资源。
 *
 * <p>设计要点：
 * <ul>
 *   <li>限流 key 通过 {@link RateLimitKeyResolver} 接口抽象，当前为 IP 维度</li>
 *   <li>每个 key 对应一个独立的令牌桶，存储于 {@link ConcurrentHashMap}</li>
 *   <li>令牌桶实现为无锁（CAS），适合 WebFlux 非阻塞场景</li>
 *   <li>超限响应：HTTP 429，格式与系统统一错误格式一致（复用 SecurityResponseUtils）</li>
 *   <li>{@code gateway.rate-limit.enabled=false} 时直接跳过，无性能损耗</li>
 * </ul>
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

  /**
   * 在 SecurityHeaderCleanFilter(-200) 之后，ReactiveUserJwtFilter(-100) 之前
   */
  static final int ORDER = -150;
  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
  private static final String ERROR_CODE = "GATEWAY_RATE_LIMIT_EXCEEDED";
  private static final String ERROR_MESSAGE = "请求过于频繁，请稍后再试";

  private final boolean enabled;
  private final int capacity;
  private final int refillRate;
  private final RateLimitKeyResolver keyResolver;

  /**
   * 每个限流 key 对应一个令牌桶状态
   */
  private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

  public RateLimitFilter(GatewayProperties properties, RateLimitKeyResolver keyResolver) {
    GatewayProperties.RateLimitProperties rl = properties.getRateLimit();
    this.enabled = rl.isEnabled();
    this.capacity = rl.getCapacity();
    this.refillRate = rl.getRefillRate();
    this.keyResolver = keyResolver;
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!enabled) {
      return chain.filter(exchange);
    }

    String key = keyResolver.resolve(exchange);
    TokenBucket bucket = buckets.computeIfAbsent(key,
        k -> new TokenBucket(capacity, refillRate));

    if (bucket.tryConsume()) {
      return chain.filter(exchange);
    }

    log.warn("限流触发：key={} path={}", key, exchange.getRequest().getPath());
    return writeRateLimitResponse(exchange);
  }

  private Mono<Void> writeRateLimitResponse(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // 优先从 attributes 读取 traceId：
    // 在 ReactiveUserJwtFilter 运行过的链路中，会提前写入 exchange attributes。
    String traceId = exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID);
    // 兜底：attributes 未命中时从 header 读取。
    if (traceId == null) {
      traceId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID);
    }
    // 最后兜底：保证错误响应始终带 traceId，便于排查限流问题。
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString();
    }

    String body = SecurityResponseUtils.buildErrorJson(ERROR_CODE, ERROR_MESSAGE, traceId);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  // ----------------------------------------------------------------
  // 令牌桶实现（无锁 CAS）
  // ----------------------------------------------------------------

  /**
   * 单个 key 的令牌桶状态。
   *
   * <p>状态为 (tokens, lastRefillNanos) 的组合 long（高 32 位存 tokens，低 32 位存时间戳低位）。
   * 此处简化为 AtomicReference 持有两字段对象，适合中低并发场景。
   */
  static final class TokenBucket {

    private final int capacity;
    private final double refillRatePerNano; // 每纳秒补充令牌数

    private final AtomicReference<BucketState> state;

    TokenBucket(int capacity, int refillRatePerSecond) {
      this.capacity = capacity;
      this.refillRatePerNano = (double) refillRatePerSecond / 1_000_000_000L;
      this.state = new AtomicReference<>(new BucketState(capacity, Instant.now().toEpochMilli()));
    }

    /**
     * 尝试消耗一个令牌。
     *
     * @return true 表示获取令牌成功，可以放行；false 表示令牌不足，应限流
     */
    boolean tryConsume() {
      while (true) {
        BucketState current = state.get();
        long now = System.currentTimeMillis();
        long elapsed = now - current.lastRefillMs;

        // 计算补充令牌数（不超过容量）
        double refilled = elapsed * refillRatePerNano * 1_000_000L; // elapsed ms → ns
        double newTokens = Math.min(capacity, current.tokens + refilled);

        if (newTokens < 1.0) {
          return false; // 令牌不足
        }

        BucketState next = new BucketState(newTokens - 1.0, now);
        if (state.compareAndSet(current, next)) {
          return true;
        }
        // CAS 失败说明并发更新，重试
      }
    }

    record BucketState(double tokens, long lastRefillMs) {

    }
  }
}
