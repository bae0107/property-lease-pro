package com.jugu.propertylease.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.gateway.config.GatewayProperties;
import com.jugu.propertylease.gateway.filter.ratelimit.IpRateLimitKeyResolver;
import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.filter.reactive.ReactiveUserJwtFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("RateLimitFilter 测试")
class RateLimitFilterTest {

  private GatewayFilterChain allowChain() {
    GatewayFilterChain chain = mock(GatewayFilterChain.class);
    when(chain.filter(any())).thenReturn(Mono.empty());
    return chain;
  }

  private GatewayProperties buildProps(boolean enabled, int capacity, int refillRate) {
    GatewayProperties props = new GatewayProperties();
    GatewayProperties.RateLimitProperties rl = new GatewayProperties.RateLimitProperties();
    rl.setEnabled(enabled);
    rl.setCapacity(capacity);
    rl.setRefillRate(refillRate);
    props.setRateLimit(rl);
    return props;
  }

  @Test
  @DisplayName("Order 应为 -150")
  void order_shouldBeMinus150() {
    RateLimitFilter filter = new RateLimitFilter(buildProps(true, 10, 5),
        new IpRateLimitKeyResolver());
    assertThat(filter.getOrder()).isEqualTo(-150);
  }

  @Test
  @DisplayName("限流关闭时所有请求直接放行")
  void filter_disabled_shouldPassAllRequests() {
    RateLimitFilter filter = new RateLimitFilter(buildProps(false, 1, 1),
        new IpRateLimitKeyResolver());

    for (int i = 0; i < 10; i++) {
      MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
      MockServerWebExchange exchange = MockServerWebExchange.from(request);
      StepVerifier.create(filter.filter(exchange, allowChain())).verifyComplete();
      assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
  }

  @Test
  @DisplayName("容量内请求全部放行")
  void filter_withinCapacity_shouldPassRequests() {
    int capacity = 5;
    RateLimitFilter filter = new RateLimitFilter(buildProps(true, capacity, 1),
        new IpRateLimitKeyResolver());

    for (int i = 0; i < capacity; i++) {
      MockServerHttpRequest request = MockServerHttpRequest.get("/test")
          .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
          .build();
      MockServerWebExchange exchange = MockServerWebExchange.from(request);
      StepVerifier.create(filter.filter(exchange, allowChain())).verifyComplete();
      assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
  }

  @Test
  @DisplayName("超出容量后返回 429")
  void filter_exceedCapacity_shouldReturn429() {
    int capacity = 3;
    RateLimitFilter filter = new RateLimitFilter(buildProps(true, capacity, 0),
        new IpRateLimitKeyResolver());

    for (int i = 0; i < capacity; i++) {
      MockServerHttpRequest request = MockServerHttpRequest.get("/test")
          .remoteAddress(new java.net.InetSocketAddress("10.0.0.2", 12345))
          .build();
      MockServerWebExchange exchange = MockServerWebExchange.from(request);
      StepVerifier.create(filter.filter(exchange, allowChain())).verifyComplete();
    }

    MockServerHttpRequest overRequest = MockServerHttpRequest.get("/test")
        .remoteAddress(new java.net.InetSocketAddress("10.0.0.2", 12345))
        .build();
    MockServerWebExchange overExchange = MockServerWebExchange.from(overRequest);
    StepVerifier.create(filter.filter(overExchange, allowChain())).verifyComplete();
    assertThat(overExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  @DisplayName("不同 IP 使用独立令牌桶，互不影响")
  void filter_differentIps_shouldHaveIndependentBuckets() {
    int capacity = 2;
    RateLimitFilter filter = new RateLimitFilter(buildProps(true, capacity, 0),
        new IpRateLimitKeyResolver());

    for (int i = 0; i < capacity; i++) {
      MockServerHttpRequest request = MockServerHttpRequest.get("/test")
          .remoteAddress(new java.net.InetSocketAddress("192.168.1.1", 1000))
          .build();
      StepVerifier.create(filter.filter(MockServerWebExchange.from(request), allowChain()))
          .verifyComplete();
    }

    MockServerHttpRequest ipBRequest = MockServerHttpRequest.get("/test")
        .remoteAddress(new java.net.InetSocketAddress("192.168.1.2", 1000))
        .build();
    MockServerWebExchange ipBExchange = MockServerWebExchange.from(ipBRequest);
    StepVerifier.create(filter.filter(ipBExchange, allowChain())).verifyComplete();
    assertThat(ipBExchange.getResponse().getStatusCode()).isNotEqualTo(
        HttpStatus.TOO_MANY_REQUESTS);
  }

  // ===== TraceId 携带（Bug2修复）=====

  @Test
  @DisplayName("限流触发时，429响应体包含来自 attributes 的 traceId")
  void filter_rateLimited_429ResponseContainsTraceIdFromAttributes() {
    RateLimitFilter filter = new RateLimitFilter(buildProps(true, 1, 0),
        new IpRateLimitKeyResolver());

    // 消耗唯一令牌
    MockServerHttpRequest first = MockServerHttpRequest.get("/test")
        .remoteAddress(new java.net.InetSocketAddress("10.0.0.9", 1234))
        .build();
    StepVerifier.create(filter.filter(MockServerWebExchange.from(first), allowChain()))
        .verifyComplete();

    // 第二个请求被限流，exchange attributes 中已有 ReactiveUserJwtFilter 写入的 traceId
    MockServerHttpRequest over = MockServerHttpRequest.get("/test")
        .remoteAddress(new java.net.InetSocketAddress("10.0.0.9", 1234))
        .build();
    MockServerWebExchange overExchange = MockServerWebExchange.from(over);
    overExchange.getAttributes().put(ReactiveUserJwtFilter.ATTR_TRACE_ID, "rate-trace-001");

    StepVerifier.create(filter.filter(overExchange, allowChain())).verifyComplete();

    assertThat(overExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    String body = overExchange.getResponse().getBodyAsString().block();
    assertThat(body).contains("rate-trace-001");
  }

  @Test
  @DisplayName("限流触发时，无 attributes 则从 header 兜底读取 traceId")
  void filter_rateLimited_429ResponseContainsTraceIdFromHeaderFallback() {
    RateLimitFilter filter = new RateLimitFilter(buildProps(true, 1, 0),
        new IpRateLimitKeyResolver());

    MockServerHttpRequest first = MockServerHttpRequest.get("/test")
        .remoteAddress(new java.net.InetSocketAddress("10.0.0.8", 1234))
        .build();
    StepVerifier.create(filter.filter(MockServerWebExchange.from(first), allowChain()))
        .verifyComplete();

    // 第二个请求携带 X-Trace-Id header，但 attributes 为空（极端场景）
    MockServerHttpRequest over = MockServerHttpRequest.get("/test")
        .remoteAddress(new java.net.InetSocketAddress("10.0.0.8", 1234))
        .header(SecurityConstants.HEADER_TRACE_ID, "header-trace-fallback")
        .build();
    MockServerWebExchange overExchange = MockServerWebExchange.from(over);

    StepVerifier.create(filter.filter(overExchange, allowChain())).verifyComplete();

    assertThat(overExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    String body = overExchange.getResponse().getBodyAsString().block();
    assertThat(body).contains("header-trace-fallback");
  }
}
